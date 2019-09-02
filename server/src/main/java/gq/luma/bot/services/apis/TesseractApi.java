package gq.luma.bot.services.apis;

import gq.luma.bot.services.Service;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.image.BufferedImage;
import java.util.HashMap;

public class TesseractApi implements Service {
    private static Tesseract tesseract;

    @Override
    public void startService() throws Exception {
        System.setProperty("java.specification.version", "1.9");
        tesseract = new Tesseract();
    }

    public static String doOcr(BufferedImage image) throws TesseractException {
        return tesseract.doOCR(image);
    }
}
