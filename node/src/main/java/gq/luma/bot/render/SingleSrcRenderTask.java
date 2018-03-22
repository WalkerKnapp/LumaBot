package gq.luma.bot.render;

import gq.luma.bot.BotReference;
import gq.luma.bot.ClientSocket;
import gq.luma.bot.SrcDemo;
import gq.luma.bot.render.fs.FuseRenderFS;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.render.renderer.RendererInterface;
import gq.luma.bot.render.structure.RenderSettings;
import gq.luma.bot.utils.FileReference;
import gq.luma.bot.utils.LumaException;
import org.apache.commons.io.FilenameUtils;

import java.awt.*;
import java.io.*;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class SingleSrcRenderTask extends SrcRenderTask {

    private SrcDemo demo;
    private RenderSettings settings;

    private File demoFile;
    private File baseDir;

    private FFRenderer renderer;
    private File mountPoint;

    private String status;

    private DecimalFormat df;

    public SingleSrcRenderTask(SrcDemo demo, RenderSettings settings, String demoFileName, String baseDirName) {
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

            /*Consumer<MediaPicture> screenshotQuery = picture -> {
                if(picture.getTimeStamp() == (long)((demo.getPlaybackTime() * settings.getFps())/2)){
                    BufferedImage image = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                    final DataBufferByte db = (DataBufferByte) image.getRaster()
                            .getDataBuffer();
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
            };*/

            this.renderer = RendererInterface.createSinglePass(settings, finalFile);
            this.renderer.setIgnoreTime(settings.shouldRemoveBroken() ? demo.getFirstPlaybackTick()/60d : 0d);

            this.status = "Setting up File System";

            this.mountPoint = new File(demo.getGame().getGameDir(), "export");

            //Setting up filesystem
            ClientSocket.renderFS.getRenderFS().configure(settings, renderer);
            ClientSocket.renderFS.getRenderFS().getErrorHandler().exceptionally(this::handleError);

            if(this.mountPoint.exists()) {
                if (!this.mountPoint.delete()) {
                    System.err.println("Unable to delete mount point");
                }
            }

            Files.createSymbolicLink(mountPoint.toPath(), ClientSocket.renderFS.getMountPoint());

            parseHcontent(demo.getSignOnString()).ifPresent(hcontentString -> {
                System.out.println("Found hcontent string of: " + hcontentString);
                File[] workshopFiles = demo.getGame().getWorkshopDir().listFiles();
                if(workshopFiles != null && Stream.of(workshopFiles).noneMatch(f -> {
                    File[] subFiles = f.listFiles();
                    return subFiles != null
                            && f.getName().equalsIgnoreCase(hcontentString)
                            && Stream.of(subFiles).anyMatch(subFile -> subFile.getName().equalsIgnoreCase(demo.getMapName() + ".bsp"));
                })){
                    downloadSteamMap(demo.getGame().getWorkshopDir(), hcontentString, demo.getGame().getPublishingApp(), demo.getGame().getAppCode());
                    System.out.println("Downloaded id: " + hcontentString);
                }
            });

            this.status = "Setting up File System";
            checkResources(demo.getGame(), settings);

            this.status = "Starting game";

            //Running Game
            writeCfg().join();
            if(demo.getGame().getLog().exists() && !demo.getGame().getLog().delete()) {
                throw new LumaException("Unable to delete console log. Please contact the developer.");
            }
            System.out.println("---------------Opening game---------------------");
            Desktop.getDesktop().browse(new URI(BotReference.STEAM_SHORTCUT_HEADER + demo.getGame().getAppCode()));
            SourceLogMonitor.monitor("cl_thirdperson", demo.getGame().getLog()).join();
            Thread.sleep(3000);

            if(settings.isPretify()){
                Thread.sleep(10000);
            }

            sendCommand("exec autorecord", demo.getGame());

            if(settings.shouldReallyStartOdd(demo)) {
                System.out.println("Starting odd! Specified start odd: " + settings.specifiedStartOdd() + " and first playback tick: " + demo.getFirstPlaybackTick());
                if(!demo.getMapName().contains("mp")) {
                    SourceLogMonitor.monitor("Redownloading all lightmaps", demo.getGame().getLog()).join();
                    Thread.sleep(700);
                } else {
                    SourceLogMonitor.monitor("Demo message, tick 0", demo.getGame().getLog()).join();
                    Thread.sleep(1000);
                }
                sendCommand("demo_pauseatservertick 1;demo_resume", demo.getGame());
                Thread.sleep(3000);
                sendCommand("sv_alternateticks 1", demo.getGame());
                if(demo.getMapName().contains("mp_")){
                    sendCommand("+remote_view", demo.getGame());
                }
                Thread.sleep(3000);
                sendCommand("exec restarter", demo.getGame());
            }

            this.status = "Rendering";
            waitForDemStop(demo.getGame());
            sendCommand("endmovie", demo.getGame()).join();

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
    public void cancel() {
        cleanup();
    }

    @Override
    public String getThumbnail() {
        return "nothing";
    }

    private CompletableFuture<Void> writeCfg(){
        CompletableFuture<Void> cf = new CompletableFuture<>();
        new Thread(() -> {
            try{
                FileWriter fw = new FileWriter(new File(demo.getGame().getConfigDir(), "autorecord.cfg"));
                BufferedWriter bw = new BufferedWriter(fw);

                if(settings.isHq()) bw.write("exec render\n"); else bw.write("sv_cheats 1\n");
                if(settings.shouldReallyStartOdd(demo)) bw.write("sv_alternateticks 0\n");
                if(!settings.isInterpolate()) bw.write("demo_interpolateview 0\n");
                if(settings.isOob()) {
                    bw.write("r_novis 1\n");
                    bw.write("r_portal_use_pvs_optimization 0\n");
                }
                if(settings.isDemohack()) bw.write("portal_demohack 1\n");

                bw.write("host_framerate " + (settings.getFps() * settings.getFrameblendIndex()) + "\n");
                bw.write("mat_setvideomode " + settings.getWidth() + " " + settings.getHeight() + " 1\n");
                if(settings.shouldReallyStartOdd(demo)) bw.write("demo_pauseatservertick 1\n");
                bw.write("demo_debug 1\n");
                if(!settings.shouldReallyStartOdd(demo)) bw.write("startmovie \"export\\tga_\" raw\n");
                bw.write("playdemo \"" + demoFile.getAbsolutePath() + "\"\n");

                bw.close();
                fw.close();
                cf.complete(null);
            } catch (IOException e) {
                cf.completeExceptionally(e);
            }
        }).start();
        return cf;
    }

    @Override
    void cleanup() {
        killGame(demo.getGame());
        this.renderer.forcefullyClose();

        if(!this.mountPoint.delete()){
            System.err.println("Unable to delete mount point");
        }

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
