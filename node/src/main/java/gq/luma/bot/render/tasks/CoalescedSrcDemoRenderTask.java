package gq.luma.bot.render.tasks;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import gq.luma.bot.ClientSocket;
import gq.luma.bot.SrcDemo;
import gq.luma.bot.SrcGame;
import gq.luma.bot.render.SourceLogMonitor;
import gq.luma.bot.render.SrcRenderTask;
import gq.luma.bot.render.fs.FuseRenderFS;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.render.renderer.RendererFactory;
import gq.luma.bot.render.structure.RenderSettings;
import gq.luma.bot.utils.FileReference;
import gq.luma.bot.utils.LumaException;
import jnr.ffi.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class CoalescedSrcDemoRenderTask extends SrcRenderTask {
    private static final Logger logger = LoggerFactory.getLogger(CoalescedSrcDemoRenderTask.class);

    private SrcGame currentGame = null;

    private String status;
    private String name;

    private List<SrcDemo> srcDemos;
    private RenderSettings settings;
    private File baseDir;

    private FFRenderer renderer;
    private long predictedTotalFrames;

    public CoalescedSrcDemoRenderTask(JsonObject data) throws LumaException {
        this(data.getString("name", ""),
                new File(FileReference.tempDir, data.getString("dir", "")),
                RenderSettings.of(data.get("settings").asObject()),
                data.get("demos").asArray().values().stream().map(JsonValue::asObject).map(SrcDemo::ofUnchecked).collect(Collectors.toList()));
    }

    private CoalescedSrcDemoRenderTask(String name, File baseDir, RenderSettings settings, List<SrcDemo> demos){
        this.name = name;
        this.baseDir = baseDir;
        this.settings = settings;
        this.srcDemos = demos;

        this.predictedTotalFrames = (long) (srcDemos.stream().map(SrcDemo::getPlaybackTime).reduce((f1, f2) -> f1 + f2).orElse(1f) * settings.getFps());
    }

    @Override
    public void executeAsync() {
        try {
            this.status = "Setting up FFmpeg";

            File finalFile = new File(baseDir, name + "." + settings.getFormat().getOutputContainer());
            this.renderer = RendererFactory.createSinglePass(settings, finalFile);

            this.status = "Setting up File System";

            ClientSocket.renderFS.getRenderFS().configure(settings, renderer);
            ClientSocket.renderFS.getRenderFS().getErrorHandler().exceptionally(this::handleError);

            Thread.sleep(1000);

            for (SrcDemo demo : srcDemos) {
                File demoFile = new File(FileReference.tempDir, demo.getAssociatedFile());

                if (currentGame != demo.getGame()) {
                    killGame(currentGame);
                    writeSettings(currentGame = demo.getGame());
                    setupGame(currentGame, settings);
                    sendCommand("exec rendersettings.cfg", currentGame);
                    Thread.sleep(3000);
                }

                scanForWorkshop(currentGame, demo);

                if (demo.getPlaybackTicks() > 2) {
                    if (settings.shouldReallyStartOdd(demo)) {
                        sendCommand("demo_pauseatservertick 1;sv_alternateticks 0", currentGame);
                    } else {
                        sendCommand("exec restarter", currentGame);
                    }

                    if(settings.shouldRemoveBroken()) {
                        renderer.setIgnoreTime(demo.getFirstPlaybackTick() / 60);
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
                    logger.trace("Reading " + data.length + " bytes vs an ideal count of " + ((settings.getWidth() * settings.getHeight() * 3) + 18));
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
    protected void cleanup(){
        killGame(currentGame);
        killNow();
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
            logger.debug("Latest Frame: " + renderer.getLatestFrame());
            double d = ((double)renderer.getLatestFrame() / predictedTotalFrames) * 100d;
            logger.debug("Grabbed Percentage: " + d);
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
