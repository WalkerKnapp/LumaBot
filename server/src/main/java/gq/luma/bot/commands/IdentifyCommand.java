package gq.luma.bot.commands;

import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import clarifai2.dto.prediction.Prediction;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import gq.luma.bot.Luma;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.params.io.input.FileInput;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.commands.subsystem.Localization;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.services.TesseractApi;
import gq.luma.bot.utils.LumaException;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class IdentifyCommand {
    @Command(aliases = {"identify", "i"}, description = "identify_description", usage = "identify_usage")
    public void onIdentify(CommandEvent event){
        Localization loc = event.getLocalization();
        CompletableFuture<Message> responseMessage = event.getMessage().getChannel().sendMessage(loc.get("analyze_pending"));
        try {
            FileInput fi = ParamUtilities.getFirstInput(event.getMessage(), InputType.IMAGE);
            try(InputStream is = fi.getStream()){
                byte[] imageStream = is.readAllBytes();
                List<? extends ClarifaiOutput<? extends Prediction>> conceptList = Luma.clarifai.analyzeImage(Luma.clarifai.getGeneralModel(), imageStream);
                EmbedBuilder eb = new EmbedBuilder().setColor(BotReference.LUMA_COLOR).setTitle("Found the following:");
                AtomicInteger featurecount = new AtomicInteger();
                conceptList.forEach(output -> output.data().stream().map(prediction -> (Concept)prediction).forEach(concept -> {
                    if(featurecount.get() < 4) {
                        eb.addField(concept.name(), Float.toString(concept.value() * 100) + "%", false);
                        featurecount.getAndIncrement();
                    }
                }));
                responseMessage.join().edit("", eb);
            }

        } catch (LumaException | IOException e) {
            e.printStackTrace();
            responseMessage.join().edit("", EmbedUtilities.getErrorMessage(e.getMessage(), loc));
        }
    }

    @Command(aliases = {"read", "ocr"}, description = "ocr_description", usage = "ocr_usage")
    public void onOcr(CommandEvent event){
        Localization loc = event.getLocalization();
        CompletableFuture<Message> responseMessage = event.getMessage().getChannel().sendMessage(loc.get("analyze_pending"));
        try {
            FileInput fi = ParamUtilities.getFirstInput(event.getMessage(), InputType.IMAGE);
            try(InputStream is = fi.getStream()){
                String meme = TesseractApi.doOcr(ImageIO.read(is));
                responseMessage.join().edit("", new EmbedBuilder().setColor(BotReference.LUMA_COLOR).setTitle("Found the following text: ").setDescription(meme));
            }

        } catch (LumaException | IOException | TesseractException e) {
            e.printStackTrace();
            responseMessage.join().edit("", EmbedUtilities.getErrorMessage(e.getMessage(), loc));
        }
    }
}
