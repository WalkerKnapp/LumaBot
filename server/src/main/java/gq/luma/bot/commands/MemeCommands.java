package gq.luma.bot.commands;

import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.LumaException;
import gq.luma.bot.utils.embeds.EmbedUtilities;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
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
            input = ImageIO.read(ParamUtilities.getFirstInput(event.getMessage(), InputType.IMAGE).getStream());
        } catch (IOException | LumaException e) {
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }

        filterImage(input, brightness, sharpness, contrast, saturation, noise);

        return EmbedUtilities.getImageMessage(input, event.getLocalization());
    }

    private static void filterImage(BufferedImage original, int brightness, int sharpness, int contrast, int saturation, int noise){

        RescaleOp op = new RescaleOp(brightness/100f, 0, null);
        op.filter(original, original);

        RescaleOp contrastOp1 = new RescaleOp(0, -0.5f, null);
        RescaleOp contrastOp2 = new RescaleOp(contrast/100f, 0.5f, null);
        contrastOp1.filter(original, original);
        contrastOp2.filter(original, original);

        Kernel sharpenKernel = new Kernel(3, 3, new float[] {
                -(sharpness/100), -(sharpness/100), -(sharpness/100),
                -(sharpness/100), (sharpness/100)*3, -(sharpness/100),
                -(sharpness/100), -(sharpness/100), -(sharpness/100)});

        ConvolveOp sharpenOp = new ConvolveOp(sharpenKernel);
        sharpenOp.filter(original, original);

        RGBImageFilter saturationFilter = new RGBImageFilter() {
            public int filterRGB(int x, int y, int rgb) {
                float[] hsb = Color.RGBtoHSB((rgb>>16)&0xFF, (rgb>>8)&0xFF ,0xFF, null);
                return Color.HSBtoRGB(hsb[0], hsb[1] * (saturation/100), hsb[2]);
            }
        };

        FilteredImageSource saturationSource = new FilteredImageSource(original.getSource(), saturationFilter);
        original.getGraphics().drawImage(Toolkit.getDefaultToolkit().createImage(saturationSource), 0, 0, null);

        float std = (noise/100);
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
    }
}
