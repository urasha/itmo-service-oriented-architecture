import org.w3c.dom.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

class ConfigureDomain {
    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            throw new IllegalArgumentException(
                    "domain tlsDirectory masterPasswordFile HTTPSPort bindAddress flatsCertificate"
                            + " agencyConfig");
        }
        Path domain = Path.of(args[0]).toAbsolutePath();
        Path tls = Path.of(args[1]).toAbsolutePath();
        int port = Integer.parseInt(args[3]);
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("HTTPS port must be 1024..65535");
        }
        Path config = domain.resolve("config/domain.xml");
        Path pidFile = domain.resolve("config/pid");
        if (Files.exists(pidFile)) {
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                throw new IllegalStateException("Stop the domain before configuration");
            }
        }
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var document = factory.newDocumentBuilder().parse(config.toFile());
        Element serverConfig = null;
        var configs = document.getElementsByTagName("config");
        for (int i = 0; i < configs.getLength(); i++) {
            Element candidate = (Element) configs.item(i);
            if (candidate.getAttribute("name").equals("server-config")) {
                serverConfig = candidate;
            }
        }
        if (serverConfig == null) {
            throw new IllegalStateException("No server-config");
        }
        configureListeners(serverConfig, port, args[4]);
        configureTls(document, serverConfig);
        configureVirtualServers(serverConfig);
        configureJvm(document, serverConfig, Path.of(args[6]));
        configureKeyStores(domain, tls, Path.of(args[2]), Path.of(args[5]));
        if (!Files.exists(domain.resolve("config/domain.xml.before-agency"))) {
            Files.copy(config, domain.resolve("config/domain.xml.before-agency"));
        }
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(new DOMSource(document), new StreamResult(config.toFile()));
        System.out.println(
                "Configured HTTPS-only Payara application listener; admin bound to loopback.");
    }

    private static void configureListeners(Element serverConfig, int port, String bindAddress) {
        var listeners = serverConfig.getElementsByTagName("network-listener");
        for (int i = 0; i < listeners.getLength(); i++) {
            Element listener = (Element) listeners.item(i);
            String name = listener.getAttribute("name");
            if (name.equals("admin-listener")) {
                listener.setAttribute("address", "127.0.0.1");
            } else if (name.equals("http-listener-2")) {
                listener.setAttribute("address", bindAddress);
                listener.setAttribute("port", Integer.toString(port));
                listener.setAttribute("enabled", "true");
            } else {
                listener.setAttribute("enabled", "false");
            }
        }
    }

    private static void configureTls(Document document, Element serverConfig) {
        var protocols = serverConfig.getElementsByTagName("protocol");
        for (int i = 0; i < protocols.getLength(); i++) {
            Element protocol = (Element) protocols.item(i);
            if (!protocol.getAttribute("name").equals("http-listener-2")) {
                continue;
            }
            protocol.setAttribute("security-enabled", "true");
            Element ssl = (Element) protocol.getElementsByTagName("ssl").item(0);
            ssl.setAttribute("cert-nickname", "agency");
            ssl.setAttribute("ssl3-enabled", "false");
            ssl.setAttribute("tls-enabled", "false");
            ssl.setAttribute("tls11-enabled", "false");
            ssl.setAttribute("tls12-enabled", "true");
            property(document, ssl, "tls13-enabled", "true");
            Element http = (Element) protocol.getElementsByTagName("http").item(0);
            http.setAttribute("max-post-size", "0");
        }
    }

    private static void configureVirtualServers(Element serverConfig) {
        var virtualServers = serverConfig.getElementsByTagName("virtual-server");
        for (int i = 0; i < virtualServers.getLength(); i++) {
            Element vs = (Element) virtualServers.item(i);
            if (vs.getAttribute("id").equals("server")) {
                vs.setAttribute("network-listeners", "http-listener-2");
            }
        }
    }

    private static void configureJvm(Document document, Element serverConfig, Path appConfig) {
        var javaConfig = (Element) serverConfig.getElementsByTagName("java-config").item(0);
        var options = javaConfig.getElementsByTagName("jvm-options");
        for (int i = options.getLength() - 1; i >= 0; i--) {
            Node option = options.item(i);
            if (option.getTextContent().startsWith("-Dagency.config=")
                    || option.getTextContent().startsWith("-Dfile.encoding=")
                    || option.getTextContent().startsWith("-Xmx")) {
                javaConfig.removeChild(option);
            }
        }
        Element appOption = document.createElement("jvm-options");
        appOption.setTextContent("-Dagency.config=" + appConfig.toAbsolutePath());
        javaConfig.appendChild(appOption);
        Element memory = document.createElement("jvm-options");
        memory.setTextContent("-Xmx512m");
        javaConfig.appendChild(memory);
        Element encoding = document.createElement("jvm-options");
        encoding.setTextContent("-Dfile.encoding=UTF-8");
        javaConfig.appendChild(encoding);
    }

    private static void configureKeyStores(
            Path domain, Path tls, Path masterPassword, Path flatsCertificate) throws Exception {
        char[] master =
                Files.readString(masterPassword, StandardCharsets.UTF_8).trim().toCharArray();
        char[] keyPassword =
                Files.readString(tls.resolve("keystore.password"), StandardCharsets.UTF_8)
                        .trim()
                        .toCharArray();
        try {
            KeyStore source = KeyStore.getInstance("PKCS12");
            try (var in = Files.newInputStream(tls.resolve("keystore.p12"))) {
                source.load(in, keyPassword);
            }
            Path domainStore = domain.resolve("config/keystore.p12");
            KeyStore destination = KeyStore.getInstance(domainStore.toFile(), master);
            destination.setKeyEntry(
                    "agency",
                    source.getKey("agency", keyPassword),
                    master,
                    source.getCertificateChain("agency"));
            try (var out = Files.newOutputStream(domainStore)) {
                destination.store(out, master);
            }
            Path trustPasswordFile = tls.resolve("flats-truststore.password");
            if (!Files.exists(trustPasswordFile)) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                Files.writeString(
                        trustPasswordFile,
                        Base64.getEncoder().encodeToString(random),
                        StandardCharsets.US_ASCII,
                        StandardOpenOption.CREATE_NEW);
            }
            char[] trustPassword = Files.readString(trustPasswordFile).trim().toCharArray();
            KeyStore trust = KeyStore.getInstance("PKCS12");
            trust.load(null, trustPassword);
            try (var input = Files.newInputStream(flatsCertificate)) {
                trust.setCertificateEntry(
                        "flats",
                        CertificateFactory.getInstance("X.509").generateCertificate(input));
            }
            Path trustFile = tls.resolve("flats-truststore.p12");
            try (var out = Files.newOutputStream(trustFile)) {
                trust.store(out, trustPassword);
            }
            Arrays.fill(trustPassword, '\0');
            for (Path privateFile : List.of(trustPasswordFile, trustFile, domainStore)) {
                if (Files.getFileStore(privateFile).supportsFileAttributeView("posix")) {
                    Files.setPosixFilePermissions(
                            privateFile, PosixFilePermissions.fromString("rw-------"));
                }
            }
        } finally {
            Arrays.fill(master, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }

    private static void property(Document doc, Element parent, String name, String value) {
        var properties = parent.getElementsByTagName("property");
        for (int i = 0; i < properties.getLength(); i++) {
            Element p = (Element) properties.item(i);
            if (p.getAttribute("name").equals(name)) {
                p.setAttribute("value", value);
                return;
            }
        }
        Element p = doc.createElement("property");
        p.setAttribute("name", name);
        p.setAttribute("value", value);
        parent.appendChild(p);
    }
}
