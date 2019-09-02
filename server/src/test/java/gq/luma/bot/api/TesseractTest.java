package gq.luma.bot.api;

import gq.luma.bot.services.apis.TesseractApi;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class TesseractTest {
    @Before
    public void before() throws Exception {
        new TesseractApi().startService();
    }

    @Test
    public void tesseract_test() throws Exception {
        BufferedImage image = ImageIO.read(new File("F:\\Win User Files\\Pictures\\legend.png"));
        System.out.println(image.getWidth());
        System.out.println(TesseractApi.doOcr(image));
    }
}
