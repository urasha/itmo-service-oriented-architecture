package ru.itmo.soa.agency;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.itmo.soa.contract.JsonCodec;
import javax.net.ssl.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import static org.junit.jupiter.api.Assertions.*;

/** HTTPS fixtures test client failures; Payara integration is covered separately by AgencyHttpsIT. */
class FlatsClientTest {
    @TempDir static Path directory;
    private static final String PASSWORD = "test-fixture-only";
    private static Path trusted, untrusted, passwordFile;
    private HttpsServer server;
    private ExecutorService executor;
    private int status = 200;
    private String body = AgencyServiceTest.FLAT;
    private String contentType = "application/json; charset=utf-8";
    private boolean delay;
    private boolean slowBody;

    @BeforeAll static void certificates() throws Exception {
        passwordFile = directory.resolve("test.password"); Files.writeString(passwordFile,PASSWORD);
        String keytool = Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        for (String name : List.of("good","wrong-host")) {
            Path store = directory.resolve(name + ".p12");
            Process process = new ProcessBuilder(keytool,"-genkeypair","-alias","server","-keyalg","RSA","-keysize","2048",
                    "-validity","2","-storetype","PKCS12","-keystore",store.toString(),"-storepass",PASSWORD,"-keypass",PASSWORD,
                    "-dname","CN=HTTPS test","-ext","SAN=dns:" + (name.equals("good") ? "localhost" : "wrong.example"),"-noprompt")
                    .redirectErrorStream(true).redirectOutput(directory.resolve(name + ".log").toFile()).start();
            assertEquals(0, process.waitFor(), () -> "keytool failed: " + directory);
        }
        KeyStore trust = KeyStore.getInstance("PKCS12"); trust.load(null,PASSWORD.toCharArray());
        for (String name : List.of("good","wrong-host")) {
            KeyStore keys = KeyStore.getInstance(directory.resolve(name + ".p12").toFile(), PASSWORD.toCharArray());
            trust.setCertificateEntry(name, keys.getCertificate("server"));
        }
        trusted=directory.resolve("trust.p12"); try(var out=Files.newOutputStream(trusted)){trust.store(out,PASSWORD.toCharArray());}
        KeyStore empty=KeyStore.getInstance("PKCS12");empty.load(null,PASSWORD.toCharArray());
        untrusted=directory.resolve("empty.p12");try(var out=Files.newOutputStream(untrusted)){empty.store(out,PASSWORD.toCharArray());}
    }
    private void start(String certificate) throws Exception {
        KeyStore keys = KeyStore.getInstance(directory.resolve(certificate + ".p12").toFile(),PASSWORD.toCharArray());
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,PASSWORD.toCharArray());
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),null,null);
        server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/flats/1",exchange -> {
            try {
                if(delay) Thread.sleep(1500);
                byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",contentType);exchange.sendResponseHeaders(status,bytes.length);
                if(slowBody) { exchange.getResponseBody().write(bytes,0,1);exchange.getResponseBody().flush();Thread.sleep(1500);exchange.getResponseBody().write(bytes,1,bytes.length-1); }
                else exchange.getResponseBody().write(bytes);
            } catch(InterruptedException e){Thread.currentThread().interrupt();} finally {exchange.close();}
        });server.start();
    }
    private FlatsClient client(Path trust,Duration timeout,int port) {
        var settings = new AgencySettings(URI.create("https://localhost:"+port),URI.create("https://public.example"),trust,
                passwordFile,Duration.ofSeconds(2),timeout,"https://client.example");
        return new FlatsClient(settings, JsonCodec.createMapper());
    }
    private FlatsClient client(){return client(trusted,Duration.ofSeconds(3),server.getAddress().getPort());}
    private void error(int expected){assertEquals(expected,assertThrows(AgencyException.class,()->client().get(1)).status());}
    @AfterEach void stop(){if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow();}

    @Test void readsCompleteFlatOverVerifiedHttps() throws Exception {start("good");assertEquals("100.01",client().get(1).price().toPlainString());}
    @Test void preserves404() throws Exception {status=404;start("good");error(404);}
    @ParameterizedTest @ValueSource(ints={201,204,303,400,422,500,503})
    void mapsOtherUpstreamStatusesTo502(int value)throws Exception {status=value;start("good");error(502);}
    @ParameterizedTest @ValueSource(strings={"{}","[]","null","broken","{\"id\":1,\"id\":2}"})
    void rejectsMalformedOrIncompleteJson(String value)throws Exception{body=value;start("good");error(502);}
    @ParameterizedTest @ValueSource(strings={"id","creationDate","transport","name","coordinates","new","house"})
    void rejectsMissingRequiredProperties(String field)throws Exception{var mapper=JsonCodec.createMapper();var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(body);node.remove(field);body=mapper.writeValueAsString(node);start("good");error(502);}
    @Test void rejectsWrongId()throws Exception{body=body.replace("\"id\":1","\"id\":2");start("good");error(502);}
    @Test void rejectsWrongContentType()throws Exception{contentType="text/html";start("good");error(502);}
    @Test void rejectsInvalidNestedModel()throws Exception{body=body.replace("\"numberOfFloors\":2","\"numberOfFloors\":0");start("good");error(502);}
    @Test void rejectsUntrustedCertificate()throws Exception{start("good");assertEquals(502,assertThrows(AgencyException.class,()->client(untrusted,Duration.ofSeconds(3),server.getAddress().getPort()).get(1)).status());}
    @Test void verifiesHostnameEvenWhenCertificateTrusted()throws Exception{start("wrong-host");error(502);}
    @Test void mapsHeaderTimeoutTo504()throws Exception{delay=true;start("good");assertEquals(504,assertThrows(AgencyException.class,()->client(trusted,Duration.ofMillis(700),server.getAddress().getPort()).get(1)).status());}
    @Test void boundsEntireBodyReadTime()throws Exception{slowBody=true;start("good");assertEquals(504,assertThrows(AgencyException.class,()->client(trusted,Duration.ofMillis(700),server.getAddress().getPort()).get(1)).status());}
    @Test void mapsConnectionRefusalTo502()throws Exception{int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}final int closedPort=port;assertEquals(502,assertThrows(AgencyException.class,()->client(trusted,Duration.ofSeconds(2),closedPort).get(1)).status());}
}
