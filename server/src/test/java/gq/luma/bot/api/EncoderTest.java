package gq.luma.bot.api;

import gq.luma.bot.utils.WordEncoder;
import org.junit.Before;
import org.junit.Test;

public class EncoderTest {
    @Before
    public void before() throws Exception {
        new WordEncoder().startService();
    }

    @Test
    public void encoderTest(){
        System.out.println(WordEncoder.encode(9061007));
    }
}
