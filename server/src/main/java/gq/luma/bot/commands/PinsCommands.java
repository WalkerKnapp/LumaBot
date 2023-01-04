package gq.luma.bot.commands;

import emoji4j.EmojiUtils;
import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import org.apache.logging.log4j.util.Strings;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.emoji.CustomEmoji;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.util.DiscordRegexPattern;

import java.awt.*;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class PinsCommands {
    @Command(aliases = {"emoji"}, description = "pins_emoji_description", usage = "", parent = "pins", neededPerms = "PINS")
    public EmbedBuilder onPinsEmoji(CommandEvent event) {
        if (event.getServer().isPresent()) {
            Server server = event.getServer().get();

            // Try to parse as a custom emoji
            if (event.getMessage().getCustomEmojis().size() > 0) {
                CustomEmoji emoji = event.getMessage().getCustomEmojis().get(0);

                Luma.database.setServerPinEmojiCustom(server.getId(), emoji.getId());

                return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("pins_emoji_updated"), emoji.getMentionTag()), event.getLocalization());

            } else if (event.getCommandArgs().length > 0) {
                String arg = event.getCommandArgs()[0];

                // Test for unicode emojis
                if (EmojiUtils.isEmoji(arg)) {
                    Luma.database.setServerPinEmojiUnicode(server.getId(), EmojiUtils.getEmoji(arg).getEmoji());

                    return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("pins_emoji_updated"), EmojiUtils.getEmoji(arg).getEmoji()), event.getLocalization());
                } else {
                    return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("pins_emoji_parse_error"), arg), event.getLocalization());
                }
            }

            return EmbedUtilities.getErrorMessage(event.getLocalization().get("pins_emoji_not_found"), event.getLocalization());
        }
        return null;
    }

    @Command(aliases = {"channel"}, description = "pins_channel_description", usage = "", parent = "pins", neededPerms = "PINS")
    public EmbedBuilder onPinsChannel(CommandEvent event) {
        if (event.getServer().isPresent()) {
            Server server = event.getServer().get();
            ServerTextChannel pinsChannel = null;

            // Try to parse as a mention
            if (event.getMessage().getMentionedChannels().size() > 0) {
                pinsChannel = event.getMessage().getMentionedChannels().get(0).asServerTextChannel().orElse(null);
            } else if (event.getCommandArgs().length > 0) {
                String arg = event.getCommandArgs()[0];

                // Try to parse out a string mention
                Matcher matcher = DiscordRegexPattern.CHANNEL_MENTION.matcher(arg);
                if (matcher.matches()) {
                    String id = matcher.group("id");
                    pinsChannel = server.getTextChannelById(id).orElse(null);
                } else {
                    // Try to parse an ID number
                    try {
                        long id = Long.parseLong(arg);
                        pinsChannel = server.getTextChannelById(id).orElse(null);
                    } catch (NumberFormatException e) {
                        return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("pins_channel_parse_error"), arg), event.getLocalization());
                    }
                }
            }

            if (pinsChannel == null) {
                return EmbedUtilities.getErrorMessage(event.getLocalization().get("pins_channel_not_found"), event.getLocalization());
            }

            Luma.database.setServerPinChannel(server.getId(), pinsChannel.getId());

            return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("pins_channel_updated"), pinsChannel.getMentionTag()), event.getLocalization());
        }
        return null;
    }

    @Command(aliases = {"threshold"}, description = "pins_threshold_description", usage = "", parent = "pins", neededPerms = "PINS")
    public EmbedBuilder onPinsThreshold(CommandEvent event) {
        if (event.getServer().isPresent()) {
            Server server = event.getServer().get();

            if (event.getCommandArgs().length > 0) {
                String arg = event.getCommandArgs()[0];

                try {
                    int threshold = Integer.parseInt(arg);

                    Luma.database.setServerPinThreshold(server.getId(), threshold);

                    return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("pins_threshold_updated"), threshold), event.getLocalization());
                } catch (NumberFormatException e) {
                    // ignored
                }

                return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("pins_threshold_parse_error"), arg), event.getLocalization());
            }

            return EmbedUtilities.getErrorMessage(event.getLocalization().get("pins_threshold_not_found"), event.getLocalization());
        }
        return null;
    }

    @Command(aliases = {"blacklist"}, description = "pins_blacklist_description", usage = "", parent = "pins", neededPerms = "PINS")
    public EmbedBuilder onPinsBlacklist(CommandEvent event) {
        if (event.getServer().isPresent()) {
            Server server = event.getServer().get();
            ServerTextChannel blacklistChannel = null;

            // Try to parse as a mention
            if (event.getMessage().getMentionedChannels().size() > 0) {
                blacklistChannel = event.getMessage().getMentionedChannels().get(0).asServerTextChannel().orElse(null);
            } else if (event.getCommandArgs().length > 0) {
                String arg = event.getCommandArgs()[0];

                // Try to parse out a string mention
                Matcher matcher = DiscordRegexPattern.CHANNEL_MENTION.matcher(arg);
                if (matcher.matches()) {
                    String id = matcher.group("id");
                    blacklistChannel = server.getTextChannelById(id).orElse(null);
                } else {
                    // Try to parse an ID number
                    try {
                        long id = Long.parseLong(arg);
                        blacklistChannel = server.getTextChannelById(id).orElse(null);
                    } catch (NumberFormatException e) {
                        return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("pins_blacklist_parse_error"), arg), event.getLocalization());
                    }
                }
            }

            if (blacklistChannel == null) {
                return new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle(event.getLocalization().get("pins_blacklist_title"))
                        .setDescription(Strings.join(Luma.database.getPinBlacklist(server.getId()).map(ServerTextChannel::getMentionTag).collect(Collectors.toList()), '\n'));
            }

            if (Luma.database.isChannelPinBlacklisted(server.getId(), blacklistChannel.getId())) {
                Luma.database.deletePinBlacklist(server.getId(), blacklistChannel.getId());

                return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("pins_blacklist_removed"), blacklistChannel.getMentionTag()), event.getLocalization());
            } else {
                Luma.database.addPinBlacklist(server.getId(), blacklistChannel.getId());

                return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("pins_blacklist_added"), blacklistChannel.getMentionTag()), event.getLocalization());
            }
        }
        return null;
    }

    @Command(aliases = {"editable"}, description = "pins_editable_description", usage = "", parent = "pins", neededPerms = "PINS")
    public EmbedBuilder onPinsEditable(CommandEvent event) throws SQLException {
        if (event.getServer().isPresent()) {
            Server server = event.getServer().get();

            if (event.getCommandArgs().length > 0) {
                String arg = event.getCommandArgs()[0];

                if (arg.equalsIgnoreCase("true")) {
                    Luma.database.setServerPinEditable(server.getId(), true);
                    return EmbedUtilities.getSuccessMessage(event.getLocalization().get("pins_editable_updated_true"), event.getLocalization());
                } else if (arg.equalsIgnoreCase("false")) {
                    Luma.database.setServerPinEditable(server.getId(), false);
                    return EmbedUtilities.getSuccessMessage(event.getLocalization().get("pins_editable_updated_false"), event.getLocalization());
                } else {
                    return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("pins_editable_parse_error"), arg), event.getLocalization());
                }
            } else {
                if (Luma.database.isServerPinEditable(server.getId())) {
                    return new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle(event.getLocalization().get("pins_editable_title"))
                            .setDescription(event.getLocalization().get("pins_editable_true_description"));
                } else {
                    return new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle(event.getLocalization().get("pins_editable_title"))
                            .setDescription(event.getLocalization().get("pins_editable_false_description"));
                }
            }
        } else {
            return EmbedUtilities.getErrorMessage(event.getLocalization().get("pins_editable_in_server"), event.getLocalization());
        }
    }
}
