package gq.luma.bot.services.node;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import gq.luma.bot.services.Database;
import gq.luma.bot.utils.LumaException;
import org.java_websocket.WebSocket;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class Node {
    private String token;
    private String host;
    private WebSocket webSocket;

    private Task currentTask;
    private CompletableFuture<JsonObject> taskCf;
    private CompletableFuture<String> requestCf;
    private CompletableFuture<Void> cancelCf;

    Node(String token, String host, WebSocket webSocket){
        this.token = token;
        this.host = host;
        this.webSocket = webSocket;
    }

    public void updateSession(String session) throws SQLException {
        Database.updateNodeSession(this.token, session);
    }

    public String getLatestName() {
        try {
            return Database.getNodeByToken(token).orElseThrow(IllegalArgumentException::new).getString("lastKnownName");
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public CompletableFuture<JsonObject> scheduleTask(Task task) {
        System.out.println("Got Task scheduled on node: " + this.host);
        this.currentTask = task;
        task.setRendering(true);
        this.taskCf = new CompletableFuture<>();

        System.out.println("Trying to serialize the task...");
        try {
            String sending = "RenderStart>>" + task.serialize().set("no-upload", task.isNoUpload()).set("upload-type", "gdrive").toString();
            System.out.println("Sending: " + sending);
            webSocket.send(sending);

        } catch (IOException e){
            e.printStackTrace();
            this.taskCf.completeExceptionally(e);
        }

        return this.taskCf;
    }

    public CompletableFuture<String> requestStatus(){
        this.requestCf = new CompletableFuture<>();
        webSocket.send("RenderStatus");
        System.out.println("Sending: RenderStatus");
        return this.requestCf;
    }

    public CompletableFuture<Void> requestCancel(){
        this.cancelCf = new CompletableFuture<>();
        webSocket.send("RenderCancel");
        System.out.println("Sending: RenderCancel");
        this.cancelCf.thenAccept(v -> {
            currentTask = null;
            taskCf = null;
        });
        return this.cancelCf;
    }

    public boolean isUsingTask(Task task){
        return task.equals(this.currentTask);
    }

    void onMessage(String message){
        System.out.println("Got: " + message);
        String code = message.split(">>")[0];
        String content = message.split(">>").length > 1 ? message.split(">>")[1] : null;
        if(code.equals("RenderFinished") && content != null){
            taskCf.complete(Json.parse(content).asObject());
            currentTask = null;
            taskCf = null;
        } else if(code.equals("RenderError")) {
            taskCf.completeExceptionally(new LumaException(content));
            currentTask = null;
            taskCf = null;
        } else if(code.equals("RenderStatus")){
            requestCf.complete(content);
        } else if(code.equals("RenderCanceled")){
            cancelCf.complete(null);
        }
    }

    String getToken(){
        return token;
    }

    boolean isAvailable(){
        return currentTask == null;
    }

    void setTaskRendering(boolean status){
        if(currentTask != null){
            currentTask.setRendering(status);
        }
    }
}
