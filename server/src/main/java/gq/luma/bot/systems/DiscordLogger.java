package gq.luma.bot.systems;

import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.channels.TextChannel;
import de.btobastian.javacord.entities.message.MessageAuthor;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import de.btobastian.javacord.entities.permissions.Role;
import de.btobastian.javacord.events.message.MessageDeleteEvent;
import de.btobastian.javacord.events.message.MessageEditEvent;
import de.btobastian.javacord.listeners.message.MessageDeleteListener;
import de.btobastian.javacord.listeners.message.MessageEditListener;
import gq.luma.bot.Luma;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;

public class DiscordLogger implements MessageEditListener, MessageDeleteListener {
    private static final Logger logger = LoggerFactory.getLogger(DiscordLogger.class);
    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        event.getServer()
                .ifPresent(server -> Luma.database.getServerLog(server)
                        .ifPresent(id -> server.getTextChannelById(id)
                                .ifPresentOrElse(serverLog -> event.getChannel().asServerChannel()
                                        .ifPresent(channel -> event.getMessage()
                                                .ifPresentOrElse(message -> serverLog.sendMessage(new EmbedBuilder()
                                                                .setTitle("Message from " + message.getAuthor().getDiscriminatedName() + " deleted")
                                                                .addField("Content", message.getContent(), false)
                                                                .setTimestamp()
                                                                .setFooter("#" + channel.getName())
                                                                .setColor(getTopRoleColor(message.getAuthor(), server))),
                                                        () -> serverLog.sendMessage(new EmbedBuilder()
                                                                .setTitle("Message deleted")
                                                                .addField("__Unable to query message information__", "_ _", false)
                                                                .setTimestamp()
                                                                .setFooter("#" + channel.getName())))),
                                        () -> logger.error("Couldn't find channel with id: " + id))));
    }

    @Override
    public void onMessageEdit(MessageEditEvent event) {
        event.getServer()
                .ifPresent(server -> Luma.database.getServerLog(server)
                        .ifPresent(id -> server.getTextChannelById(id)
                                .ifPresentOrElse(serverLog -> event.getChannel().asServerChannel()
                                        .ifPresent(channel -> event.getMessage().ifPresentOrElse(message -> serverLog.sendMessage(new EmbedBuilder()
                                                        .setTitle("Message from " + message.getAuthor().getDiscriminatedName() + " edited")
                                                        .addField("Old Content", event.getOldContent().orElse("*Failed to query content*"), false)
                                                        .addField("New Content", event.getNewContent(),false)
                                                        .setTimestamp()
                                                        .setFooter("#" + channel.getName())
                                                        .setColor(getTopRoleColor(message.getAuthor(), server))),
                                                () -> serverLog.sendMessage(new EmbedBuilder()
                                                        .setTitle("Message edited")
                                                        .addField("__Unable to query message information__", "_ _", false)
                                                        .addField("Old Content", event.getOldContent().orElse("*Failed to query content*"), false)
                                                        .addField("New Content", event.getNewContent(),false)
                                                        .setTimestamp()
                                                        .setFooter("#" + channel.getName())))),
                                        () -> logger.error("Couldn't find channel with id: " + id))));
    }

    private Color getTopRoleColor(MessageAuthor author, Server server){
        if(author.asUser().isPresent()) {
            List<Role> roles = author.asUser().get().getRoles(server);
            for (int i = roles.size() - 1; i >= 0; i--) {
                if (roles.get(i).getColor().isPresent() && roles.get(i).getColor().get() != Color.BLACK) {
                    return roles.get(i).getColor().get();
                }
            }
        }
        return Color.BLACK;
    }
}
