package gq.luma.bot.commands.subsystem;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.permissions.PermissionSet;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.util.logging.ExceptionLogger;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class CommandExecutor {
    private final ArrayList<MCommand> commands;
    private final Map<String, Localization> localizations;

    public CommandExecutor(DiscordApi api){
        this.commands = new ArrayList<>();
        this.localizations = new HashMap<>();

        api.addMessageCreateListener(messageCreateEvent ->  {
            //System.out.println("Got Event! Content: " + messageCreateEvent.getMessage().getContent());
            if(messageCreateEvent.getMessage().getAuthor().asUser().isPresent() && !messageCreateEvent.getMessage().getAuthor().asUser().get().isBot() && !messageCreateEvent.getMessage().getAuthor().asUser().get().isYourself()) {
                String[] split = messageCreateEvent.getMessage().getContent().split("\\s+");
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
                try {
                    String loc = Luma.database.getEffectiveLocale(event.getChannel());
                    Localization localization = getLocalization(loc);
                    for (String name : command.getCommand().aliases()) {
                        String expectedCommand = generateCommandString(command, event.getChannel(), localization, name);
                        String[] expectedTree = expectedCommand.split("\\s+");
                        if (equalsUpToFirst(expectedTree, split)) {
                            execute(command, expectedCommand, expectedTree, split, event, localization, user);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error thrown while executing command: ");
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean isUnderWhitelist(MCommand command, MessageCreateEvent event){
        if(command.getCommand().whilelistedGuilds().isEmpty())
            return true;
        if(event.getServer().isEmpty())
            return false;
        for(String guildId : command.getCommand().whilelistedGuilds().split(";")){
            if(guildId.equals(event.getServer().get().getIdAsString())){
                return true;
            }
        }
        return false;
    }

    public boolean canRun(MCommand command, User user, Optional<Server> serverO){
        try {
            if (!command.getCommand().neededPerms().isEmpty()) {
                PermissionSet.Permission neededPermission = PermissionSet.Permission.valueOf(command.getCommand().neededPerms());
                if (neededPermission == PermissionSet.Permission.DEVELOPER) {
                    for (PermissionSet checkPerm : Luma.database.getPermissionByTargetId(user.getId())) {
                        if (checkPerm.effectivelyHasPermission(neededPermission)) {
                            return true;
                        }
                    }
                }
                if (serverO.isPresent()) {
                    Server server = serverO.get();
                    //Check owner perms
                    if (server.isOwner(user)) {
                        for (PermissionSet checkPerm : Luma.database.getPermission(server, PermissionSet.PermissionTarget.OWNER, 0L)) {
                            if (checkPerm.effectivelyHasPermission(neededPermission)) {
                                return true;
                            }
                        }
                    }
                    //Check user perms
                    for (PermissionSet checkPerm : Luma.database.getPermission(server, PermissionSet.PermissionTarget.USER, user.getId())) {
                        if (checkPerm.effectivelyHasPermission(neededPermission)) {
                            return true;
                        }
                    }
                    for (Role checkRole : server.getRoles(user)) {
                        //Check role perms
                        for (PermissionSet checkPerm : Luma.database.getPermission(server, PermissionSet.PermissionTarget.ROLE, checkRole.getId())) {
                            if (checkPerm.effectivelyHasPermission(neededPermission)) {
                                return true;
                            }
                        }
                        //Check discord perms
                        for (PermissionType checkType : checkRole.getAllowedPermissions()) {
                            for (PermissionSet checkPerm : Luma.database.getPermission(server, PermissionSet.PermissionTarget.PERMISSION, checkType.getValue())) {
                                if (checkPerm.effectivelyHasPermission(neededPermission)) {
                                    return true;
                                }
                            }
                        }

                    }
                }
                return false;
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void execute(MCommand command, String commandUsed, String[] commandTree, String[] messageSplit, MessageCreateEvent event, Localization localization , User user) {
        int substringIndex = commandUsed.length() + 1;
        String content = "";
        if(substringIndex < event.getMessage().getContent().length()){
            content = event.getMessage().getContent().substring(substringIndex);
        }
        CommandEvent commandEvent = new CommandEvent(event.getApi(),
                localization,
                content,
                Arrays.copyOfRange(messageSplit, commandTree.length, messageSplit.length),
                event.getMessage(),
                event.getChannel(),
                Optional.ofNullable(event.getChannel().asServerTextChannel().isPresent() ? event.getChannel().asServerTextChannel().get().getServer() : null),
                user);

        System.out.println("Invoking!");
        Luma.executorService.submit(() -> {
            System.out.println("Started execution of command: " + event.getMessage().getContent());
            try {
                Object ret = command.getMethod().invoke(command.getCaller(), commandEvent);
                if (ret instanceof EmbedBuilder) {
                    System.out.println("Sending embed builder...");
                    event.getChannel().sendMessage((EmbedBuilder) ret).exceptionally(ExceptionLogger.get());
                } else if (ret instanceof String) {
                    System.out.println("Sending string...");
                    event.getChannel().sendMessage((String) ret).exceptionally(ExceptionLogger.get());
                }
            } catch (Exception e){
                System.err.println("Encountered error while executing command:");
                e.printStackTrace();
            }
            System.out.println("Finished execution of command: " + event.getMessage().getContent());
        });
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
            expectedCommand = Luma.database.getEffectivePrefix(context) + " " + localization.get(name + "_command");
        }
        else {
            expectedCommand = Luma.database.getEffectivePrefix(context) + " " + Arrays.stream(command.getCommand().parent().split(" ")).map(str -> localization.get(str + "_command")).collect(Collectors.joining(" ")) + " " + localization.get(name + "_command");
        }
        return expectedCommand;
    }
}
