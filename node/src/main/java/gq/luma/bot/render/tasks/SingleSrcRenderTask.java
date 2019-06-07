package gq.luma.bot.render.tasks;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.ClientSocket;
import gq.luma.bot.SrcDemo;
import gq.luma.bot.render.SourceLogMonitor;
import gq.luma.bot.render.SrcRenderTask;
import gq.luma.bot.render.fs.FuseRenderFS;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.render.renderer.RendererFactory;
import gq.luma.bot.render.renderer.SinglePassFFRenderer;
import gq.luma.bot.RenderSettings;
import gq.luma.bot.VideoOutputFormat;
import gq.luma.bot.utils.FileReference;
import gq.luma.bot.LumaException;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.concurrent.CompletableFuture;

public class SingleSrcRenderTask extends SrcRenderTask {

    private SrcDemo demo;
    private RenderSettings settings;

    private File demoFile;
    private File baseDir;

    private FFRenderer renderer;

    private DecimalFormat df;

    public SingleSrcRenderTask(JsonObject data) throws LumaException {
        this(SrcDemo.of(data.get("demo").asObject()),
                RenderSettings.of(data.get("settings").asObject()),
                data.getString("name", ""),
                data.getString("dir", ""));
    }

    private SingleSrcRenderTask(SrcDemo demo, RenderSettings settings, String demoFileName, String baseDirName) {
        this.demo = demo;
        this.settings = settings;

        this.df = new DecimalFormat("#.#");
        df.setRoundingMode(RoundingMode.CEILING);

        this.baseDir = new File(FileReference.tempDir, baseDirName);
        this.demoFile = new File(baseDir, demoFileName);

        if(!baseDir.mkdir()){
            System.out.println("Dir already exists.");
        }

        this.status = "Waiting";
    }

    @Override
    public void executeAsync() {
        try {
            this.status = "Setting up FFmpeg";

            File finalFile = new File(baseDir, FilenameUtils.removeExtension(demoFile.getName()) + "." + settings.getFormat().getOutputContainer());

            if(settings.isTwoPass() && settings.getFormat() == VideoOutputFormat.H264) {
                this.renderer = RendererFactory.createTwoPass(settings, finalFile);
                //this.renderer = RendererFactory.createCppTwoPass(settings, finalFile, demo);
            } else {
                this.renderer = RendererFactory.createSinglePass(settings, finalFile);
                //this.renderer = RendererFactory.createCppOnePass(settings, finalFile, demo);
            }
            this.renderer.setSampleOffset(4410);
            this.renderer.setIgnoreTime(settings.shouldRemoveBroken() ? demo.getFirstPlaybackTick()/60d : 0d);
            ClientSocket.renderFS.getRenderFS().configure(settings, renderer);
            ClientSocket.renderFS.getRenderFS().getErrorHandler().exceptionally(this::handleError);

            scanForWorkshop(demo.getGame(), demo);

            writeCfg().join();
            setupGame(demo.getGame(), settings);

            sendCommand(demo.getGame(), "exec autorecord");

            if(settings.shouldReallyStartOdd(demo) && !demo.getMapName().contains("mp_")) {
                System.out.println("Starting odd! Specified start odd: " + settings.specifiedStartOdd() + " and first playback tick: " + demo.getFirstPlaybackTick());
                if(!demo.getMapName().contains("mp_")) {
                    new SourceLogMonitor(demo.getGame().getLog(), "Redownloading all lightmaps").monitor().join();
                    Thread.sleep(700);
                } else {
                    new SourceLogMonitor(demo.getGame().getLog(), "Demo message, tick 0").monitor().join();
                    Thread.sleep(1000);
                }
                sendCommand(demo.getGame(), "demo_pauseatservertick 1;demo_resume");
                Thread.sleep(3000);
                sendCommand(demo.getGame(), "sv_alternateticks 1");
                Thread.sleep(3000);
                sendCommand(demo.getGame(), "exec restarter");
            }

            this.status = "Rendering";
            waitForDemStop(demo.getGame());
            sendCommand(demo.getGame(), "endmovie").join();

            Thread.sleep(1000);

            killGame(demo.getGame());
            ClientSocket.renderFS.getRenderFS().waitToFinish();
            ((FuseRenderFS)ClientSocket.renderFS.getRenderFS()).resetStatus();

            //Cleaning up
            cleanup();
            cf.complete(finalFile);
        }
        catch (Exception e) {
            this.handleError(e);
        }
    }

