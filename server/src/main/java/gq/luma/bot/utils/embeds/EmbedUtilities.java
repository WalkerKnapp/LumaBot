package gq.luma.bot.utils.embeds;

import gq.luma.bot.commands.subsystem.Localization;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;

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
}
