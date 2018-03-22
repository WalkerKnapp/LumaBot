package gq.luma.bot;

import gq.luma.bot.reference.KeyReference;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

public class WSClientTest extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(WSClientTest.class);

    public WSClientTest(URI serverUri, Map<String, String> headers) {
        super(serverUri, new Draft_6455(), headers, 0);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        logger.info("Opened connection to server. Status: " + serverHandshake.getHttpStatus() + " with message " + serverHandshake.getHttpStatusMessage());
    }

    @Override
    public void onMessage(String message) {
        logger.debug("Received message from server: %s", message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("Lost connection to server with exit code " + code + " from " + (remote ? "server" : "client") + ", with reason " + reason);
    }

    @Override
    public void onError(Exception e) {
        logger.debug("Encountered error: ", e);
    }

    public static void main(String[] args) throws Exception {


        new KeyReference().startService();
        char[] keystorePass = KeyReference.keystorePass.toCharArray();

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(WSClientTest.class.getResourceAsStream("/keystore.jks"), keystorePass);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        SSLSocketFactory factory = sslContext.getSocketFactory();
        Map<String, String> headers = new HashMap<>();
        headers.put("token", "wj3e8hjn82WxwL3a3qLMHFwgxj0pejzwcbrUbgE6nz60nMd4NZCpGsHMF87fz0VRsQXzGygS9MUzXGc5ONBw");
        headers.put("name", "GamingsNode");
        WebSocketClient client = new WSClientTest(new URI("wss://localhost:8887"), headers);
        client.setSocket(factory.createSocket());

        client.connect();
    }
}
