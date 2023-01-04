package gq.luma.bot.systems;

import gq.luma.bot.Luma;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.message.MessageDeleteEvent;
import org.javacord.api.event.message.MessageEditEvent;
import org.javacord.api.listener.message.MessageDeleteListener;
import org.javacord.api.listener.message.MessageEditListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
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
                                                .filter(message -> message.getAuthor().getId() != 185013154198061056L)
                                                .ifPresentOrElse(message -> serverLog.sendMessage(new EmbedBuilder()
                                                                .setTitle("Message from " + message.getAuthor().getDiscriminatedName() + " deleted")
                                                                .addField("Content", message.getContent(), false)
                                                                .setTimestamp(Instant.now())
                                                                .setFooter("#" + channel.getName())
                                                                .setColor(getTopRoleColor(message.getAuthor(), server))),
                                                        () -> serverLog.sendMessage(new EmbedBuilder()
                                                                .setTitle("Message deleted")
                                                                .addField("__Unable to query message information__", "_ _", false)
                                                                .setTimestamp(Instant.now())
                                                                .setFooter("#" + channel.getName())))),
                                        () -> logger.error("Couldn't find channel with id: " + id))));
    }

    @Override
    public void onMessageEdit(MessageEditEvent event) {
        event.getServer()
                .ifPresent(server -> Luma.database.getServerLog(server)
                        .ifPresent(id -> server.getTextChannelById(id)
                                .ifPresentOrElse(serverLog -> event.getChannel().asServerChannel()
                                        .ifPresent(channel -> serverLog.sendMessage(new EmbedBuilder()
                                                        .setTitle("Message from " + event.getMessage().getAuthor().getDiscriminatedName() + " edited")
                                                        .addField("Old Content", event.getOldMessage().map(Message::getContent).orElse("*Failed to query content*"), false)
                                                        .addField("New Content", event.getMessageContent(),false)
                                                        .setTimestamp(Instant.now())
                                                        .setFooter("#" + channel.getName())
                                                        .setColor(getTopRoleColor(event.getMessage().getAuthor(), server)))),
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
