package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.services.Bot;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.services.Database;
import gq.luma.bot.systems.watchers.SlowMode;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageSet;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.util.logging.ExceptionLogger;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ServerCommands {
    @Command(aliases = {"slow"}, description = "slow_description", usage = "slow_usage", neededPerms = "SET_SLOW_MODE", whilelistedGuilds = "146404426746167296")
    public EmbedBuilder onSlow(CommandEvent event){
        if(event.getCommandArgs().length >= 2){
            String channelMention = event.getCommandArgs()[0];
            String seconds = event.getCommandArgs()[1];
            event.getApi().getTextChannelById(channelMention.substring(2, channelMention.length() - 1)).ifPresentOrElse(tc -> {
                if(SlowMode.slowModes.containsKey(tc.getId())){
                    SlowMode.slowModes.remove(tc.getId());
                    event.getChannel().sendMessage("", EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("slow_disable_message"), tc.asServerTextChannel().map(ServerChannel::getName).orElse(event.getLocalization().get("channel_unnamed"))), event.getLocalization()));
                } else {
                    SlowMode.slowModes.put(tc.getId(), Integer.parseInt(seconds) * 1000);
                    event.getChannel().sendMessage("", EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("slow_enable_message"), tc.asServerTextChannel().map(ServerChannel::getName).orElse(event.getLocalization().get("channel_unnamed"))), event.getLocalization()));
                }
            }, () -> event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage(event.getLocalization().get("slow_no_channel_message"), event.getLocalization())));
        } else {
            return EmbedUtilities.getErrorMessage(event.getLocalization().get("not_enough_arguments"), event.getLocalization());
        }
        return null;
    }

    @Command(aliases = {"cleanup"}, description = "cleanup_description", usage = "cleanup_usage", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public void onCleanup(CommandEvent event){
        if(event.getCommandArgs().length >= 1){
            int messageCount = Math.max(Integer.valueOf(event.getCommandArgs()[0]), 0);
            MessageSet history = event.getChannel().getMessages(messageCount).join();
            event.getChannel().bulkDelete(history).exceptionally(t -> {
                for(Message m : history){
                    m.delete();
                }
                return null;
            });
        } else {
            event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage(event.getLocalization().get("not_enough_arguments"), event.getLocalization()));
        }
    }

    @Command(aliases = {"role", "roles"}, description = "role_description", usage = "")
    public EmbedBuilder onRole(CommandEvent event) throws SQLException {
        if(event.getServer().isPresent()) {
            User user = event.getAuthor();
            Server server = event.getServer().get();
            if (event.getCommandArgs().length >= 1) {
                Optional<Role> roleOptional = Luma.database.getRoleByName(server.getId(), event.getCommandRemainder()).flatMap(event.getApi()::getRoleById);
                if (roleOptional.isPresent()) {
                    Role role = roleOptional.get();
                    if (!server.getRoles(user).contains(role)) {
                        server.addRoleToUser(user, role).exceptionally(ExceptionLogger.get());
                        return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("role_added_message"), role.getName()), event.getLocalization());
                    } else {
                        server.removeRoleFromUser(user, role).exceptionally(ExceptionLogger.get());
                        return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("role_removed_message"), role.getName()), event.getLocalization());
                    }
                } else {
                    return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("role_not_found_message"), event.getCommandRemainder()), event.getLocalization());
                }
            } else {
                return new EmbedBuilder().setColor(BotReference.LUMA_COLOR).addField(event.getLocalization().get("role_available_title"), BotReference.ZERO_LENGTH_CHAR + String.join("\n", Luma.database.getAvailibeRoles(server.getId())), false);
            }
        }
        return null;
    }
}
