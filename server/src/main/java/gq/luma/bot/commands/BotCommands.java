package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.commands.subsystem.Localization;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import org.apache.commons.io.FilenameUtils;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class BotCommands {
    @Command(aliases = {"stop"}, description = "stop_usage", usage = "", neededPerms = "DEVELOPER")
    public void onStop(CommandEvent event){
        event.getChannel().sendMessage(EmbedUtilities.getSuccessMessage(event.getLocalization().get("stop_message"), event.getLocalization())).join();
        event.getApi().disconnect();
        System.exit(0);
    }

    @Command(aliases = {"reload"}, description = "reload_usage", usage = "", neededPerms = "DEVELOPER")
    public EmbedBuilder onReload(CommandEvent event) throws Exception {
        if(event.getCommandArgs().length > 0){
            if(event.getCommandArgs()[0].equalsIgnoreCase("filters")){
                Luma.filterManager.startService();
                return EmbedUtilities.getSuccessMessage("Reloaded filters.", event.getLocalization());
            }
            for(String file : event.getCommandArgs()){
                try {
                    InputStream stream = new FileInputStream(new File(FileReference.localesDir, file));
                    event.getExecutor().setLocalization(FilenameUtils.getBaseName(file), new Localization(stream, FilenameUtils.getBaseName(file)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return EmbedUtilities.getSuccessMessage(String.format(event.getLocalization().get("reload_success_message"), event.getCommandArgs().length), event.getLocalization());
        }
        else{
            return EmbedUtilities.getErrorMessage(event.getLocalization().get("reload_failure_message"), event.getLocalization());
        }
    }
}
