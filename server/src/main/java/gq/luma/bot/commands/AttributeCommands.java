package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.sql.SQLException;
import java.util.Optional;

public class AttributeCommands {
    @Command(aliases = {"server"}, description = "server_description", usage = "")
    public EmbedBuilder onServer(CommandEvent event){
        if(event.getCommandRemainder().isEmpty()){
            try {
                if (event.getServer().isPresent()){
                    String prefix = Luma.database.getServerPrefix(event.getServer().get());
                    String locale = Luma.database.getServerLocale(event.getServer().get());
                    return new EmbedBuilder()
                            .setColor(BotReference.LUMA_COLOR)
                            .addField(event.getLocalization().get("server"), event.getServer().get().getName(), false)
                            .addField(event.getLocalization().get("prefix"), prefix == null ? event.getLocalization().get("none") : prefix, true)
                            .addField(event.getLocalization().get("locale"), locale == null ? event.getLocalization().get("none") : locale, true);
                }
                return EmbedUtilities.getErrorMessage(event.getLocalization().get("non-server_warning"), event.getLocalization());
            } catch (SQLException e){
                return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
            }
        }
        return null;
    }

    @Command(aliases = {"prefix"}, description = "prefix_server_description", usage = "prefix_server_usage", parent = "server set", neededPerms = "CHANGE_PREFIX_OR_LANGUAGE")
    public EmbedBuilder onSetPrefix(CommandEvent event){
        try {
            if (event.getServer().isPresent()) {
                if (event.getCommandArgs().length == 1) {
                    String oldPrefix = Luma.database.getEffectivePrefix(event.getChannel());
                    Luma.database.setServerPrefix(event.getServer().get(), event.getCommandRemainder());
                    return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("server_prefix_changed_message"), event.getServer().get().getName(), oldPrefix, event.getCommandRemainder()), event.getLocalization());
                } else if (event.getCommandArgs().length > 1) {
                    return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("prefix_change_too_many_arguments"), event.getCommandRemainder()), event.getLocalization());
                } else {
                    return EmbedUtilities.getErrorMessage(event.getLocalization().get("prefix_change_not_enough_arguments"), event.getLocalization());
                }
            } else {
                return EmbedUtilities.getErrorMessage(event.getLocalization().get("non-server_warning"), event.getLocalization());
            }
        } catch (SQLException e){
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }
    }

    @Command(aliases = {"lang", "locale"}, description = "locale_server_description", usage = "locale_server_usage", parent = "server set", neededPerms = "CHANGE_PREFIX_OR_LANGUAGE")
    public EmbedBuilder onSetLang(CommandEvent event){
        try {
            if (event.getCommandArgs().length == 1) {
                if (event.getServer().isPresent()) {
                    String oldLocale = Luma.database.getEffectiveLocale(event.getChannel());
                    if(event.getExecutor().getLocalization(event.getCommandRemainder()) != null){
                        Luma.database.setServerLocale(event.getServer().get(), event.getCommandRemainder());
                        return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("server_locale_changed_message"), event.getServer().get().getName(), oldLocale, event.getCommandRemainder()), event.getLocalization());
                    } else{
                        return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("locale_not_found_message"), event.getCommandRemainder()), event.getLocalization());
                    }
                } else {
                    return EmbedUtilities.getErrorMessage(event.getLocalization().get("non-server_warning"), event.getLocalization());
                }
            } else if (event.getCommandArgs().length > 1) {
                return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("locale_change_too_many_arguments"), event.getCommandRemainder()), event.getLocalization());
            } else {
                return EmbedUtilities.getErrorMessage(event.getLocalization().get("locale_change_not_enough_arguments"), event.getLocalization());
            }
        } catch (SQLException e){
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }
    }

    @Command(aliases = {"channel"}, description = "channel_description", usage = "channel_usage")
    public EmbedBuilder onChannel(CommandEvent event){
        try {
            TextChannel textChannel = event.getChannel();
            if (!event.getCommandRemainder().isEmpty()) {
                Optional<TextChannel> tc = event.getApi().getTextChannelById(event.getCommandRemainder());
                if (!tc.isPresent()) {
                    return null;
                }
                textChannel = tc.get();
            }

            String prefix = Luma.database.getChannelPrefix(textChannel);
            String locale = Luma.database.getChannelLocale(textChannel);

            return new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle(event.getLocalization().get("channel_overrides"))
                    .addField(event.getLocalization().get("channel"), "#" + textChannel.asServerTextChannel().map(ServerChannel::getName).orElseGet(() -> event.getLocalization().get("channel_unnamed")), false)
                    .addField(event.getLocalization().get("prefix"), prefix == null ? event.getLocalization().get("none") : prefix, true)
                    .addField(event.getLocalization().get("locale"), locale == null ? event.getLocalization().get("none") : locale, true);
        } catch (SQLException e){
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }
    }

    @Command(aliases = {"prefix"}, description = "prefix_channel_description", usage = "prefix_channel_usage", parent = "channel set", neededPerms = "CHANGE_PREFIX_OR_LANGUAGE")
    public EmbedBuilder onChannelPrefix(CommandEvent event){
        try {
            if (event.getCommandArgs().length > 0) {
                TextChannel textChannel = event.getChannel();
                if (event.getCommandArgs().length > 1) {
                    Optional<TextChannel> tc = event.getApi().getTextChannelById(event.getCommandArgs()[1]);
                    if (!tc.isPresent()) {
                        return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("invalid_channel_message"), event.getCommandArgs()[1]), event.getLocalization());
                    }
                    textChannel = tc.get();
                }
                String current = Luma.database.getChannelPrefix(textChannel);

                Luma.database.setChannelPrefix(textChannel, event.getCommandArgs()[0]);

                if (current == null) {
                    return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("channel_prefix_changed_message"), event.getCommandArgs()[0]), event.getLocalization());
                } else {
                    return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("channel_prefix_replaced_message"), current, event.getCommandArgs()[0]), event.getLocalization());
                }
            } else {
                return EmbedUtilities.getErrorMessage(event.getLocalization().get("prefix_change_not_enough_arguments"), event.getLocalization());
            }
        } catch (SQLException e){
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }
    }

    @Command(aliases = {"lang", "locale"}, description = "locale_channel_description", usage = "locale_channel_usage", parent = "channel set", neededPerms = "CHANGE_PREFIX_OR_LANGUAGE")
    public EmbedBuilder onChannelLang(CommandEvent event){
        try {
            if (event.getCommandArgs().length > 0) {
                TextChannel textChannel = event.getChannel();
                if (event.getCommandArgs().length > 1) {
                    Optional<TextChannel> tc = event.getApi().getTextChannelById(event.getCommandArgs()[1]);
                    if (!tc.isPresent()) {
                        return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("invalid_channel_message"), event.getCommandArgs()[1]), event.getLocalization());
                    }
                    textChannel = tc.get();
                }
                String current = Luma.database.getChannelLocale(textChannel);

                if(event.getExecutor().getLocalization(event.getCommandArgs()[0]) != null){
                    Luma.database.setChannelLocale(textChannel, event.getCommandArgs()[0]);
                    if(current != null){
                        return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("channel_locale_replaced_message"), current, event.getCommandArgs()[0]), event.getLocalization());
                    } else {
                        return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("channel_locale_changed_message"), event.getCommandArgs()[0]), event.getLocalization());
                    }
                } else {
                    return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("locale_not_found_message"), event.getCommandArgs()[0]), event.getLocalization());
                }
            } else {
                return EmbedUtilities.getErrorMessage(event.getLocalization().get("locale_change_not_enough_arguments"), event.getLocalization());
            }
        } catch (SQLException e){
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }
    }

    @Command(aliases = {"prefix"}, description = "channel_prefix_remove_description", usage = "channel_prefix_remove_usage", parent = "channel remove", neededPerms = "CHANGE_PREFIX_OR_LANGUAGE")
    public EmbedBuilder onChannelRemovePrefix(CommandEvent event){
        TextChannel textChannel = event.getChannel();
        if (event.getCommandArgs().length > 0) {
            Optional<TextChannel> tc = event.getApi().getTextChannelById(event.getCommandArgs()[1]);
            if (!tc.isPresent()) {
                return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("invalid_channel_message"), event.getCommandArgs()[1]), event.getLocalization());
            }
            textChannel = tc.get();
        }

        try {
            Luma.database.setChannelPrefix(textChannel, null);
            return EmbedUtilities.getSuccessMessage(event.getLocalization().get("prefix_removed_success_message"), event.getLocalization());
        } catch (SQLException e) {
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }
    }

    @Command(aliases = {"lang", "locale"}, description = "channel_locale_remove_description", usage = "channel_locale_remove_usage", parent = "channel remove", neededPerms = "CHANGE_PREFIX_OR_LANGUAGE")
    public EmbedBuilder onChannelRemoveLang(CommandEvent event){
        TextChannel textChannel = event.getChannel();
        if (event.getCommandArgs().length > 1) {
            Optional<TextChannel> tc = event.getApi().getTextChannelById(event.getCommandArgs()[1]);
            if (!tc.isPresent()) {
                return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("invalid_channel_message"), event.getCommandArgs()[1]), event.getLocalization());
            }
            textChannel = tc.get();
        }

        try {
            Luma.database.setChannelLocale(textChannel, null);
            return EmbedUtilities.getSuccessMessage(event.getLocalization().get("locale_removed_success_message"), event.getLocalization());
        } catch (SQLException e) {
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }
    }
}
