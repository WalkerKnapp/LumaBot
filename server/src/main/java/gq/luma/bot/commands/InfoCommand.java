package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.time.temporal.ChronoField;

public class InfoCommand {

    private static final long P2SR_BOT_SPAM_ID = 401828833189429258L;

    @Command(aliases = {"ping"}, description = "ping_description", usage = "")
    public EmbedBuilder onPing(CommandEvent event){
        int pingAmt = event.getMessage().getCreationTimestamp().minusMillis(System.currentTimeMillis()).get(ChronoField.NANO_OF_SECOND)/1000000;

        if (event.getChannel().getId() == P2SR_BOT_SPAM_ID) {
            int pingCount = Luma.database.incrementPingCount(event.getAuthor().getId());

            int oldPB = Luma.database.getFastestPing(event.getAuthor().getId());
            if (pingAmt < oldPB) {
                Luma.database.setFastestPing(event.getAuthor().getId(), pingAmt);
                return new EmbedBuilder()
                        .setAuthor(event.getAuthor())
                        .addField("Pong!", pingAmt + " ms", true)
                        .addField("NEW PERSONAL BEST", "Old: " + (oldPB != Integer.MAX_VALUE ? (oldPB + " ms") : ""), true)
                        .addField("Leaderboard", Luma.database.getPingLeaderboard(), false)
                        .setFooter("You have pinged " + pingCount + " times.")
                        .setColor(BotReference.LUMA_COLOR);
            } else {
                return new EmbedBuilder()
                        .setAuthor(event.getAuthor())
                        .addField("Pong!", pingAmt + " ms", true)
                        .addField("Leaderboard", Luma.database.getPingLeaderboard(), false)
                        .setFooter("You have pinged " + pingCount + " times.")
                        .setColor(BotReference.LUMA_COLOR);
            }
        } else {
            return new EmbedBuilder()
                    .addField(event.getLocalization().get("ping_response"), pingAmt + " ms", true)
                    .setColor(BotReference.LUMA_COLOR);
        }
    }
}
