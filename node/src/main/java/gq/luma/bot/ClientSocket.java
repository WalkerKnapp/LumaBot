package gq.luma.bot;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import gq.luma.bot.render.tasks.CoalescedSrcDemoRenderTask;
import gq.luma.bot.render.structure.RenderSettings;
import gq.luma.bot.render.tasks.SingleSrcRenderTask;
import gq.luma.bot.render.fs.FSInterface;
import gq.luma.bot.render.Task;
import gq.luma.bot.uploader.GoogleDriveUploader;
import gq.luma.bot.utils.FileReference;
import gq.luma.bot.utils.LumaException;
import okhttp3.OkHttpClient;
import org.apache.commons.io.IOUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ClientSocket extends WebSocketClient {
    public static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(6);

    public static OkHttpClient okhttpClient;
    public static FSInterface renderFS;
    public static String steamKey;

    private static final Logger logger = LoggerFactory.getLogger(ClientSocket.class);
    private static final String VERSION = "0.0.1-dev";

    private GoogleDriveUploader uploader;
    private Task currentTask;
    private CompletableFuture<String> keysReceived;
    private CompletableFuture<String> fileReceive;

    private ClientSocket(URI serverUri, Map<String,String> httpHeaders) {
        super(serverUri, new Draft_6455(), httpHeaders, 0);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        logger.info("Opened connection to server. Status: " + serverHandshake.getHttpStatus() + " with message " + serverHandshake.getHttpStatusMessage());
        //logger.info("Opened connection to server.");

        new Thread(() -> {
            try {
                logger.info("Requesting keys");
                keysReceived = new CompletableFuture<>();
                send("KeyRequest>>steam,gdrive");
                JsonObject keys = Json.parse(keysReceived.get(10, TimeUnit.SECONDS)).asObject();
                steamKey = keys.getString("steam", null);
                uploader = new GoogleDriveUploader(keys.getString("gdrive", null));
            } catch (Exception e) {
                logger.error("Encountered exception while requesting keys: ", e);
                close(1000, "Failed to receive keys.");
                System.exit(-1);
            }
        }).start();
    }

    @Override
    public void onMessage(String message) {
        try {
            logger.info("Received message from server: {}", message);
            String code = message.split(">>")[0];
            String content = message.split(">>").length > 1 ? message.split(">>")[1] : null;
            logger.debug("Code: {}", code);
            logger.debug("Content: {}", content);
            if (code.equalsIgnoreCase("RenderStart") && content != null) {
                logger.debug("Found Renderstart!");
                JsonObject object = Json.parse(content).asObject();
                Task task;
                if (object.getString("type", "").equalsIgnoreCase("single-source")) {
                    task = new SingleSrcRenderTask(SrcDemo.of(object.get("demo").asObject()),
                            RenderSettings.of(object.get("settings").asObject()),
                            object.getString("name", ""),
                            object.getString("dir", ""));
                } else if (object.getString("type", "").equalsIgnoreCase("coalesced")) {
                    task = new CoalescedSrcDemoRenderTask(object.getString("name", ""),
                            new File(FileReference.tempDir, object.getString("dir", "")),
                            RenderSettings.of(object.get("settings").asObject()),
                            object.get("demos").asArray().values().stream().map(JsonValue::asObject).map(SrcDemo::ofUnchecked).collect(Collectors.toList()));
                } else {
                    throw new IllegalArgumentException("Type: " + object.getString("type", "{null}") + " not found.");
                }
                this.currentTask = task;
                executorService.submit(() -> {
                    for(JsonValue v : object.get("requiredFiles").asArray().values()){
                        fileReceive = new CompletableFuture<>();
                        send("FileRequest>>" + v.asString());
                        System.out.println("Requesting file: " + v.asString());
                        fileReceive.join();
                    }

                    task.execute().thenAccept(f -> {
                        try {
                            JsonObject result = new JsonObject();
                            result.set("upload-type", object.get("upload-type"));
                            //result.set("code", uploader.uploadFile(f));
                            result.set("code", "");
                            result.set("thumbnail", task.getThumbnail());
                            result.set("dir", object.get("dir"));
                            send("RenderFinished>>" + result.toString());
                        } catch (Exception e){
                            logger.error("Encountered an error while uploading file: ", e);
                        }
                    }).exceptionally(t -> {
                        logger.error("Got Error in Task execute: ", t);
                        send("RenderError>>" + t.getMessage());
                        return null;
                    });
                });

            } else if (code.equalsIgnoreCase("RenderStatus")) {
                send("RenderStatus>>" + currentTask.getStatus());
            } else if (code.equalsIgnoreCase("RenderCancel")) {
                currentTask.cancel();
                send("RenderCanceled");
            } else if(code.equalsIgnoreCase("Keys")){
                keysReceived.complete(content);
            }
        } catch (IOException | LumaException e){
            logger.error("Found error: " + e);
            if(currentTask != null) {
                logger.debug("Sending: RenderError>>{}", e.getMessage());
                send("RenderError>>" + e.getMessage());
            }
        }
    }

    @Override
    public void onMessage(ByteBuffer buffer){
        System.out.println("Got message for data.");

        byte[] pathBuffer = new byte[256];
        buffer.get(pathBuffer, 0, 256);
        String path = new String(pathBuffer).trim();
        System.out.println("Got data for file: " + path);
        File file = new File(FileReference.tempDir, path);
        if(!file.getParentFile().exists() && !file.getParentFile().mkdirs()){
            System.err.println("Unable to create parent directory.");
        }

        byte[] byteData = new byte[buffer.remaining()];
        buffer.get(byteData, 0, buffer.remaining());
        try (FileOutputStream fos = new FileOutputStream(file);
             ByteArrayInputStream bais = new ByteArrayInputStream(byteData)){
            IOUtils.copy(bais, fos);
        } catch (IOException e) {
            e.printStackTrace();
        }

        send("FileReceived>>" + path);
        logger.debug("Received file: {}", path);
        fileReceive.complete(path);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("Lost connection to server with exit code {} from {}, with reason: {}", code, remote ? "remote" : "local", reason);
    }

    @Override
    public void onError(Exception t) {
        logger.error("Encountered Error: ", t);
    }

    public static void main(String[] args) throws Exception {
        if(args.length < 3){
            logger.error("Format: java -jar client.jar {host} {name} {token}");
            return;
        }

        okhttpClient = new OkHttpClient();

        Path mount = Paths.get(FileReference.tempDir.getAbsolutePath(), "mount");
        logger.debug(mount.toString());
        renderFS = FSInterface.openFuse(mount).join();

        Map<String, String> headers = new HashMap<>();
        headers.put("token", args[2]);
        headers.put("name", args[1]);
        headers.put("version", VERSION);
        WebSocketClient client = new ClientSocket(new URI(args[0]), headers);
        client.setSocket(createSocket());

        client.connectBlocking();
    }

    private static Socket createSocket() throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, KeyManagementException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(ClientSocket.class.getResourceAsStream("/Luma.cert"));

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null);
        ks.setCertificateEntry("default", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

        SSLSocketFactory factory = sslContext.getSocketFactory();

        return factory.createSocket();
    }
}
