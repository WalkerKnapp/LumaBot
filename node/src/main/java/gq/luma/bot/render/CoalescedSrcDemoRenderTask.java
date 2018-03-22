package gq.luma.bot.render;

import gq.luma.bot.ClientSocket;
import gq.luma.bot.SrcDemo;
import gq.luma.bot.SrcGame;
import gq.luma.bot.render.fs.FuseRenderFS;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.render.renderer.RendererInterface;
import gq.luma.bot.render.structure.RenderSettings;
import gq.luma.bot.utils.FileReference;
import gq.luma.bot.utils.LumaException;
import jnr.ffi.Pointer;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class CoalescedSrcDemoRenderTask extends SrcRenderTask {

    private SrcGame currentGame = SrcGame.NONE;

    private String status;
    private String name;
    private long requester;

    private List<SrcDemo> srcDemos;
    private RenderSettings settings;
    private File baseDir;

    private FFRenderer renderer;
    private long predictedTotalFrames;

    public CoalescedSrcDemoRenderTask(String name, long requester, File baseDir, RenderSettings settings, List<SrcDemo> demos){
        this.name = name;
        this.requester = requester;
        this.baseDir = baseDir;
        this.settings = settings;
        this.srcDemos = demos;

        this.predictedTotalFrames = (long) (srcDemos.stream().map(SrcDemo::getPlaybackTime).reduce((f1, f2) -> f1 + f2).orElse(1f) * settings.getFps());
    }

    /*
    , picture -> {
                if(picture.getTimeStamp() == predictedTotalFrames/2){
                    BufferedImage image = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                    final DataBufferByte db = (DataBufferByte) image.getRaster().getDataBuffer();
                    final byte[] bytes = db.getData();
                    System.out.println("Image size: " + bytes.length);
                    System.out.println("Picture size: " + picture.getDataPlaneSize(0));
                    picture.getData(0).getByteBuffer(0, picture.getDataPlaneSize(0)).get(bytes, 0, bytes.length);
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        System.out.println("Saving image at timestamp " + picture.getTimeStamp());
                        ImageIO.write(image, "png", baos);
                        this.thumbnailId = ClientSocket.uploader.uploadInputStream(new ByteArrayInputStream(baos.toByteArray()), "thumbnail.png", "image/png", baos.size());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
     */

    @Override
    public void executeAsync() {
        try {
            this.status = "Setting up FFmpeg";

            File finalFile = new File(baseDir, name + "." + settings.getFormat().getOutputContainer());
            this.renderer = RendererInterface.createSinglePass(settings, finalFile);

            this.status = "Setting up File System";

            ClientSocket.renderFS.getRenderFS().configure(settings, renderer);
            ClientSocket.renderFS.getRenderFS().getErrorHandler().exceptionally(this::handleError);

            Thread.sleep(1000);

            for (SrcDemo demo : srcDemos) {
                File demoFile = new File(FileReference.tempDir, demo.getAssociatedFile());

                if (currentGame != demo.getGame()) {
                    killGame(currentGame);
                    setupGame(currentGame = demo.getGame());
                    Thread.sleep(3000);
                }

                parseHcontent(demo.getSignOnString()).ifPresent(hcontentString -> {
                    if (Stream.of(Objects.requireNonNull(currentGame.getWorkshopDir().listFiles())).noneMatch(f -> f.getName().equalsIgnoreCase(hcontentString))) {
                        downloadSteamMap(currentGame.getWorkshopDir(), hcontentString, currentGame.getPublishingApp(), currentGame.getAppCode());
                        System.out.println("Downloaded id: " + hcontentString);
                    }
                });

                if (demo.getPlaybackTicks() > 2) {
                    if (settings.shouldReallyStartOdd(demo)) {
                        sendCommand("demo_pauseatservertick 1;sv_alternateticks 0", currentGame);
                    } else {
                        sendCommand("exec restarter", currentGame);
                    }

                    Thread.sleep(1000);

                    writePlayDemo(demoFile.getAbsolutePath(), currentGame);
                    sendCommand("exec autoplay", currentGame);

                    if (settings.shouldReallyStartOdd(demo)) {
                        if (!demo.getMapName().contains("mp_")) {
                            SourceLogMonitor.monitor("Redownloading all lightmaps", demo.getGame().getLog()).join();
                            Thread.sleep(700);
                        } else {
                            SourceLogMonitor.monitor("Demo message, tick 0", demo.getGame().getLog()).join();
                            Thread.sleep(1000);
                        }
                        sendCommand("demo_pauseatservertick 1;demo_resume", currentGame);
                        Thread.sleep(3000);
                        sendCommand("sv_alternateticks 1", currentGame);
                        Thread.sleep(500);
                        sendCommand("exec restarter", currentGame);
                    }

                    this.status = "Rendering";
                    waitForDemStop(currentGame);
                    sendCommand("endmovie", currentGame);

                } else {
                    sendCommand("demo_pauseatservertick 1", currentGame);
                    sendCommand("cl_screenshotname export/tga0000_", currentGame);
                    Thread.sleep(3000);
                    writePlayDemo(demoFile.getAbsolutePath(), currentGame);
                    sendCommand("exec autoplay", currentGame);

                    if (!demo.getMapName().contains("mp_")) {
                        SourceLogMonitor.monitor("Redownloading all lightmaps", demo.getGame().getLog()).join();
                        Thread.sleep(2000);
                    } else {
                        SourceLogMonitor.monitor("Demo message, tick 0", demo.getGame().getLog()).join();
                        Thread.sleep(2000);
                    }

                    sendCommand("screenshot", currentGame);

                    Thread.sleep(3000);

                    byte[] data = Files.readAllBytes(new File(currentGame.getGameDir(), "screenshots/export/tga0000_.tga").toPath());
                    System.out.println("Reading " + data.length + " bytes vs an ideal count of " + ((settings.getWidth() * settings.getHeight() * 3) + 18));
                    FuseRenderFS fs = ((FuseRenderFS)ClientSocket.renderFS.getRenderFS());
                    fs.write("/tga000_.tga", Pointer.wrap(fs.getRuntime(), ByteBuffer.wrap(data)), data.length, 0, null);

                    Thread.sleep(1000);

                    sendCommand("stopdemo", currentGame);
                }

                renderer.setFrameOffset(renderer.getLatestFrame() + 1);
                ((FuseRenderFS)ClientSocket.renderFS.getRenderFS()).resetStatus();

                Thread.sleep(5000);
            }

            this.renderer.finish();
            this.renderer.forcefullyClose();
            killGame(currentGame);
            cf.complete(finalFile);
            cleanup();

        } catch (Exception e){
            cf.completeExceptionally(e);
            cleanup();
        }
    }

    @Override
    void cleanup(){
        killGame(currentGame);
        killNow();
    }

    private void setupGame(SrcGame game) throws LumaException, IOException, URISyntaxException, InterruptedException {
        this.status = "Setting up File System.";

        checkResources(game, settings);

        if (game.getLog().exists() && !game.getLog().delete()) {
            throw new LumaException("Unable to delete console log. Please contact the developer.");
        }

        File mountPoint = new File(game.getGameDir(), "export");

        if (mountPoint.exists() && !mountPoint.delete()) {
            throw new LumaException("Failed to remove the frame export directory.");
        }

        Files.createSymbolicLink(mountPoint.toPath(), ClientSocket.renderFS.getMountPoint());

        writeSettings(game);

        this.status = "Starting game";

        System.out.println("---------------Opening game---------------------");
        Desktop.getDesktop().browse(new URI(SrcRenderTask.STEAM_SHORTCUT_HEADER + game.getAppCode()));
        SourceLogMonitor.monitor("cl_thirdperson", game.getLog()).join();

        Thread.sleep(3000);

        sendCommand("exec rendersettings.cfg", game);
    }

    private void writeSettings(SrcGame game) throws IOException {
        try(FileWriter fw = new FileWriter(new File(game.getConfigDir(), "rendersettings.cfg"));
            BufferedWriter bw = new BufferedWriter(fw)){
            if(settings.isHq()) bw.write("exec render\n"); else bw.write("sv_cheats 1\n");
            if(!settings.isInterpolate()) bw.write("demo_interpolateview 0\n");
            if(settings.isOob()) bw.write("r_novis 1\n");
            bw.write("cl_playback_screenshots 1\n");
            bw.write("host_framerate " + (settings.getFps() * settings.getFrameblendIndex()) + "\n");
            bw.write("mat_setvideomode " + settings.getWidth() + " " + settings.getHeight() + " 1\n");
            bw.write("demo_debug 1\n");
        }
    }

    private void writePlayDemo(String demoPath, SrcGame game) throws IOException {
        try(FileWriter fw = new FileWriter(new File(game.getConfigDir(), "autoplay.cfg"));
            BufferedWriter bw = new BufferedWriter(fw)){
            bw.write("playdemo \"" + demoPath + "\"\n");
        }
    }

    @Override
    public String getType() {
        return "Coalesced Source Demo Render";
    }

    @Override
    public String getStatus() {
        String ret = this.status;
        if(ret.equals("Rendering")){
            System.out.println("Latest Frame: " + renderer.getLatestFrame());
            double d = (renderer.getLatestFrame() / predictedTotalFrames) * 100d;
            System.out.println("Grabbed Percentage: " + d);
            //ret += ": " + df.format(d) + "% complete.";
        }
        return ret;
    }

    @Override
    public void cancel() {
        killGame(currentGame);
        killNow();
    }

    @Override
    public String getThumbnail() {
        return "nothing";
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof CoalescedSrcDemoRenderTask && ((CoalescedSrcDemoRenderTask) object).baseDir.getName().equalsIgnoreCase(this.baseDir.getName());
    }
}
