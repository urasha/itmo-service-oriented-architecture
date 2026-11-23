"""Prepare and run the lab processes together on Linux or FreeBSD."""
import fcntl
import http.client
import json
import os
from pathlib import Path
import shutil
import signal
import ssl
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.request import urlopen
from urllib.parse import urlsplit
from xml.dom import minidom

ROOT = Path(__file__).resolve().parents[2]
ENV = os.environ
RUNTIME = Path(ENV["SOA_RUNTIME"])
WILDFLY = Path(ENV["WILDFLY_HOME"])
BASE = RUNTIME / "wildfly-base"
TLS = Path(ENV["AGENCY_TLS_DIR"])
LOGS = RUNTIME / "logs"
JAVA = str(Path(ENV["JAVA_HOME"]) / "bin/java")
CONSUL = "http://127.0.0.1:" + ENV["CONSUL_HTTP_PORT"]


def prepare():
    subprocess.run(["sh", ROOT / "deploy/jetty/prepare.sh"], check=True)
    subprocess.run(["sh", ROOT / "deploy/tls/generate.sh", TLS, "agency"], check=True)
    # HAProxy reads a PEM certificate chain followed by its private key.
    key = subprocess.check_output([
        "openssl", "pkcs12", "-in", TLS / "keystore.p12", "-nocerts", "-nodes",
        "-passin", "file:" + str(TLS / "keystore.password")])
    (TLS / "haproxy.pem").write_bytes((TLS / "agency.crt").read_bytes() + key)
    subprocess.run([ENV["HAPROXY_BIN"], "-c", "-f", ROOT / "deploy/lab3/haproxy.cfg"], check=True)

    config = BASE / "configuration"
    if not config.exists():
        shutil.copytree(WILDFLY / "standalone/configuration", config)
    # Use a dedicated strict-max pool for SLSBs. WildFly allocates instances on demand.
    maximum = int(ENV["EJB_POOL_MAX"])
    if maximum < 1:
        raise ValueError("EJB_POOL_MAX must be positive")
    document = minidom.parse(str(WILDFLY / "standalone/configuration/standalone.xml"))
    pool = next(node for node in document.getElementsByTagName("strict-max-pool")
                if node.getAttribute("name") == "slsb-strict-max-pool")
    pool.removeAttribute("derive-size")
    pool.setAttribute("max-pool-size", str(maximum))
    (config / "standalone.xml").write_bytes(document.toxml(encoding="utf-8"))
    deployments = BASE / "deployments"
    deployments.mkdir(exist_ok=True)
    shutil.copy2(ROOT / "flats-ejb/target/flats-ejb.jar", deployments)
    (deployments / "flats-ejb.jar.dodeploy").touch()

    ports = {key: int(ENV[name]) for key, name in [
        ("http", "CONSUL_HTTP_PORT"), ("server", "CONSUL_RPC_PORT"),
        ("serf_lan", "CONSUL_LAN_PORT"), ("serf_wan", "CONSUL_WAN_PORT"), ("dns", "CONSUL_DNS_PORT")]}
    ports.update(grpc=-1, grpc_tls=-1)
    (RUNTIME / "consul.json").write_text(json.dumps({
        "disable_update_check": True, "node_name": "soa-lab3", "server": True, "bootstrap_expect": 1,
        "bind_addr": "127.0.0.1", "client_addr": "127.0.0.1", "ports": ports,
        "connect": {"enabled": False}, "peering": {"enabled": False},
        "data_dir": str(RUNTIME / "consul-data"), "ui_config": {"enabled": True}}))


