package gq.luma.bot.render;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import gq.luma.bot.ClientSocket;
import gq.luma.bot.SrcGame;
import gq.luma.bot.render.structure.RenderSettings;
import gq.luma.bot.utils.FileReference;
import gq.luma.bot.utils.LumaException;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class SrcRenderTask implements Task {
    private static final Logger logger = LoggerFactory.getLogger(SrcRenderTask.class);
    private static final Pattern ID_PATTERN = Pattern.compile("(?<=workshop\\\\|/)(?<id>\\d*)(?=[\\\\/])");
    private static HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
            .scheme("https")
            .host("api.steampowered.com")
            .addPathSegment("ISteamRemoteStorage")
            .addPathSegment("GetUGCFileDetails")
            .addPathSegment("v1");

    static final String STEAM_SHORTCUT_HEADER = "steam://rungameid/";

    private transient Future future;
    transient CompletableFuture<File> cf;
    private boolean rendering;

    @Override
    public CompletableFuture<File> execute() {
        this.cf = new CompletableFuture<>();
        this.future = ClientSocket.executorService.submit(this::executeAsync);
        return cf;
    }

    void killNow(){
        this.future.cancel(true);
    }

    public abstract void executeAsync();

    CompletableFuture<Void> sendCommand(String command, SrcGame game){
        CompletableFuture<Void> cf = new CompletableFuture<>();
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(game.getExe(), "-hijack", "-console", "+" + command);
                logger.debug("---------------Injecting {}---------------", command);
                pb.start().waitFor();
                cf.complete(null);
            } catch (IOException | InterruptedException e) {
                cf.completeExceptionally(e);
            }
        }).start();
        return cf;
    }

    void waitForDemStop(SrcGame game){
        long renderingStartTime = System.currentTimeMillis();

        CompletableFuture<?>[] demoWatcherCfs = {SourceLogMonitor.monitor("dem_stop", game.getLog()), SourceLogMonitor.monitor("leaderboard_open", game.getLog(), 1000)};
        CompletableFuture.anyOf(demoWatcherCfs).join();
        Stream.of(demoWatcherCfs).forEach(demoWatcherCf -> demoWatcherCf.cancel(true));

        long renderingStopTime = System.currentTimeMillis();
        System.out.println("Time Spent rendering: " + (renderingStopTime - renderingStartTime)/1000);
    }

    void killGame(SrcGame game){
        if(game == SrcGame.NONE)
            return;
        ProcessHandle
                .allProcesses()
                .filter(processHandle -> processHandle.info().command().map(command -> command.contains(game.getExeName())).orElse(false))
                .findFirst()
                .ifPresent(ProcessHandle::destroyForcibly);
    }

    void checkResources(SrcGame game, RenderSettings settings) throws IOException {
        File resourcesFolder = new File(FileReference.resDir.getAbsolutePath(), game.getDirectoryName());
        File binDir = new File(game.getGameDir().getParentFile(), "bin");
        logger.debug("Resources: " + resourcesFolder.getAbsolutePath());

        for(File resource : Objects.requireNonNull(resourcesFolder.listFiles())){
            logger.trace("Checking resource: " + resource.getName());
            Path toCheck = Paths.get(binDir.getAbsolutePath(), resource.getName());
            boolean anyMatchBin = Files.exists(toCheck);

            logger.trace("ToCheck: " + toCheck.toString());
            logger.trace("Any match?: " + anyMatchBin);
            logger.trace("Is pretify?: " + settings.isPretify());

            if(settings.isPretify() && !anyMatchBin){
                Files.copy(resource.toPath(), toCheck);
            } else if(!settings.isPretify() && anyMatchBin){
                Files.delete(toCheck);
            }
        }
    }

    static Optional<File> downloadSteamMap(File workshopDir, String ugcID, int deployApp, int deployAppAlt){
        try {
            System.out.println("Using key: " + ClientSocket.steamKey);

            String result = performRequest(urlBuilder.setQueryParameter("appid", String.valueOf(deployApp))
                    .setQueryParameter("key", ClientSocket.steamKey)
                    .setQueryParameter("ugcid", ugcID).build());

            System.out.println("String: " + result);

            JsonObject node = Json.parse(result).asObject();
            if(node.get("data") != null && !node.get("data").asObject().isEmpty()){
                node = node.get("data").asObject();
            } else {
                String secondaryResult =  performRequest(urlBuilder.setQueryParameter("appid", String.valueOf(deployAppAlt))
                        .setQueryParameter("key", ClientSocket.steamKey)
                        .setQueryParameter("ugcid", ugcID).build());
                node = Json.parse(secondaryResult).asObject();
                System.out.println("String: " + node.toString());
                if(node.get("data") != null){
                    node = node.get("data").asObject();
                } else {
                    return Optional.empty();
                }
            }

            System.out.println("Got response " + node.toString());

            File contentDir = new File(workshopDir, ugcID);
            if(!contentDir.exists() && !contentDir.mkdir()){
                throw new LumaException("Unable to make directory for downloading additional maps.");
            }

            File finalFile = new File(contentDir, lastOf(node.getString("filename", "").split("/")));

            try(InputStream inputStream = new URL(node.getString("url", "")).openStream();
                ReadableByteChannel rbc = Channels.newChannel(inputStream);
                FileOutputStream fos = new FileOutputStream(finalFile)){
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }

            System.out.println("Downloaded to " + finalFile.getAbsolutePath());

            return Optional.of(finalFile);

        } catch (IOException | LumaException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private static String performRequest(HttpUrl url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        System.out.println(request.toString());
        return ClientSocket.okhttpClient.newCall(request)
                .execute()
                .body()
                .string();
    }

    Optional<String> parseHcontent(String signOnString){
        Matcher m = ID_PATTERN.matcher(signOnString);
        while(m.find()){
            if(m.group("id") != null) return Optional.of(m.group("id"));
        }
        return Optional.empty();
    }

    Void handleError(Throwable t){
        t.printStackTrace();
        cf.completeExceptionally(t);
        try {
            cleanup();
        } catch (IOException e) {
            e.printStackTrace();
        }
        killNow();
        return null;
    }

    abstract void cleanup() throws IOException;

    private static String lastOf(String[] array){
        return array[array.length - 1];
    }
}
