package gq.luma.bot.commands;

import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageSet;

import java.util.stream.Collectors;

public class ServerCommands {
    @Command(aliases = {"cleanup"}, description = "cleanup_description", usage = "cleanup_usage", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296;425386024835874826;902666492817072158")
    public void onCleanup(CommandEvent event){
        if(event.getCommandArgs().length >= 1){
            int messageCount = Math.max(Integer.parseInt(event.getCommandArgs()[0]) + 1, 0);
            MessageSet messages = event.getChannel().getMessages(messageCount).join();
            Message.delete(event.getApi(), messages.stream().filter(m -> !m.isPinned()).collect(Collectors.toList())).join();
        } else {
            event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage(event.getLocalization().get("not_enough_arguments"), event.getLocalization()));
        }
    }
}
