package gq.luma.bot.render;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import gq.luma.bot.ClientSocket;
import gq.luma.bot.SrcDemo;
import gq.luma.bot.SrcGame;
import gq.luma.bot.RenderSettings;
import gq.luma.bot.utils.FileReference;
import gq.luma.bot.LumaException;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class SrcRenderTask implements Task {
    private static final String STEAM_SHORTCUT_HEADER = "steam://rungameid/";
    private static final Logger logger = LoggerFactory.getLogger(SrcRenderTask.class);
    private static final Pattern ID_PATTERN = Pattern.compile("(?<=workshop\\\\|/)(?<id>\\d*)(?=[\\\\/])");
    private static HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
            .scheme("https")
            .host("api.steampowered.com")
            .addPathSegment("ISteamRemoteStorage")
            .addPathSegment("GetUGCFileDetails")
            .addPathSegment("v1");

    private transient Future future;
    protected transient CompletableFuture<File> cf;

    protected String status;

    @Override
    public CompletableFuture<File> execute() {
        this.cf = new CompletableFuture<>();
        this.future = ClientSocket.executorService.submit(this::executeAsync);
        return cf;
    }

    protected void killNow(){
        this.future.cancel(true);
    }

    public abstract void executeAsync();

    protected CompletableFuture<Void> sendCommand(SrcGame game, String... command) throws IOException {
        String[] args = new String[3 + command.length];
        args[0] = game.getExe();
        args[1] = "-hijack";
        args[2] = "-console";
        for(int i = 3; i < args.length; i++){
            args[i] = "+" + command[i - 3];
        }

        ProcessBuilder pb = new ProcessBuilder(args);
        System.out.println("Sending command: " + Arrays.toString(args));
        logger.debug("---------------Injecting {}---------------", Arrays.toString(command));
        return pb.start().onExit().thenApply(p -> null);
    }

    protected void waitForDemStop(SrcGame game) throws InterruptedException {
        long renderingStartTime = System.currentTimeMillis();

        new SourceLogMonitor(game.getLog(), 15, "dem_stop", "leaderboard_open").monitor().join();

        long renderingStopTime = System.currentTimeMillis();
        System.out.println("Time Spent rendering: " + (renderingStopTime - renderingStartTime)/1000);
    }

    protected void killGame(SrcGame game){
        if(game == null)
            return;
        ProcessHandle
                .allProcesses()
                .filter(processHandle -> processHandle.info().command().map(command -> command.contains(game.getExeName())).orElse(false))
                .findFirst()
                .ifPresent(ProcessHandle::destroyForcibly);
    }

    private void checkResources(SrcGame game, RenderSettings settings) throws IOException {
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

    protected void setupGame(SrcGame game, RenderSettings settings) throws LumaException, IOException, URISyntaxException, InterruptedException {
        this.status = "Setting up File System.";

        checkResources(game, settings);

        try{
            Files.deleteIfExists(game.getLog().toPath());
        } catch (Exception e){
            e.printStackTrace();
            throw new LumaException("Failed to remove the console.log");
        }

        File mountPoint = new File(game.getGameDir(), "export");

        if (mountPoint.exists() && !mountPoint.delete()) {
            throw new LumaException("Failed to remove the frame export directory.");
        }

        Files.createSymbolicLink(mountPoint.toPath(), ClientSocket.renderFS.getMountPoint());

        this.status = "Starting game";

        System.out.println("---------------Opening game---------------------");
        Desktop.getDesktop().browse(new URI(SrcRenderTask.STEAM_SHORTCUT_HEADER + game.getAppCode()));
        new SourceLogMonitor(game.getLog(), 50, "cl_thirdperson").monitor().join();

        Thread.sleep(3000);

        if(settings.isPretify()){
            Thread.sleep(10000);
        }
    }

    protected void scanForWorkshop(SrcGame game, SrcDemo demo) throws LumaException {
        AtomicBoolean errorEncountered = new AtomicBoolean(false);
        parseHcontent(demo.getSignOnString()).ifPresent(hcontentString -> {
            if (Stream.of(Objects.requireNonNull(game.getWorkshopDir().listFiles())).noneMatch(f -> {
                File[] subFiles = f.listFiles();
                return subFiles != null
                        && f.getName().equalsIgnoreCase(hcontentString)
                        && Stream.of(subFiles).anyMatch(subFile -> subFile.getName().equalsIgnoreCase(demo.getMapName() + ".bsp"));
            })) {
                errorEncountered.set(!downloadSteamMap(game.getWorkshopDir(), hcontentString, game.getPublishingApp(), game.getAppCode()).isPresent());
                System.out.println("Downloaded id: " + hcontentString);
            }
        });
        if(errorEncountered.get()){
            throw new LumaException("Failed to download workshop map specified in demo: " + demo.getAssociatedFile());
        }
    }

    private static Optional<File> downloadSteamMap(File workshopDir, String ugcID, int deployApp, int deployAppAlt){
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
        return Objects.requireNonNull(ClientSocket.okhttpClient.newCall(request)
                .execute()
                .body())
                .string();
    }

    private Optional<String> parseHcontent(String signOnString){
        Matcher m = ID_PATTERN.matcher(signOnString);
        while(m.find()){
            if(m.group("id") != null) return Optional.of(m.group("id"));
        }
        return Optional.empty();
    }

    protected Void handleError(Throwable t){
        t.printStackTrace();
        cf.completeExceptionally(t);
        try {
            cleanup();
        } catch (IOException | LumaException e) {
            e.printStackTrace();
        }
        killNow();
        return null;
    }

    protected abstract void cleanup() throws IOException, LumaException;

    private static String lastOf(String[] array){
        return array[array.length - 1];
    }
}
