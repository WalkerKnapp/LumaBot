package gq.luma.bot.services.node;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.Luma;
import gq.luma.bot.services.Database;
import gq.luma.bot.services.Service;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class NodeServer extends WebSocketServer implements Service {

    private static final Logger logger = LoggerFactory.getLogger(NodeServer.class);

    private Map<String, Node> currentConnections = new ConcurrentHashMap<>();

    public NodeServer() {
        super(new InetSocketAddress("10.0.0.24", 8887));
    }

    public boolean hasOpenNode(){
        return currentConnections.values().stream().anyMatch(Node::isAvailable);
    }

    public Node openNode(){
        System.out.println("Got request for open node.");
        return currentConnections.values().stream().filter(Node::isAvailable).findFirst().orElseThrow(() -> new IllegalArgumentException("No open node found. :("));
    }

    public Optional<Node> getNodeRunningTask(Task task){
        return currentConnections.values().stream().filter(node -> node.isUsingTask(task)).findFirst();
    }

    @Override
    public void startService() throws Exception {

        //WebSocketImpl.DEBUG = true;

        char[] keystorePass = KeyReference.keystorePass.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(NodeServer.class.getResourceAsStream("/keystore.jks"), keystorePass);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, keystorePass);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        this.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
        this.start();
    }

    @Override
    public void onStart() {
        logger.debug("Starting websocket!");
    }

    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket connection, Draft draft, ClientHandshake handshake) throws InvalidDataException {
        ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(connection, draft, handshake);
        System.out.println("Got handshake request from: " + connection.getRemoteSocketAddress().getHostName());
        if(!handshake.hasFieldValue("token")){
            throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "Unauthorized");
        }
        if(!handshake.hasFieldValue("name")){
            throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "No name");
        }
        try {
            String host = connection.getRemoteSocketAddress().getHostName();
            String token = handshake.getFieldValue("token");
            if (Luma.database.getNodeByToken(token).isPresent()) {
                if (currentConnections.containsKey(host)) {
                    throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "Preexisting frames exist. Please retry.");
                }
                logger.info("Client authenticated at: " + host + " with token: " + token);
                currentConnections.put(host, new Node(token, host, connection));
                Luma.database.updateNode(token, host, host, handshake.getFieldValue("name"));
            } else {
                throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "Unauthorized");
            }
        } catch (SQLException e){
            e.printStackTrace();
            throw new InvalidDataException(CloseFrame.ABNORMAL_CLOSE);
        }
        return builder;
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket webSocket, ClientHandshake clientHandshake) {
        logger.info("Connection opened with client: " + webSocket.getRemoteSocketAddress().toString());
    }

    @Override
    public void onClose(org.java_websocket.WebSocket webSocket, int code, String reason, boolean remote) {
        String host = webSocket.getRemoteSocketAddress().getHostName();
        logger.info("Lost connection to client: " + host + " with exit code " + code + " from " + (remote ? "client" : "server") + ", with reason " + reason);
        try {
            currentConnections.get(host).setTaskRendering(false);
            currentConnections.get(host).updateSession(null);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        currentConnections.remove(host);
    }

    @Override
    public void onMessage(org.java_websocket.WebSocket webSocket, String message) {
        logger.info("Received message from " + webSocket.getRemoteSocketAddress().toString() + ": " + message);
        String code = message.split(">>")[0];
        String content = message.split(">>")[1];
        if(code.equalsIgnoreCase("KeyRequest")){
            System.out.println("Parsed request for keys.");
            try {
                String[] requests = content.split(",");
                System.out.println(Arrays.toString(requests));
                JsonObject ret = new JsonObject();
                for (String r : requests) {
                    if (r.equalsIgnoreCase("steam")) {
                        ret.set("steam", KeyReference.steamKey);
                    } else if (r.equalsIgnoreCase("gdrive")) {
                        ret.set("gdrive", new String(IOUtils.toByteArray(KeyReference.gdriveServiceAcc.toURI())));
                    }
                }
                System.out.println("Sending: " + ret.toString());
                webSocket.send("Keys>>" + ret.toString());
                return;
            } catch (IOException e){
                logger.error("Encountered error: ", e);
            }
        } else if(code.equalsIgnoreCase("FileRequest")){
            System.out.println("Parsed request for file: " + content);
            File resource = new File(FileReference.tempDir, content);
            byte[] byteContent = new byte[(int) (256 + resource.length())];
            System.out.println("Adding request bytes to buffer");
            System.arraycopy(content.getBytes(), 0, byteContent, 0, content.getBytes().length);

            System.out.println("Adding file bytes to buffer");
            try {
                System.out.println("We out here");
                System.arraycopy(FileUtils.readFileToByteArray(resource), 0, byteContent, 256, (int) resource.length());
                System.out.println("We trying");
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("Sending content... length: " + byteContent.length);
            //System.out.println("Sending byte content: " + new String(Hex.encodeHex(byteContent)));
            //((WebSocketImpl)webSocket).send((Collection<Framedata>)webSocket.getDraft().createFrames(ByteBuffer.wrap(byteContent), false));
            new Thread(() -> webSocket.send(byteContent)).start();
            return;
        }
        String host = webSocket.getRemoteSocketAddress().getHostName();
        if(currentConnections.containsKey(host)){
            logger.debug("Fully authenticated on token: " + currentConnections.get(host).getToken());
            currentConnections.get(host).onMessage(message);
        } else {
            logger.error(host + " made a request without being in the connections.");
        }
    }

    @Override
    public void onError(org.java_websocket.WebSocket webSocket, Exception e) {
        //logger.error("Encountered error on socket: " + webSocket.getRemoteSocketAddress() != null ? webSocket.getRemoteSocketAddress().toString() : "null", e);
        e.printStackTrace();
    }
}