def start(name, command, **extra_env):
    with (LOGS / (name + ".log")).open("w") as log:
        process = subprocess.Popen(command, env={**ENV, **extra_env}, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
    print(f"{name} pid={process.pid}", flush=True)
    return process


def wait_for(name, processes, ready):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        for service, process in processes.items():
            if process.poll() is not None:
                raise RuntimeError(f"{service} exited; see {LOGS / (service + '.log')}")
        try:
            if ready():
                return
        except (OSError, URLError):
            pass
        time.sleep(1)
    raise TimeoutError(f"{name} did not become ready; see {LOGS}")


def consul_value(path):
    with urlopen(CONSUL + path, timeout=3) as response:
        return json.load(response)


def stop(processes):
    # Stop consumers before their upstreams, allowing Jetty to deregister.
    for process in reversed(list(processes.values())):
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()


def run():
    LOGS.mkdir(parents=True, exist_ok=True)
    with (RUNTIME / "stack.lock").open("a+") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        lock.seek(0)
        lock.truncate()
        lock.write(str(os.getpid()))
        lock.flush()
        prepare()
        processes = {}
        try:
            processes["consul"] = start("consul", [
                ENV["CONSUL_BIN"], "agent", "-config-file", RUNTIME / "consul.json"],
                SSL_CERT_FILE=ENV["FLATS_CERTIFICATE"])
            processes["wildfly"] = start("wildfly", [
                "sh", WILDFLY / "bin/standalone.sh", "-b", "127.0.0.1",
                "-Djboss.socket.binding.port-offset=" + ENV["WILDFLY_PORT_OFFSET"],
                "-Dwildfly.statistics-enabled=true"], JBOSS_BASE_DIR=str(BASE),
                JAVA_OPTS="-Xms64m -Xmx256m -XX:ActiveProcessorCount=2")
            wait_for("Consul leader", processes, lambda: consul_value("/v1/status/leader"))
            wait_for("EJB deployment", processes, lambda:
                     (BASE / "deployments/flats-ejb.jar.deployed").exists() and
                     not (BASE / "deployments/flats-ejb.jar.dodeploy").exists())
            processes["jetty"] = start("jetty", ["sh", ROOT / "deploy/jetty/start.sh"])
            wait_for("flats discovery", processes, lambda:
                     consul_value("/v1/health/service/flats?passing=true"))
            for index in (1, 2):
                name = f"agency-{index}"
                port = ENV[f"AGENCY_PORT_{index}"]
                processes[name] = start(name, [JAVA, "-Xms32m", "-Xmx192m", "-XX:ActiveProcessorCount=2",
                    "-jar", ROOT / "agency-service/target/agency-service.jar"], AGENCY_INSTANCE_PORT=port)
            for index in (1, 2):
                url = "https://localhost:" + ENV[f"AGENCY_PORT_{index}"] + "/agency/get-cheapest/1/1"
                wait_for(f"agency-{index}", processes, lambda: redirects(url))
            processes["haproxy"] = start("haproxy", [ENV["HAPROXY_BIN"], "-db", "-f", ROOT / "deploy/lab3/haproxy.cfg"])
            wait_for("HAProxy", processes, lambda: redirects(
                "https://localhost:" + ENV["AGENCY_HTTPS_PORT"] + "/agency/get-cheapest/1/1"))
            print("Ready: " + ENV["FLATS_PUBLIC_BASE_URL"] + "/app/index.html", flush=True)
            # HAProxy keeps serving if one agency instance goes down.
            processes["haproxy"].wait()
            raise RuntimeError("HAProxy stopped")
        finally:
            stop(processes)


def redirects(url):
    # Do not follow the browser-facing Location (it may refer to an SSH tunnel).
    address = urlsplit(url)
    context = ssl.create_default_context(cafile=TLS / "agency.crt")
    connection = http.client.HTTPSConnection(address.hostname, address.port, timeout=3, context=context)
    try:
        connection.request("GET", address.path)
        return connection.getresponse().status == 303
    finally:
        connection.close()


def stop_running_stack():
    path = RUNTIME / "stack.lock"
    if path.exists():
        with path.open("r+") as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                os.kill(int(lock.read()), signal.SIGTERM)
                fcntl.flock(lock, fcntl.LOCK_EX)
                print("Stopped")
                return
    print("Stack is already stopped")


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    try:
        if sys.argv[1:] == ["stop"]:
            stop_running_stack()
        elif len(sys.argv) == 1:
            run()
        else:
            sys.exit("Usage: run.sh [stop]")
    except KeyboardInterrupt:
        pass
