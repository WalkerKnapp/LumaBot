package gq.luma.bot.commands.subsystem;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.Optional;

public class CommandEvent {
    private DiscordApi api;
    private CommandExecutor executor;
    private Localization localization;

    private String commandRemainder;
    private String[] commandArgs;

    private Message message;
    private TextChannel channel;
    private Optional<Server> server;
    private User user;

    public CommandEvent(DiscordApi api, CommandExecutor executor, Localization localization, String commandRemainder, String[] commandArgs, Message message, TextChannel textChannel, Optional<Server> server, User author) {
        this.api = api;
        this.executor = executor;
        this.localization = localization;
        this.commandRemainder = commandRemainder;
        this.commandArgs = commandArgs;
        this.message = message;
        this.channel = textChannel;
        this.server = server;
        this.user = author;
    }

    public DiscordApi getApi() {
        return api;
    }

    public CommandExecutor getExecutor(){
        return executor;
    }

    public String getCommandRemainder(){
        return this.commandRemainder;
    }

    public String[] getCommandArgs(){
        return this.commandArgs;
    }

    public Message getMessage() {
        return message;
    }

    public TextChannel getChannel() {
        return channel;
    }

    public Optional<Server> getServer() {
        return server;
    }

    public Localization getLocalization() {
        return localization;
    }

    public User getAuthor() {
        return user;
    }
}
