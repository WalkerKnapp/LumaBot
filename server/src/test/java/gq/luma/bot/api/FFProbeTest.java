package gq.luma.bot.api;

import gq.luma.bot.systems.ffprobe.FFProbe;
import gq.luma.bot.systems.ffprobe.FFProbeResult;
import gq.luma.bot.reference.FileReference;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;

public class FFProbeTest {
    @Before
    public void before() throws Exception {
        new FileReference().startService();
    }

    @Test
    public void ffprobe_analyze() throws IOException {
        try(FileInputStream fis = new FileInputStream("H:\\Portal 2\\Rendering\\PitFlings_1575_Zypehmux.mp4")) {
            FFProbeResult result = FFProbe.analyzeByStream(fis).join();
        }
    }
}
