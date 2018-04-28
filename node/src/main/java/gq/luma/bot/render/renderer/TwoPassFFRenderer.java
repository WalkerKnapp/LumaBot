package gq.luma.bot.render.renderer;

import gq.luma.bot.render.structure.RenderSettings;
import gq.luma.bot.utils.FileReference;
import io.humble.video.Encoder;
import io.humble.video.Muxer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TwoPassFFRenderer extends SinglePassFFRenderer {
    private static final Logger logger = LoggerFactory.getLogger(TwoPassFFRenderer.class);

    private File tempHuffyFile;
    private File exportFile;
    private RenderSettings finalExportSettings;

    public TwoPassFFRenderer(Muxer tempHuffyMuxer, Encoder tempHuffyVideoEncoder, Encoder tempHuffyAudioEncoder, File tempHuffyFile, File exportFile, RenderSettings settings){
        super(tempHuffyMuxer, tempHuffyVideoEncoder, tempHuffyAudioEncoder);
        this.tempHuffyFile = tempHuffyFile;
        this.exportFile = exportFile;
        this.finalExportSettings = settings;
    }

    @Override
    public void finish() throws IOException, InterruptedException {
        super.finish();
        logger.debug("Starting 1st encoding pass.");
        ProcessBuilder firstPassPB = new ProcessBuilder(FileReference.ffmpeg.getAbsolutePath(),
                "-report",
                "-y",
                "-i", tempHuffyFile.getAbsolutePath(),
                "-c:v", "libx264",
                "-b:v", finalExportSettings.getKBBBitrate() + "k",
                "-pass", "1",
                "-c:a", "aac",
                "-b:a", "128k",
                "-max_muxing_queue_size", "700",
                "-f", "mp4",
                "NUL").directory(tempHuffyFile.getParentFile())
                //.redirectOutput(new File(tempHuffyFile.getParent(), "1stpassout.txt"))
                .redirectError(new File(tempHuffyFile.getParent(), "1stpasserror.txt"));
        Process firstPassProcess = firstPassPB.start();
        firstPassProcess.waitFor();
        logger.debug("Starting 2nd encoding pass.");
        ProcessBuilder secondPassPB = new ProcessBuilder(FileReference.ffmpeg.getAbsolutePath(),
                "-report",
                "-i", tempHuffyFile.getAbsolutePath(),
                "-c:v", "libx264",
                "-b:v", finalExportSettings.getKBBBitrate() + "k",
                "-pass", "2",
                "-c:a", "aac",
                "-b:a", "128k",
                "-max_muxing_queue_size", "700",
                exportFile.getAbsolutePath()).directory(tempHuffyFile.getParentFile())
                //.redirectOutput(new File(tempHuffyFile.getParent(), "2ndpassout.txt"))
                .redirectError(new File(tempHuffyFile.getParent(), "2ndpasserror.txt"));;
        Process secondPassProcess = secondPassPB.start();
        secondPassProcess.waitFor();
        logger.debug("Finished final task!");

        Files.delete(tempHuffyFile.toPath());

    }
}
