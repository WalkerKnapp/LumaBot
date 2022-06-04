package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.services.SkillRoleService;
import gq.luma.bot.services.apis.IVerbApi;
import gq.luma.bot.systems.watchers.SlowMode;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageSet;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.util.logging.ExceptionLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ServerCommands {
    private Logger logger = LoggerFactory.getLogger(ServerCommands.class);

    @Command(aliases = {"slow"}, description = "slow_description", usage = "slow_usage", neededPerms = "SET_SLOW_MODE", whilelistedGuilds = "146404426746167296;425386024835874826")
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

    @Command(aliases = {"lblisting"}, description = "lblisting_description", usage = "lblisting_usage", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296;425386024835874826")
    public void onLbListing(CommandEvent event) {
        if (event.getCommandArgs().length > 0) {


            for(int i = 0; i < SkillRoleService.IVERB_MAP_IDS.length; i++) {
                StringBuilder sb = new StringBuilder();
                sb.append("Map: ").append(i).append('\n');
                for(Integer time : Luma.skillRoleService.scores.get(SkillRoleService.IVERB_MAP_IDS[i]).keySet()) {
                    ArrayList<IVerbApi.ScoreMetadata> scores = Luma.skillRoleService.scores.get(SkillRoleService.IVERB_MAP_IDS[i]).get(time);
                    for (IVerbApi.ScoreMetadata m : scores) {
                        if (m.steamId == Long.parseLong(event.getCommandArgs()[0])) {
                            sb.append("\tTime: ").append(time);
                        }
                    }
                }
                sb.append('\n');
                event.getChannel().sendMessage(sb.toString());
            }
        }
    }

    @Command(aliases = {"ipcheck"}, description = "ipcheck_description", usage = "ipcheck_usage", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public void onIpCheck(CommandEvent event) {
        int ipCount = Luma.database.ipCount();

        Message m = event.getChannel()
                .sendMessage("Checking verified IPs for abuse: 0/" + ipCount + "...")
                .join();

        Luma.executorService.submit(() -> {
            try {
                AtomicInteger i = new AtomicInteger(1);
                HashMap<Long, Set<String>> userEvils = new HashMap<>();
                Luma.database.allIps((userId, ip) -> {
                    if (i.get() % 50 == 0) {
                        m.edit("Checking verified IPs for abuse: " + i.get() + "/" + ipCount + "...")
                                .join();
                    }

                    List<String> evils = Luma.evilsService.checkEvil(ip);
                    if (!evils.isEmpty()) {
                        userEvils.computeIfAbsent(userId, id -> new HashSet<>())
                                .addAll(evils);
                    }

                    i.getAndIncrement();
                });

                m.edit("Mapping IPs to users 0/" + userEvils.size())
                        .join();

                HashMap<User, Set<String>> evilsMapped = new HashMap<>();

                int j = 1;
                for (var entry : userEvils.entrySet()) {
                    if (j % 50 == 0) {
                        m.edit("Mapping IPs to users " + j + "/" + userEvils.size())
                                .join();
                    }

                    evilsMapped.put(event.getApi().getUserById(entry.getKey()).join(), entry.getValue());

                    j++;
                }

                m.edit("Buffering output...").join();

                StringBuilder messageBuffer = new StringBuilder("The following users verified with IP addresses marked as abusive:\n");

                List<Map.Entry<User, Set<String>>> sortedEvils = evilsMapped.entrySet().stream().sorted(Comparator.comparing(u -> u.getKey().getDiscriminatedName(), Comparator.naturalOrder()))
                        .collect(Collectors.toList());

                for (var entry : sortedEvils) {
                    StringBuilder sb = new StringBuilder(entry.getKey().getMentionTag() + "(" + entry.getKey().getDiscriminatedName() + ")\n");

                    for (var evil : entry.getValue()) {
                        sb.append("\t- `").append(evil).append("`\n");
                    }

                    if (messageBuffer.length() + sb.length() > 2000) {
                        event.getChannel().sendMessage(messageBuffer.toString()).join();
                        messageBuffer = new StringBuilder();
                    }

                    messageBuffer.append(sb);
                }

                if (messageBuffer.length() > 0) {
                    event.getChannel().sendMessage(messageBuffer.toString()).join();
                }

                m.delete().join();
            } catch (Throwable t) {
                logger.error("Failed to complete iplist", t);
            }
        });
    }

    @Command(aliases = {"cleanup"}, description = "cleanup_description", usage = "cleanup_usage", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296;425386024835874826;902666492817072158")
    public void onCleanup(CommandEvent event){
        if(event.getCommandArgs().length >= 1){
            int messageCount = Math.max(Integer.valueOf(event.getCommandArgs()[0]), 0);
            MessageSet messages = event.getChannel().getMessages(messageCount).join();
            Message.delete(event.getApi(), messages.stream().filter(m -> !m.isPinned()).collect(Collectors.toList())).join();
        } else {
            event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage(event.getLocalization().get("not_enough_arguments"), event.getLocalization()));
        }
    }

    @Command(aliases = {"role", "roles"}, description = "role_description", usage = "")
    public EmbedBuilder onRole(CommandEvent event) throws SQLException {
        if(event.getServer().isPresent()) {
            User user = event.getAuthor();
            Server server = event.getServer().get();

            if (event.getChannel().getId() == 586288833771995166L) {
                return null;
            }

            if (event.getCommandArgs().length >= 1) {
                Optional<Long> longOptional = Luma.database.getRoleByName(server.getId(), event.getCommandRemainder());
                System.out.println("LongOptional isPresent: " + longOptional.isPresent());
                Optional<Role> roleOptional = longOptional.flatMap(event.getApi()::getRoleById);
                System.out.println("RoleOptional isPresent: " + roleOptional.isPresent());
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

    @Command(aliases = {"help"}, parent = "vote", description = "vote_description", usage = "", neededPerms = "VOTES")
    public EmbedBuilder onVote(CommandEvent event) {
        return new EmbedBuilder()
                .setColor(BotReference.LUMA_COLOR)
                .addField(event.getPrefix() + " vote keys get", "See all of the keys used for voting.")
                .addField(event.getPrefix() + " vote keys add", "Add new keys to this server's key pool.")
                .addField(event.getPrefix() + " vote keys clear", "Clears all keys for this server.")
                .addField(event.getPrefix() + " vote keys remove", "Remove keys when applicable.")
                .addField(event.getPrefix() + " vote start", "Starts the vote in the current channel or a specified channel.");
    }

    @Command(aliases = {"get"}, parent = "vote keys", description = "keys_description", usage = "", neededPerms = "VOTES")
    public EmbedBuilder onKeys(CommandEvent event) {
        if(event.getServer().isPresent()) {
            List<String> keys = Luma.database.getVotingKeysByServer(event.getServer().get().getId());
            byte[] file = new byte[keys.stream().map(String::getBytes).mapToInt(bytes -> bytes.length + 2).sum()];
            int i = 0;
            for(String str : keys) {
                byte[] bytes = str.getBytes();
                System.arraycopy(bytes, 0, file, i, bytes.length);
                i += bytes.length;
                file[i++] = '\r';
                file[i++] = '\n';
            }
            event.getAuthor().sendMessage(new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle("Keys for " + event.getServer().get().getName()), new ByteArrayInputStream(file), "keys.txt");
            return new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle("Keys for " + event.getServer().get().getName())
                    .addField("Key Count", String.valueOf(keys.size()));
        } else {
            return EmbedUtilities.getErrorMessage("vote_servers_only", event.getLocalization());
        }
    }

    @Command(aliases = {"add"}, parent = "vote keys", description = "keys_add_description", usage = "", neededPerms = "VOTES")
    public EmbedBuilder onAdd(CommandEvent event) {
        if(event.getServer().isPresent()) {
            long serverId = event.getServer().get().getId();
            AtomicInteger added = new AtomicInteger();
            for(String arg : event.getCommandArgs()) {
                Luma.database.insertVotingKey(arg, serverId);
                added.getAndIncrement();
            }

            for(MessageAttachment attachment : event.getMessage().getAttachments()) {
                try {
                    new BufferedReader(new InputStreamReader(attachment.downloadAsInputStream())).lines().forEach(str -> {
                        Luma.database.insertVotingKey(str, serverId);
                        added.getAndIncrement();
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            int newCount = Luma.database.countVotingKeysByServer(serverId);

            return new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle("Added keys for " + event.getServer().get().getName())
                    .addField("Added Keys", String.valueOf(added.get()))
                    .addField("Total Keys", String.valueOf(newCount));
        } else {
            return EmbedUtilities.getErrorMessage("vote_servers_only", event.getLocalization());
        }
    }

    @Command(aliases = {"remove"}, parent = "vote keys", description = "keys_remove_description", usage = "", neededPerms = "VOTES")
    public EmbedBuilder onRemove(CommandEvent event) {
        if(event.getServer().isPresent()) {
            long serverId = event.getServer().get().getId();
            AtomicInteger removed = new AtomicInteger();
            for(String arg : event.getCommandArgs()) {
                Luma.database.removeVotingKey(arg, serverId);
                removed.getAndIncrement();
            }

            for(MessageAttachment attachment : event.getMessage().getAttachments()) {
                try {
                    new BufferedReader(new InputStreamReader(attachment.downloadAsInputStream())).lines().forEach(str -> {
                        Luma.database.removeVotingKey(str, serverId);
                        removed.getAndIncrement();
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            int newCount = Luma.database.countVotingKeysByServer(serverId);

            return new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle("Removed keys for " + event.getServer().get().getName())
                    .addField("Removed Keys", String.valueOf(removed.get()))
                    .addField("Total Keys", String.valueOf(newCount));
        } else {
            return EmbedUtilities.getErrorMessage("vote_servers_only", event.getLocalization());
        }
    }

    @Command(aliases = {"clear"}, parent = "vote keys", description = "keys_clear_description", usage = "", neededPerms = "VOTES")
    public EmbedBuilder onClear(CommandEvent event) {
        if(event.getServer().isPresent()) {
            long serverId = event.getServer().get().getId();

            int oldCount = Luma.database.countVotingKeysByServer(serverId);
            Luma.database.clearVotingKeys(serverId);

            return new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle("Cleared all keys for " + event.getServer().get().getName())
                    .addField("Removed Keys", String.valueOf(oldCount));
        } else {
            return EmbedUtilities.getErrorMessage("vote_servers_only", event.getLocalization());
        }
    }

    private Map<ServerTextChannel, CompletableFuture<Void>> channelVoteRunners = new HashMap<>();

    @Command(aliases = {"start"}, parent = "vote", description = "vote_start_description", usage = "", neededPerms = "VOTES")
    public EmbedBuilder onChannelVote(CommandEvent event) {
        if(event.getServer().isPresent()){
            Server server = event.getServer().get();
            ServerTextChannel textChannel;
            if(event.getMessage().getMentionedChannels().size() > 0) {
                textChannel = event.getMessage().getMentionedChannels().get(0);
            } else {
                textChannel = event.getChannel().asServerTextChannel().orElseThrow();
            }

            if(channelVoteRunners.get(textChannel) != null && !channelVoteRunners.get(textChannel).isDone()) {
                return new EmbedBuilder().setColor(BotReference.LUMA_COLOR).addField("Please wait for the totals to be added.", "Error: Cannot run multiple simultaneous counts in one channel.");
            } else {
                CompletableFuture<Message> message = event.getChannel().sendMessage(new EmbedBuilder().setColor(BotReference.LUMA_COLOR)
                        .setTitle("Counting Votes..."));
                channelVoteRunners.put(textChannel, CompletableFuture.runAsync(() -> {
                    List<User> users = textChannel.getMessagesAsStream()
                            .map(Message::getAuthor)
                            .map(MessageAuthor::asUser)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .distinct()
                            .collect(Collectors.toList());
                    List<String> keys = Luma.database.getVotingKeysByServer(server.getId());
                    if(users.size() <= keys.size()) {
                        message.join().edit(new EmbedBuilder().setColor(BotReference.LUMA_COLOR)
                                .setTitle("Finished counting votes!")
                                .addField("Total Users", String.valueOf(users.size())));
                        Collections.shuffle(keys);
                        for(int i = 0; i < users.size(); i++) {
                            users.get(i).sendMessage(new EmbedBuilder()
                                    .setColor(BotReference.LUMA_COLOR)
                                    .setTitle(server.getName() + " Vote")
                                    .addField("Key", keys.get(i))).exceptionally(ExceptionLogger.get());
                        }
                    } else {
                        message.join().edit(new EmbedBuilder().setColor(Color.RED)
                                .setTitle("Not enough keys for the users.")
                                .addField("Total Users", String.valueOf(users.size()))
                                .addField("Total Keys", String.valueOf(keys.size())));
                    }
                }));
            }

        }
        return null;
    }

    @Command(aliases = {"count"}, parent = "vote", description = "vote_count_description", usage = "", neededPerms = "VOTES")
    public EmbedBuilder onVoteCount(CommandEvent event) {
        if(event.getServer().isPresent()){
            ServerTextChannel textChannel;
            if(event.getMessage().getMentionedChannels().size() > 0) {
                textChannel = event.getMessage().getMentionedChannels().get(0);
            } else {
                textChannel = event.getChannel().asServerTextChannel().orElseThrow();
            }

            if(channelVoteRunners.get(textChannel) != null && !channelVoteRunners.get(textChannel).isDone()) {
                return new EmbedBuilder().setColor(BotReference.LUMA_COLOR).addField("Please wait for the totals to be added.", "Error: Cannot run multiple simultaneous counts in one channel.");
            } else {
                CompletableFuture<Message> message = event.getChannel().sendMessage(new EmbedBuilder().setColor(BotReference.LUMA_COLOR)
                        .setTitle("Counting Votes..."));
                channelVoteRunners.put(textChannel, CompletableFuture.runAsync(() -> {
                    List<User> users = textChannel.getMessagesAsStream()
                            .map(Message::getAuthor)
                            .map(MessageAuthor::asUser)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .distinct()
                            .collect(Collectors.toList());
                    message.join().edit(new EmbedBuilder().setColor(BotReference.LUMA_COLOR)
                                .setTitle("Finished counting users!")
                                .addField("Total Users", String.valueOf(users.size())));
                }));
            }

        }
        return null;
    }
}
