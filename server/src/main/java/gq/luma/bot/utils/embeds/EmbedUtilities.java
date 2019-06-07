package gq.luma.bot.utils.embeds;

import gq.luma.bot.reference.BotReference;
import gq.luma.bot.commands.subsystem.Localization;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmbedUtilities {

    public static EmbedBuilder getErrorMessage(String error, Localization localization){
        return new EmbedBuilder()
                .setAuthor(localization.get("error"), "", "https://i.imgur.com/SPeiFGu.png")
                .addField(localization.get("error_message"), error, true)
                .setColor(Color.RED);
    }

    public static EmbedBuilder getSuccessMessage(String message, Localization localization){
        return new EmbedBuilder()
                .setAuthor(localization.get("success"), "", "https://i.imgur.com/R8g3toc.png")
                .addField(localization.get("success_message"), message, true)
                .setColor(new Color(118, 255, 3));
    }

    public static EmbedBuilder getImageMessage(BufferedImage image, Localization localization){
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return new EmbedBuilder()
                    .setImage(baos.toByteArray())
                    .setColor(BotReference.LUMA_COLOR);
        } catch (IOException e) {
            return getErrorMessage(e.getMessage(), localization);
        }
    }

    public static String uploadImgur(BufferedImage image) throws IOException {

        /*URL url = new URL("https://api.imgur.com/3/image");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"))

        HttpResponse<JsonNode> request = Unirest.post(url.toString())
                .header("Authorization", "Client-ID " + Bot.globalProperties.getProperty("imgur-id"))
                .field("image", new ByteArrayInputStream(os.toByteArray()), "m.png")
                .field("type", "file")
                .asJson();

        return request.getBody().getObject().getJSONObject("data").getString("link");*/
        return null;
    }

    public static EmbeddedMessage sendCompiledEmbedAsEdit(FilteredEmbed builder, Message message){

        List<EmbedPage> pages = new ArrayList<>(Collections.singletonList(new EmbedPage()));

        List<Object[]> section = new ArrayList<>();

        for(int i = 0; i < builder.getFields().size(); i++) {
            if ((boolean) builder.getFields().get(i)[3]) {
                if (pages.get(pages.size() - 1).getFields().size() + section.size() > BotReference.FIELDS_PER_PAGE) {
                    pages.add(new EmbedPage());
                    pages.get(pages.size() - 1).getFields().addAll(section);
                } else {
                    pages.get(pages.size() - 1).getFields().addAll(section);
                }
                section.clear();
            }
            section.add(builder.getFields().get(i));
        }
        if(!section.isEmpty()){
            if (pages.get(pages.size() - 1).getFields().size() + section.size() > BotReference.FIELDS_PER_PAGE) {
                pages.add(new EmbedPage());
                pages.get(pages.size() - 1).getFields().addAll(section);
            } else {
                pages.get(pages.size() - 1).getFields().addAll(section);
            }
            section.clear();
        }

        return new EmbeddedMessage(message, builder, pages);
    }
}
