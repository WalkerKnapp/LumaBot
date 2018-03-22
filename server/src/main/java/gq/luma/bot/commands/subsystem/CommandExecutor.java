package gq.luma.bot.commands.subsystem;

import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.channels.TextChannel;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import de.btobastian.javacord.events.message.MessageCreateEvent;
import gq.luma.bot.Luma;
import gq.luma.bot.services.Database;
import gq.luma.bot.utils.StringUtilities;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class CommandExecutor {
    private ArrayList<MCommand> commands;
    private Map<String, Localization> localizations;

    public CommandExecutor(DiscordApi api){
        this.commands = new ArrayList<>();
        this.localizations = new HashMap<>();

        api.addMessageCreateListener(messageCreateEvent -> {
            //System.out.println("Got Event!");
            if(messageCreateEvent.getMessage().getAuthor().asUser().isPresent() && !messageCreateEvent.getMessage().getAuthor().asUser().get().isBot() && !messageCreateEvent.getMessage().getAuthor().asUser().get().isYourself()) {
                String[] split = StringUtilities.splitString(messageCreateEvent.getMessage().getContent());
                if (split.length > 0) {
                    commands.forEach(mCommand -> attemptExecute(mCommand, split, messageCreateEvent));
                }
            }
        });
    }

    public void registerCommand(Object caller){
        Arrays.stream(caller.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Command.class))
                .map(method-> new MCommand(method.getAnnotation(Command.class), caller, method))
                .forEach(commands::add);
    }

    private void attemptExecute(MCommand command, String[] split, MessageCreateEvent event){
        event.getMessage().getAuthor().asUser().ifPresent(user -> {
            if(canRun(command, user, event.getServer()) && isUnderWhitelist(command, event)) {
                Luma.lumaExecutorService.submit(() -> {
                    try {
                        String loc = Database.getEffectiveLocale(event.getChannel());
                        Localization localization = getLocalization(loc);
                        for (String name : command.getCommand().aliases()) {
                            String expectedCommand = generateCommandString(command, event.getChannel(), localization, name);
                            String[] expectedTree = StringUtilities.splitString(expectedCommand);
                            if (equalsUpToFirst(expectedTree, split)) {
                                execute(command, expectedCommand, expectedTree, split, event, localization, user);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error thrown while executing command: ");
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    private boolean isUnderWhitelist(MCommand command, MessageCreateEvent event){
        if(command.getCommand().whilelistedGuilds().isEmpty())
            return true;
        if(!event.getServer().isPresent())
            return false;
        for(String guildId : command.getCommand().whilelistedGuilds().split(";")){
            if(guildId.equals(event.getServer().get().getIdAsString())){
                return true;
            }
        }
        return false;
    }

    public boolean canRun(MCommand command, User user, Optional<Server> serverO){
        AtomicBoolean hasPerms = new AtomicBoolean(true);
        if (!command.getCommand().neededGuildPerms().isEmpty()) {
            serverO.ifPresent(server -> {
                try {
                    Database.getServerPermsForUser(server, user).ifPresentOrElse(list -> {
                        if(hasPermission(list, command.getCommand().neededGuildPerms())){
                            hasPerms.set(true);
                        } else {
                            hasPerms.set(false);
                        }
                    }, () -> hasPerms.set(false));
                } catch (SQLException e) {
                    e.printStackTrace();
                    hasPerms.set(false);
                }
            });
        }
        if(!command.getCommand().neededGlobalPerms().isEmpty()){
            try{
                Database.getGlobalPermsForUser(user).ifPresentOrElse(list -> {
                    if(hasPermission(list, command.getCommand().neededGuildPerms())){
                        hasPerms.set(true);
                    } else {
                        hasPerms.set(false);
                    }
                }, () -> hasPerms.set(false));
            } catch (SQLException e){
                e.printStackTrace();
                hasPerms.set(false);
            }
        }
        return hasPerms.get();
    }

    private void execute(MCommand command, String commandUsed, String[] commandTree, String[] messageSplit, MessageCreateEvent event, Localization localization , User user) throws Exception {
        int substringIndex = commandUsed.length() + 1;
        String content = "";
        if(substringIndex < event.getMessage().getContent().length()){
            content = event.getMessage().getContent().substring(substringIndex);
        }
        CommandEvent commandEvent = new CommandEvent(event.getApi(),
                this,
                localization,
                content,
                Arrays.copyOfRange(messageSplit, commandTree.length, messageSplit.length),
                event.getMessage(),
                event.getChannel(),
                Optional.ofNullable(event.getChannel().asServerTextChannel().isPresent() ? event.getChannel().asServerTextChannel().get().getServer() : null),
                user);

        //System.out.println("Invoking!");
        Object ret = command.getMethod().invoke(command.getCaller(), commandEvent);
        if (ret instanceof EmbedBuilder) {
            event.getChannel().sendMessage((EmbedBuilder) ret);
        } else if (ret instanceof String) {
            event.getChannel().sendMessage((String) ret);
        }
    }

    private boolean equalsUpToFirst(String[] array1, String[] array2){
        if(array1.length > array2.length){
            return false;
        }
        else{
            for(int i = 0; i < array1.length; i++){
                if(!array1[i].equalsIgnoreCase(array2[i]))
                    return false;
            }
            return true;
        }
    }

    public void setLocalization(String language, Localization localization){
        this.localizations.put(language, localization);
    }

    public Localization getLocalization(String name){
        return localizations.get(name);
    }

    public String generateCommandString(MCommand command, TextChannel context, Localization localization, String name) throws SQLException {
        String expectedCommand;
        if(command.getCommand().parent().isEmpty()) {
            expectedCommand = Database.getEffectivePrefix(context) + " " + localization.get(name + "_command");
        }
        else {
            expectedCommand = Database.getEffectivePrefix(context) + " " + Arrays.stream(command.getCommand().parent().split(" ")).map(str -> localization.get(str + "_command")).collect(Collectors.joining(" ")) + " " + localization.get(name + "_command");
        }
        return expectedCommand;
    }

    public ArrayList<MCommand> getCommands() {
        return commands;
    }

    private boolean hasPermission(List<String> perms, String permission) {
        if (permission.equals("none") || permission.equals("")) {
            return true;
        }
        if (perms == null) {
            return false;
        }
        for (String perm : perms) {
            if (checkPermission(perm, permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkPermission(String has, String required) {
        String[] splitHas = has.split("\\.");
        String[] splitRequired = required.split("\\.");
        int lower = splitHas.length > splitRequired.length ? splitRequired.length : splitHas.length;
        for (int i = 0; i < lower; i++) {
            if (!splitHas[i].equalsIgnoreCase(splitRequired[i])) {
                return splitHas[i].equals("*");
            }
        }
        return splitRequired.length == splitHas.length;
    }
}
