package gq.luma.bot.commands;

import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.channels.ServerChannel;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.MessageSet;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import de.btobastian.javacord.entities.permissions.Role;
import gq.luma.bot.services.Bot;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.services.Database;
import gq.luma.bot.systems.watchers.SlowMode;
import gq.luma.bot.utils.embeds.EmbedUtilities;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ServerCommands {
    @Command(aliases = {"slow"}, description = "slow_description", usage = "slow_usage", neededGuildPerms = "mod", whilelistedGuilds = "146404426746167296")
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

    @Command(aliases = {"cleanup"}, description = "cleanup_description", usage = "cleanup_usage", neededGuildPerms = "mod", whilelistedGuilds = "146404426746167296")
    public void onCleanup(CommandEvent event){
        if(event.getCommandArgs().length >= 1){
            int messageCount = Integer.valueOf(event.getCommandArgs()[0]);
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

    @Command(aliases = {"role", "roles"}, description = "role_description", usage = "", whilelistedGuilds = "146404426746167296")
    public EmbedBuilder onRole(CommandEvent event) throws SQLException {
        User user = event.getMessage().getAuthor().asUser().orElseThrow(IllegalArgumentException::new);
        Server server = event.getMessage().getServer().orElseThrow(IllegalArgumentException::new);
        if(event.getCommandArgs().length >= 1){
            Optional<Role> roleOptional = Database.getRoleByName(event.getCommandArgs()[0]).flatMap(event.getApi()::getRoleById);
            if(roleOptional.isPresent()){
                Role role = roleOptional.get();
                if(!server.getRolesOf(user).contains(role)){
                    List<Role> currentRoles = server.getRolesOf(user);
                    currentRoles.add(role);
                    server.updateRoles(user, currentRoles);
                    return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("role_added_message"), role.getName()), event.getLocalization());
                } else {
                    List<Role> currentRoles = server.getRolesOf(user);
                    currentRoles.remove(role);
                    server.updateRoles(user, currentRoles);
                    server.updateRoles(user, currentRoles);
                    return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("role_removed_message"), role.getName()), event.getLocalization());
                }
            } else {
                return EmbedUtilities.getErrorMessage(String.format(event.getLocalization().get("role_not_found_message"), event.getCommandArgs()[0]), event.getLocalization());
            }
        } else {
            return new EmbedBuilder().setColor(BotReference.LUMA_COLOR).addField(event.getLocalization().get("role_available_title"), String.join("\n", Database.getAvailibeRoles()), false);
        }
    }
}
