package gq.luma.bot.services;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.*;
import gq.luma.bot.commands.subsystem.CommandExecutor;
import gq.luma.bot.commands.subsystem.Localization;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.reference.DefaultReference;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.systems.DiscordLogger;
import gq.luma.bot.systems.watchers.SlowMode;
import org.apache.commons.io.FilenameUtils;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

public class Bot implements Service {
    private static Logger logger = LoggerFactory.getLogger(Bot.class);

    public static DiscordApi api;

    @Override
    public void startService() throws Exception {
        logger.info("Loading locales.");
        HashMap<String, Localization> locales = new HashMap<>();
        File[] children = FileReference.localesDir.listFiles();
        for(File file : Objects.requireNonNull(children)){
            if(FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("properties")){
                InputStream stream = new FileInputStream(file);
                locales.put(FilenameUtils.getBaseName(file.getName()), new Localization(stream, FilenameUtils.getBaseName(file.getName())));
                stream.close();
                logger.info("Registering: " + FilenameUtils.getBaseName(file.getName()));
            }
        }

        api = new DiscordApiBuilder().setToken(KeyReference.discordToken).login().join();

        api.updateActivity("?L help");
        CommandExecutor executor = new CommandExecutor(api);
        locales.forEach(executor::setLocalization);
        executor.registerCommand(new InfoCommand());
        executor.registerCommand(new BotCommands());
        executor.registerCommand(new AttributeCommands());
        executor.registerCommand(new MemeCommands());
        executor.registerCommand(new AnalyzeCommand());
        executor.registerCommand(new IdentifyCommand());
        executor.registerCommand(new ServerCommands());

        api.addMessageCreateListener(new SlowMode());
        api.addMessageCreateListener(Luma.filterManager);

        api.getServers().forEach(server -> {
            try {
                if (!Luma.database.isServerPresent(server)) {
                    logger.error("Found database desync! Server: {} was not found. Was the database reset?", server.toString());
                    Luma.database.addServer(server, DefaultReference.CLARIFAI_CAP, Instant.now());
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        api.addMessageCreateListener(event -> {
            if(event.getMessage().getContent().split("\\s+")[0].equalsIgnoreCase("!joindate")){
                event.getServer().ifPresent(server ->
                        event.getChannel().sendMessage("Joined at: " +
                                event.getMessage().getMentionedUsers().get(0).getJoinedAtTimestamp(server)));
            }
        });

        api.addServerJoinListener(event -> {
            try {
                // Check if user is 
                Luma.database.addServer(event.getServer(), DefaultReference.CLARIFAI_CAP, Instant.now());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        DiscordLogger discordLogger = new DiscordLogger();
        api.addMessageDeleteListener(discordLogger);
        api.addMessageEditListener(discordLogger);
    }

    public void sendMessage(long serverId, long channelId, String text, EmbedBuilder embedBuilder){
        api.getServerById(serverId).ifPresent(s -> s.getTextChannelById(channelId).ifPresent(tc -> tc.sendMessage(text, embedBuilder)));
    }

}
