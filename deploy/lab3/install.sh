#!/bin/sh
set -eu
umask 077
root=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
set -a
. "${SOA_ENV_FILE:-$root/deploy/local.env}"
. "$root/deploy/versions.env"
cache="$SOA_RUNTIME/downloads"
mkdir -p "$cache"

download() {
    file="$cache/${1##*/}"
    if [ ! -f "$file" ]; then
        curl --fail --location --retry 3 --output "$file.part" "$1"
        mv "$file.part" "$file"
    fi
    actual=$(openssl dgst -sha256 "$file" | awk '{print $NF}')
    [ "$actual" = "$2" ] || { echo "Unexpected SHA-256: $file" >&2; exit 1; }
}

central=https://repo.maven.apache.org/maven2
if [ ! -f "$JETTY_HOME/start.jar" ]; then
    download "$central/org/eclipse/jetty/jetty-home/$JETTY_VERSION/jetty-home-$JETTY_VERSION.zip" \
        7cd5711f3813e61a10c65cb38824cb34447e3f0d8e88443e48c80ab5fd635e79
    unzip -q "$file" -d "$SOA_RUNTIME"
fi
if [ ! -f "$WILDFLY_HOME/jboss-modules.jar" ]; then
    download "$central/org/wildfly/wildfly-dist/$WILDFLY_VERSION/wildfly-dist-$WILDFLY_VERSION.zip" \
        1e4ac11ac3970ae6a73156dd11b88ce5243220d96ab346498c88c562262a62ac
    unzip -q "$file" -d "$SOA_RUNTIME"
fi
if [ ! -x "$CONSUL_BIN" ]; then
    case "$(uname -s)/$(uname -m)" in
        Linux/x86_64) platform=linux_amd64; digest=2dfb63fcabe9f15b956cf408248d9ebe36cfd662ca182352942a3bd4e5d5faca;;
        FreeBSD/amd64) platform=freebsd_amd64; digest=94e43f4f4dca615039a94348b99604336fa444f422479c9f00d8502b453020d0;;
        *) echo "Set CONSUL_BIN to a Consul installation for this platform" >&2; exit 1;;
    esac
    download "https://releases.hashicorp.com/consul/$CONSUL_VERSION/consul_${CONSUL_VERSION}_$platform.zip" "$digest"
    mkdir -p "$(dirname "$CONSUL_BIN")"
    unzip -p "$file" consul > "$CONSUL_BIN.part"
    chmod 700 "$CONSUL_BIN.part"
    mv "$CONSUL_BIN.part" "$CONSUL_BIN"
fi
"$JAVA_HOME/bin/java" -version
"$CONSUL_BIN" version
"$HAPROXY_BIN" -v
