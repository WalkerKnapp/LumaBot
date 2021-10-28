package gq.luma.bot.services;

import gq.luma.bot.Luma;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.emoji.Emoji;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.Reaction;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.webhook.IncomingWebhook;
import org.javacord.api.entity.webhook.Webhook;
import org.javacord.api.event.message.reaction.SingleReactionEvent;
import org.javacord.api.util.logging.ExceptionLogger;

import java.awt.*;
import java.util.Optional;

public class PinsService implements Service {
    @Override
    public void startService() throws Exception {
        Bot.api.addReactionAddListener(event -> {
            if (!event.getServer().isPresent()) {
                return;
            }
            Server server = event.getServer().get();

            // Ignore messages in any channels on the blacklist

            if (Luma.database.isChannelPinBlacklisted(server.getId(), event.getChannel().getId())) {
                return;
            }

            // Check if the emoji matches the server's pin emoji (if it exists)
            if (Luma.database.ifServerPinEmojiMatches(server, event.getEmoji())) {
                // Check if the message has already been pinned
                if(Luma.database.isPinnedMessageNotified(event.getMessageId())) {
                    // Update its pin count
                    updateMessagePinCount(event, server);
                } else {
                    event.requestReaction().thenAccept(reactionOp -> reactionOp.ifPresent(reaction -> {
                        // Check if it needs to be pinned
                        if (reaction.getCount() >= Luma.database.getServerPinThreshold(server).orElse(Integer.MAX_VALUE)) {
                            // Pin the message
                            Message pinnedMessage = event.requestMessage().join();

                            Luma.database.getServerPinChannel(server).ifPresent(pinsChannel -> {
                                IncomingWebhook pinWebhook = pinsChannel.getWebhooks().join().stream()
                                        .filter(Webhook::isIncomingWebhook)
                                        .filter(webhook -> webhook.getName().map(name -> name.contains("Luma")).orElse(false))
                                        .map(Webhook::asIncomingWebhook)
                                        .findAny().orElseGet(() -> this.createPinWebhook(pinsChannel))
                                        .orElseThrow(AssertionError::new);

                                Message pinNotification = pinnedMessage.toWebhookMessageBuilder()
                                        .addEmbed(new EmbedBuilder()
                                                .setColor(Color.RED)
                                                .setDescription(reaction.getEmoji().getMentionTag() + " " + reaction.getCount() + " - [Jump!](" + pinnedMessage.getLink().toString() + ")"))
                                        .setAllowedMentions(new AllowedMentionsBuilder()
                                                .setMentionEveryoneAndHere(false)
                                                .setMentionRoles(false)
                                                .setMentionUsers(false).build())
                                        .send(pinWebhook).join();

                                Luma.database.createPinNotification(pinnedMessage.getId(), pinNotification.getId());
                            });
                        }
                    }));
                }
            }
        });

        Bot.api.addReactionRemoveListener(event -> {
            if (!event.getServer().isPresent()) {
                return;
            }
            Server server = event.getServer().get();

            // Check if the emoji matches the server's pin emoji (if it exists)
            if (Luma.database.ifServerPinEmojiMatches(server, event.getEmoji())) {
                // Check if the message is pinned
                if(Luma.database.isPinnedMessageNotified(event.getMessageId())) {
                    // Update its pin count
                    updateMessagePinCount(event, server);
                }
            }
        });

        Bot.api.addMessageDeleteListener(event -> {
            // TODO: Reflect deleted messages in pins

            if (Luma.database.isPinnedMessageNotified(event.getMessageId())) {
                // TODO: Delete the pin record
            }
        });

        Bot.api.addMessageEditListener(event -> {
            // TODO: Reflect edited messages in pins
        });
    }

    private void updateMessagePinCount(SingleReactionEvent event, Server server) {
        event.requestReaction().thenApply(reactionOp -> reactionOp.map(Reaction::getCount).orElse(0)).thenAccept(count -> {
            updateNumericPinCount(event, server, count);
        }).exceptionally(e -> {
            updateNumericPinCount(event, server, 0);
            return null;
        });
    }

    private void updateNumericPinCount(SingleReactionEvent event, Server server, int count) {
        String serverPinEmojiMention = Luma.database.getServerPinEmojiMention(server).orElseThrow(AssertionError::new);

        Message pinnedMessage = event.requestMessage().join();
        Message pinNotification = Luma.database.getPinNotificationByPinnedMessage(event.getMessageId(), Luma.database.getServerPinChannel(server).orElseThrow(AssertionError::new).getId());
        IncomingWebhook pinWebhook = pinNotification.getAuthor().asWebhook()
                .orElseThrow(AssertionError::new).join()
                .asIncomingWebhook().orElseThrow(AssertionError::new);

        EmbedBuilder pinBox = new EmbedBuilder()
                .setColor(Color.RED)
                .setDescription(serverPinEmojiMention + " " + count + " - [Jump!](" + pinnedMessage.getLink().toString() + ")");

        Bot.api.getUncachedMessageUtil().edit(pinWebhook.getId(), pinWebhook.getToken(),
                pinNotification.getId(), pinNotification.getContent(), true,
                pinBox, true).exceptionally(ExceptionLogger.get());
    }

    private Optional<IncomingWebhook> createPinWebhook(ServerTextChannel textChannel) {
        return Optional.of(textChannel.createWebhookBuilder()
                .setName("Luma Pins")
                .setAvatar(Bot.api.getYourself().getAvatar())
                .create().join());
    }
}
