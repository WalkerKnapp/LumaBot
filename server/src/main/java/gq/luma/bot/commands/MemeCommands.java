package gq.luma.bot.commands;

import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.LumaException;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;

public class MemeCommands {
    @Command(aliases = {"deepfry"}, description = "deepfry_description", usage = "", parent = "meme")
    public EmbedBuilder onDeepFry(CommandEvent event){
        int brightness = 150;
        int sharpness = 150;
        int contrast = 150;
        int saturation = 150;
        int noise = 150;

        Map<String, String> params = ParamUtilities.getParams(event.getCommandRemainder());

        if(params.containsKey("brightness")){
            brightness = Integer.valueOf(params.get("brightness"));
        }
        if(params.containsKey("sharpness")){
            sharpness = Integer.valueOf(params.get("sharpness"));
        }
        if(params.containsKey("contrast")){
            contrast = Integer.valueOf(params.get("contrast"));
        }
        if(params.containsKey("saturation")){
            saturation = Integer.valueOf(params.get("saturation"));
        }
        if(params.containsKey("noise")){
            noise = Integer.valueOf(params.get("noise"));
        }

        BufferedImage input;
        try {
            BufferedImage read = ImageIO.read(ParamUtilities.getFirstInput(event.getMessage(), InputType.IMAGE).getStream());
            input = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
            input.getGraphics().drawImage(read, 0, 0, null);
        } catch (IOException | LumaException e) {
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }

        filterImage(input, brightness, sharpness, contrast, saturation, noise);

        return EmbedUtilities.getImageMessage(input, event.getLocalization());
    }

    private static void filterImage(BufferedImage original, int brightness, int sharpness, int contrast, int saturation, int noise){

        RescaleOp op = new RescaleOp(brightness/100f, 0, null);
        op.filter(original, original);

        try {
            ImageIO.write(original, "png", new File("temp/brightness.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        RescaleOp contrastOp1 = new RescaleOp(contrast/100f, 20.0f, null);
        contrastOp1.filter(original, original);

        try {
            ImageIO.write(original, "png", new File("temp/contrast.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Kernel sharpenKernel = new Kernel(3, 3, new float[] {
                -(sharpness/100f), -(sharpness/100f), -(sharpness/100f),
                -(sharpness/100f), (sharpness/100f)*3, -(sharpness/100f),
                -(sharpness/100f), -(sharpness/100f), -(sharpness/100f)});

        ConvolveOp sharpenOp = new ConvolveOp(sharpenKernel);
        original = sharpenOp.filter(original, null);

        try {
            ImageIO.write(original, "png", new File("temp/sharpen.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        RGBImageFilter saturationFilter = new RGBImageFilter() {
            public int filterRGB(int x, int y, int rgb) {
                float[] hsb = Color.RGBtoHSB((rgb>>16)&0xFF, (rgb>>8)&0xFF ,0xFF, null);
                return Color.HSBtoRGB(hsb[0], hsb[1] * (saturation/100f), hsb[2]);
            }
        };

        FilteredImageSource saturationSource = new FilteredImageSource(original.getSource(), saturationFilter);
        original.getGraphics().drawImage(Toolkit.getDefaultToolkit().createImage(saturationSource), 0, 0, null);

        try {
            ImageIO.write(original, "png", new File("temp/saturation.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        float std = (noise/100f);
        Random random = new Random();
        RGBImageFilter noiseFilter = new RGBImageFilter() {
            public int filterRGB(int x, int y, int rgb) {
                int ret = ((int)(((rgb>>16)&0xFF)+(random.nextGaussian()*std)))&0xFF;
                ret = (ret << 8) + (((int)(((rgb>>8)&0xFF)+(random.nextGaussian()*std)))&0xFF);
                ret = (ret << 8) + (((int)((rgb&0xFF)+(random.nextGaussian()*std)))&0xFF);
                return ret;
            }
        };

        FilteredImageSource noiseSource = new FilteredImageSource(original.getSource(), noiseFilter);
        original.getGraphics().drawImage(Toolkit.getDefaultToolkit().createImage(noiseSource), 0, 0, null);

        try {
            ImageIO.write(original, "png", new File("temp/noise.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