    @Override
    public String getType() {
        return "Source Demo Render";
    }

    @Override
    public String getStatus() {
        String ret = this.status;
        if(ret.equals("Rendering")){
            System.out.println("Latest Frame: " + this.renderer.getLatestFrame());
            System.out.println("Playback time: " + demo.getPlaybackTime());
            double d = (this.renderer.getLatestFrame() / ((demo.getPlaybackTicks() / 60d) * settings.getFps())) * 100d;
            System.out.println("Grabbed Percentage: " + d);
            ret += ": " + df.format(d) + "% complete.";
        }
        return ret;
    }

    @Override
    public void cancel() throws LumaException {
        cleanup();
    }

    @Override
    public String getThumbnail() {
        return "nothing";
    }

    private CompletableFuture<Void> writeCfg(){
        return CompletableFuture.runAsync(() -> {
            try{
                FileWriter fw = new FileWriter(new File(demo.getGame().getConfigDir(), "autorecord.cfg"));
                BufferedWriter bw = new BufferedWriter(fw);

                if(settings.isHq()) bw.write("exec render\n"); else bw.write("sv_cheats 1\n");
                if(settings.shouldReallyStartOdd(demo) && !demo.getMapName().contains("mp_")) bw.write("sv_alternateticks 0\n");
                if(!settings.isInterpolate()) bw.write("demo_interpolateview 0\n");
                if(settings.isOob()) {
                    bw.write("r_novis 1\n");
                    bw.write("r_portal_use_pvs_optimization 0\n");
                } else {
                    bw.write("r_novis 1\n");
                    bw.write("r_portal_use_pvs_optimization 0\n");
                }
                if(settings.isDemohack()) bw.write("portal_demohack 1\n");

                if(demo.getMapName().contains("mp_")){
                    //l_enable_remote_splitscreen 1
                    //ss_pip_bottom_offset 0
                    //ss_pip_right_offset 0
                    //ss_pipscale 1f
                    //bw.write("l_enable_remote_splitscreen 1\n");
                    //bw.write("ss_pip_bottom_offset 0\n");
                    //bw.write("ss_pip_right_offset 0\n");
                    //bw.write("ss_pipscale 1f\n");
                    bw.write("cl_enable_remote_splitscreen 1\n");
                }

                bw.write("host_framerate " + (settings.getFps() * settings.getFrameblendIndex()) + "\n");
                bw.write("mat_setvideomode " + settings.getWidth() + " " + settings.getHeight() + " 1\n");
                if(settings.shouldReallyStartOdd(demo) && !demo.getMapName().contains("mp_")) bw.write("demo_pauseatservertick 1\n");
                bw.write("demo_debug 1\n");
                if(!(settings.shouldReallyStartOdd(demo) && !demo.getMapName().contains("mp_"))) bw.write("startmovie \"export\\tga_\" raw\n");
                bw.write("playdemo \"" + demoFile.getAbsolutePath() + "\"\n");
                bw.write("hud_reloadscheme\n");

                bw.close();
                fw.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, ClientSocket.executorService);
    }

    @Override
    protected void cleanup() throws LumaException {
        killGame(demo.getGame());
        this.renderer.forcefullyClose();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        this.status = "Uploading files.";
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof SingleSrcRenderTask && ((SingleSrcRenderTask) object).baseDir.getName().equalsIgnoreCase(this.baseDir.getName()) && ((SingleSrcRenderTask) object).demoFile.getName().equalsIgnoreCase(this.demoFile.getName());
    }
}
