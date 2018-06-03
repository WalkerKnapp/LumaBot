package gq.luma.bot;

import com.carrotsearch.junitbenchmarks.AbstractBenchmark;
import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.eclipsesource.json.Json;
import gq.luma.bot.render.SourceLogMonitor;
import gq.luma.bot.render.fs.FSInterface;
import gq.luma.bot.render.fs.FuseRenderFS;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.render.renderer.RendererFactory;
import gq.luma.bot.utils.FileReference;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class P2Benchmark extends AbstractBenchmark {
    private static final Logger logger = LoggerFactory.getLogger(P2Benchmark.class);

    private File testDir;

    private File benchDemoFile;
    private SrcDemo benchDemo;
    private RenderSettings benchSettings;

    private FuseRenderFS renderFS;
    private Path fsMountPoint;
    private FFRenderer renderer;

    public P2Benchmark() throws LumaException, IOException, URISyntaxException, InterruptedException{
        testDir = new File(FileReference.tempDir, "benchmarks");
        if(testDir.exists() && !testDir.delete()){
            throw new LumaException("Failed to remove temp dir.");
        }
        if(!testDir.mkdir()){
            throw new LumaException("Failed to make temp dir.");
        }
        benchDemoFile = new File(FileReference.tempDir, "BombFlings_2298_Msushi.dem");
        try(FileReader fr = new FileReader(new File(FileReference.tempDir, "BombFlings_2298_Msushi.json"))) {
            benchDemo = SrcDemo.of(Json.parse(fr).asObject());
        }
        try(FileReader fr = new FileReader(new File(FileReference.tempDir, "BenchSettings.json"))){
            benchSettings = RenderSettings.of(Json.parse(fr).asObject());
        }

        renderFS = (FuseRenderFS) FSInterface.openFuse(fsMountPoint = Paths.get(FileReference.tempDir.getAbsolutePath(), "mount")).join().getRenderFS();
        //renderer = new NullFFRenderer(benchSettings.getWidth(), benchSettings.getHeight());
        renderer = RendererFactory.createSinglePass(benchSettings, new File(testDir, "meme.avi"));

        renderFS.configure(benchSettings, renderer);
        renderFS.getErrorHandler().exceptionally(t -> {throw new CompletionException(t);});

        writeCfg().join();
        setupGame(benchDemo.getGame());

        sendCommand("exec autorecord");
        System.out.println("Starting odd! Specified start odd: " + benchSettings.specifiedStartOdd() + " and first playback tick: " + benchDemo.getFirstPlaybackTick());
        new SourceLogMonitor(benchDemo.getGame().getLog(), "Demo message, tick 0").monitor().join();
        Thread.sleep(1000);
        sendCommand("demo_pauseatservertick 1;demo_resume");
        Thread.sleep(3000);
        sendCommand("sv_alternateticks 1");
        Thread.sleep(3000);
    }

    @BenchmarkOptions(benchmarkRounds = 1, warmupRounds = 0)
    @Test
    public void aparapiImmediateBenchmark() throws InterruptedException {
        long startTime = System.nanoTime();
        sendCommand("exec restarter");
        new SourceLogMonitor(benchDemo.getGame().getLog(), "dem_stop").monitor().join();
        long endTime = System.nanoTime();
        System.out.println("Spent: " + (endTime - startTime)/1_000_000_000d + " seconds");
    }

    @After
    public void after(){
        ProcessHandle
                .allProcesses()
                .filter(processHandle -> processHandle.info().command().map(command -> command.contains(benchDemo.getGame().getExeName())).orElse(false))
                .findFirst()
                .ifPresent(ProcessHandle::destroyForcibly);
    }

    private CompletableFuture<Void> writeCfg(){
        return CompletableFuture.runAsync(() -> {
            try{
                FileWriter fw = new FileWriter(new File(benchDemo.getGame().getConfigDir(), "autorecord.cfg"));
                BufferedWriter bw = new BufferedWriter(fw);

                if(benchSettings.isHq()) bw.write("exec render\n"); else bw.write("sv_cheats 1\n");
                if(benchSettings.shouldReallyStartOdd(benchDemo)) bw.write("sv_alternateticks 0\n");
                if(!benchSettings.isInterpolate()) bw.write("demo_interpolateview 0\n");
                if(benchSettings.isOob()) {
                    bw.write("r_novis 1\n");
                    bw.write("r_portal_use_pvs_optimization 0\n");
                }
                if(benchSettings.isDemohack()) bw.write("portal_demohack 1\n");

                if(benchDemo.getMapName().contains("mp_")){
                    bw.write("cl_enable_remote_splitscreen 1\n");
                }

                bw.write("host_framerate " + (benchSettings.getFps() * benchSettings.getFrameblendIndex()) + "\n");
                bw.write("mat_setvideomode " + benchSettings.getWidth() + " " + benchSettings.getHeight() + " 1\n");
                if(benchSettings.shouldReallyStartOdd(benchDemo)) bw.write("demo_pauseatservertick 1\n");
                bw.write("demo_debug 1\n");
                if(!benchSettings.shouldReallyStartOdd(benchDemo)) bw.write("startmovie \"export\\tga_\" raw\n");
                bw.write("playdemo \"" + benchDemoFile.getAbsolutePath() + "\"\n");

                bw.close();
                fw.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void setupGame(SrcGame game) throws IOException, LumaException, URISyntaxException, InterruptedException {
        if (game.getLog().exists() && !game.getLog().delete()) {
            throw new LumaException("Unable to delete console log. Please contact the developer.");
        }

        File mountPoint = new File(game.getGameDir(), "export");

        if (mountPoint.exists() && !mountPoint.delete()) {
            throw new LumaException("Failed to remove the frame export directory.");
        }

        Files.createSymbolicLink(mountPoint.toPath(), fsMountPoint);

        System.out.println("---------------Opening game---------------------");
        Desktop.getDesktop().browse(new URI("steam://rungameid/" + game.getAppCode()));
        new SourceLogMonitor(game.getLog(), 50, "cl_thirdperson").monitor().join();

        Thread.sleep(3000);
    }

    protected CompletableFuture<Void> sendCommand(String command){
        return CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(benchDemo.getGame().getExe(), "-hijack", "-console", "+" + command);
                logger.debug("---------------Injecting {}---------------", command);
                pb.start().waitFor();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
