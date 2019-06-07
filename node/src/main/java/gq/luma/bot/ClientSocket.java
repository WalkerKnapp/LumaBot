package gq.luma.bot;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import gq.luma.bot.render.tasks.CoalescedSrcDemoRenderTask;
import gq.luma.bot.render.tasks.SingleSrcRenderTask;
import gq.luma.bot.render.fs.FSInterface;
import gq.luma.bot.render.Task;
import gq.luma.bot.uploader.GoogleDriveUploader;
import gq.luma.bot.utils.FileReference;
import okhttp3.OkHttpClient;
import okhttp3.WebSocket;
import org.apache.commons.io.IOUtils;
import org.bytedeco.javacpp.avformat;
import org.bytedeco.javacpp.avutil;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.bytedeco.javacpp.avformat.av_register_all;

public class ClientSocket extends WebSocketClient {
    public static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(64);

    public static OkHttpClient okhttpClient;
    public static FSInterface renderFS;
    public static String steamKey;

    private static final Logger logger = LoggerFactory.getLogger(ClientSocket.class);
    private static final String VERSION = "0.0.1-dev";
    private static final long FILE_PACKET_SIZE = 25 * 1024 * 1024;

    private GoogleDriveUploader uploader;
    private Task currentTask;
    private CompletableFuture<String> keysReceived;
    private CompletableFuture<String> fileReceive;

    private BiConsumer<JsonObject, File> finalOp;

    private ClientSocket(URI serverUri, Map<String,String> httpHeaders) {
        super(serverUri, new Draft_6455(), httpHeaders, 0);
        finalOp = (data, f) -> {
            try {
                JsonObject result = new JsonObject();
                result.set("upload-type", data.get("upload-type"));
                result.set("code", data.get("no-upload").asBoolean() ? "none" : uploader.uploadFile(f));
                result.set("no-upload", data.get("no-upload").asBoolean());
                result.set("thumbnail", this.currentTask.getThumbnail());
                result.set("dir", data.get("dir"));
                send("RenderFinished>>" + result.toString());
            } catch (Exception e){
                logger.error("Encountered an error while uploading file: ", e);
            }
        };
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        logger.info("Opened connection to server. Status: " + serverHandshake.getHttpStatus() + " with message " + serverHandshake.getHttpStatusMessage());

        executorService.submit(() -> {
            try {
                logger.info("Requesting keys");
                keysReceived = new CompletableFuture<>();
                send("KeyRequest>>steam,gdrive");
                JsonObject keys = Json.parse(keysReceived.get(10, TimeUnit.SECONDS)).asObject();
                steamKey = keys.get("steam").asString();
                uploader = new GoogleDriveUploader(keys.get("gdrive").asString());
            } catch (Exception e) {
                logger.error("Encountered exception while requesting keys: ", e);
                close(1000, "Failed to receive keys.");
                System.exit(-1);
            }
        });
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
                JsonObject data = Json.parse(content).asObject();
                this.currentTask = parseTask(data);
                executorService.submit(() -> processCurrentTask(data));
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

    private void processCurrentTask(JsonObject data){
        for(JsonValue v : data.get("requiredFiles").asArray().values()){
            if(!new File(FileReference.tempDir, v.asString()).exists()) {
                fileReceive = new CompletableFuture<>();
                send("FileRequest>>" + v.asString());
                if (logger.isDebugEnabled()) logger.debug("Requesting file: {}", v.asString());
                fileReceive.join();
            }
        }

        this.currentTask.execute().thenAccept(f -> finalOp.accept(data, f)).exceptionally(t -> {
            logger.error("Got Error in Task execute: ", t);
            send("RenderError>>" + t.getMessage());
            return null;
        });
    }

    private Task parseTask(JsonObject data) throws LumaException {
        if (data.get("type").asString().equalsIgnoreCase("single-source")) {
            return new SingleSrcRenderTask(data);
        } else if (data.get("type").asString().equalsIgnoreCase("coalesced")) {
            return new CoalescedSrcDemoRenderTask(data);
        } else {
            throw new IllegalArgumentException("Type: " + data.get("type").asString() + " not found.");
        }
    }

    @Override
    public void onMessage(ByteBuffer buffer){
        logger.debug("Got message for a data set.");

        byte[] pathBuffer = new byte[256];
        buffer.get(pathBuffer, 0, 256);
        String path = new String(pathBuffer).trim();
        logger.info("Got data for file: {}", path);
        File file = new File(FileReference.tempDir, path);
        if(!file.getParentFile().exists() && !file.getParentFile().mkdirs()){
            logger.error("Unable to create parent directory.");
        }

        byte[] byteData = new byte[buffer.remaining()];
        buffer.get(byteData, 0, buffer.remaining());
        try (FileOutputStream fos = new FileOutputStream(file);
             ByteArrayInputStream bais = new ByteArrayInputStream(byteData)){
            IOUtils.copy(bais, fos);
        } catch (IOException e) {
            logger.error("Encountered an error while downloading the file: ", e);
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

    public void sendFile(File f) throws IOException {
        try(FileInputStream fis = new FileInputStream(f)){
            sendStream(f.getName(), fis);
        }
    }

    public void sendStream(String name, InputStream is) throws IOException {
        byte[] buf = new byte[(int) FILE_PACKET_SIZE];
        int bytesRead;
        long offest;
        do {
            bytesRead = is.read(buf);
            if(bytesRead > 0){
                //ByteBuffer buffer = ByteBuffer.allocate(bytesRead +);
                //new BinaryStreamFrame(name, )
                send(ByteBuffer.allocate(bytesRead).put(buf, 0, bytesRead));
            }
        } while (bytesRead == FILE_PACKET_SIZE);
    }

    public static void main(String[] args) throws Exception {
        if(args.length < 3){
            logger.error("Format: java -jar client.jar {host} {name} {token}");
            return;
        }

        //av_register_all();

        //for(Field field : avutil.class.getFields()){
        //   if(field.getName().startsWith("AVERROR")){
        //       try {
        //           System.out.println(field.getName() + " - " + field.get(null).toString());
        //       } catch (IllegalAccessException e) {
        //           e.printStackTrace();
        //      }
        //    }
        //}

        okhttpClient = new OkHttpClient();

        Path mount = Paths.get(FileReference.tempDir.getAbsolutePath(), "mount");
        if(logger.isDebugEnabled()) {
            logger.debug(mount.toString());
        }
        renderFS = FSInterface.openFuse(mount).join();

        Map<String, String> headers = new HashMap<>();
        headers.put("token", args[2]);
        headers.put("name", args[1]);
        headers.put("version", VERSION);
        WebSocketClient client = new ClientSocket(new URI(args[0]), headers);
        client.setSocket(createSocket());
        //client.setSocket(new Socket());

        File mountPoint = new File(SrcGame.getByDirName("portal2").getGameDir(), "export");

        if (mountPoint.exists() && !mountPoint.delete()) {
            throw new LumaException("Failed to remove the frame export directory.");
        }

        Files.createSymbolicLink(mountPoint.toPath(), ClientSocket.renderFS.getMountPoint());

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
