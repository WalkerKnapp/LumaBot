package gq.luma.bot.api;

import com.eclipsesource.json.WriterConfig;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.systems.srcdemo.SrcDemo;
import gq.luma.bot.utils.LumaException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SrcDemoTest {
    @Before
    public void before() throws Exception {
        new FileReference().startService();
    }

    @Test
    public void srcDemo() throws IOException, LumaException {
        try(FileWriter fw = new FileWriter(new File(FileReference.tempDir, "BombFlings_2298_Msushi.json"))) {
            SrcDemo.of(new File(FileReference.tempDir, "BombFlings_2298_Msushi.dem")).serialize().writeTo(fw, WriterConfig.PRETTY_PRINT);
        }
    }
}
