package gq.luma.bot.commands;

import com.jhlabs.image.*;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.utils.LumaException;
import gq.luma.bot.utils.embeds.EmbedUtilities;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

public class MemeCommands {
    @Command(aliases = {"deepfry"}, description = "deepfry_description", usage = "", parent = "meme")
    public EmbedBuilder onDeepFry(CommandEvent event){
        int brightness = 100;
        int sharpness = 100;
        int contrast = 100;
        int saturation = 100;
        int noise = 100;

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
            input = ImageIO.read(ParamUtilities.getInput(event.getMessage(), InputType.IMAGE).getStream());
        } catch (IOException | LumaException e) {
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }

        BufferedImage finalImage = generateFilter(brightness, sharpness, contrast, saturation, noise).filter(input, null);

        return EmbedUtilities.getImageMessage(finalImage, event.getLocalization());
    }

    private static CompoundFilter generateFilter(int brightness, int sharpness, int contrast, int saturation, int noise){
        ContrastFilter contrastFilter = new ContrastFilter();
        contrastFilter.setBrightness(brightness/100f);
        contrastFilter.setContrast(contrast/100f);

        SharpenFilter sharpenFilter = new SharpenFilter();

        SaturationFilter saturationFilter = new SaturationFilter();
        saturationFilter.setAmount((saturation/100f) + 1f);

        NoiseFilter noiseFilter = new NoiseFilter();
        noiseFilter.setAmount(noise);

        if(sharpness != 0f) {
            return new CompoundFilter(sharpenFilter, new CompoundFilter(saturationFilter, noiseFilter));
        }
        else{
            return new CompoundFilter(saturationFilter, noiseFilter);
        }
    }
}
