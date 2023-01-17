package gq.luma.bot.services;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.*;
import gq.luma.bot.commands.subsystem.CommandExecutor;
import gq.luma.bot.commands.subsystem.Localization;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.reference.DefaultReference;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.services.apis.GithubApi;
import gq.luma.bot.systems.AutoDunceListener;
import gq.luma.bot.systems.DiscordLogger;
import gq.luma.bot.systems.watchers.SlowMode;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bot implements Service {

    private static final long P2SR_SERVER_ID = 146404426746167296L;
    private static final long DUNCE_ROLE_ID = 312324674275115008L;

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

        api = new DiscordApiBuilder().setToken(KeyReference.discordToken)
                .setWaitForServersOnStartup(true).setWaitForUsersOnStartup(true)
                .setAllIntents().login().join();

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
        executor.registerCommand(new RoleCommands());
        executor.registerCommand(new PinsCommands());
        executor.registerCommand(new DunceCommand());
        executor.registerCommand(new ResourcesCommand());
        executor.registerCommand(new VidCommand());

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
                Luma.database.addServer(event.getServer(), DefaultReference.CLARIFAI_CAP, Instant.now());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        api.addReactionRemoveListener(event -> {

            if (event.getServer().map(s -> s.getId() != 146404426746167296L).orElse(true)) {
                return;
            }

            TextChannel tc = api.getTextChannelById(828380787531186186L).orElseThrow(AssertionError::new);

            Message m = api.getMessageById(event.getMessageId(), event.getChannel()).join();

            String messageField;

            int requiredMessageLength = ("[](" + m.getLink().toString() + ")").length();
            if (m.getContent().length() == 0) {
                messageField = "[*No Content*](" + m.getLink().toString() + ")";
            } else if (m.getContent().length() + requiredMessageLength <= 1024) {
                messageField = "[" + m.getContent() + "](" + m.getLink().toString() + ")";
            } else {
                messageField = "[" + m.getContent().substring(0, 1024 - requiredMessageLength - 5) + " ...](" + m.getLink().toString() + ")";
            }

            String emojiField;

            if (event.getEmoji().isCustomEmoji()) {
                emojiField = event.getEmoji().getMentionTag() + " (" + event.getEmoji().asCustomEmoji().orElseThrow(AssertionError::new).getId() + ")";
            } else {
                emojiField = event.getEmoji().asUnicodeEmoji().orElseThrow(AssertionError::new);
            }

            tc.sendMessage(new EmbedBuilder()
                    .setDescription("Removed Reaction")
                    .addInlineField("User", event.getUser().map(u -> u.getMentionTag() + " (" + u.getDiscriminatedName() + "/" + u.getId() + ")").orElse("No User"))
                    .addInlineField("Emoji", emojiField)
                    .addInlineField("Message", messageField)
                    .addInlineField("Removed at", Instant.now().toString()));
        });

        Role verified = api.getRoleById(558133536784121857L).orElseThrow(AssertionError::new);
        Role unverified = api.getRoleById(804200620869156885L).orElseThrow(AssertionError::new);
        Role dunce = api.getRoleById(312324674275115008L).orElseThrow(AssertionError::new);
        Role speedgaming = api.getRoleById(527595914995695626L).orElseThrow(AssertionError::new);
        Role botsRole = api.getRoleById(175637908156448768L).orElseThrow(AssertionError::new);
        Server p2Server = Bot.api.getServerById(146404426746167296L).orElseThrow(AssertionError::new);

        api.addServerMemberJoinListener(event -> {
            if (event.getServer().getId() == P2SR_SERVER_ID) {
                if (Luma.database.isDunced(event.getUser().getId())) {
                    // Add dunce role
                    event.getServer()
                            .getRoleById(DUNCE_ROLE_ID).orElseThrow(AssertionError::new)
                            .addUser(event.getUser());
                }
            }

            // Check if user was previously verified
            if(Luma.database.getUserVerified(event.getUser().getId()) == 2) {
                // Give user the Verified role
                event.getUser().addRole(api.getRoleById(558133536784121857L).orElseThrow(AssertionError::new));
                if (event.getUser().getRoles(p2Server).contains(unverified)) {
                    event.getUser().removeRole(unverified);
                }
            } else {
                // Give the user the Unverified role
                event.getUser().addRole(api.getRoleById(804200620869156885L).orElseThrow(AssertionError::new));
            }
        });

        // Force update verified once every 15 minutes

        Luma.schedulerService.scheduleWithFixedDelay(() -> Bot.api.getServerById(146404426746167296L)
                .ifPresent(server -> server.getMembers().forEach(member -> {
                    // Check if user was previously verified
                    if(Luma.database.getUserVerified(member.getId()) == 2) {
                        // Give user the Verified role
                        if(!member.getRoles(server).contains(dunce) && !member.getRoles(server).contains(verified)) {
                            member.addRole(verified);
                            if (member.getRoles(server).contains(unverified)) {
                                member.removeRole(unverified);
                            }
                        }
                    } else {
                        // Give the user the Unverified role
                        if(!member.getRoles(server).contains(dunce) && !member.getRoles(server).contains(verified) && !member.getRoles(server).contains(speedgaming) && !member.getRoles(server).contains(botsRole)) {
                            member.addRole(unverified).join();
                        } else {
                            if (member.getRoles(server).contains(unverified)) {
                                member.removeRole(unverified).join();
                            }
                        }

                        // Kick people who have been unverified for more than 24 hours
                        if (member.getRoles(server).contains(unverified) && member.getJoinedAtTimestamp(server).map(ins -> ins.isBefore(Instant.now().minus(1, ChronoUnit.DAYS))).orElse(false)) {
                            logger.info("Kicking user after 24hrs: " + member.getDiscriminatedName());
                            server.kickUser(member, "Joined for 24 hours without verifying.");
                        }
                    }
                })),
                0, 15, TimeUnit.MINUTES);

        /*api.addReactionAddListener(event -> event.getServer().ifPresent(server -> {
            if (server.getId() == 942872856893751397L && event.getMessageId() == 942874482941517945L) {
                server.getRoleById(943542972354543631L).ifPresent(role ->
                        event.requestUser().join().addRole(role));
            }
        }));*/

        Pattern p2IssuePattern = Pattern.compile("(?<repo>\\w*)#(?<issue>\\d+)");
        HashMap<String, String> p2RepoShorthand = new HashMap<>();
        p2RepoShorthand.put("sar", "SourceAutoRecord");
        p2RepoShorthand.put("conf", "srconfigs");
        p2RepoShorthand.put("wh", "wormhole");
        p2RepoShorthand.put("sm", "Portal2SpeedrunMod");

        api.addMessageCreateListener(event -> {
            if (event.getServer().map(s -> s.getId() == 146404426746167296L).orElse(false)) {
                String content = event.getMessageContent();

                Matcher m = p2IssuePattern.matcher(content);

                ArrayList<CompletableFuture<Void>> embedsFutures = new ArrayList<>();
                ArrayList<EmbedBuilder> embeds = new ArrayList<>();

                GithubApi githubApi = GithubApi.get();

                while (m.find()) {
                    String repo = null;
                    if (m.group("repo") == null || m.group("repo").isEmpty()) {
                        repo = "SourceAutoRecord";
                    } else {
                        if (p2RepoShorthand.containsKey(m.group("repo"))) {
                            repo = p2RepoShorthand.get(m.group("repo"));
                        } else {
                            continue;
                        }
                    }

                    String issueNumber = m.group("issue");

                    embedsFutures.add(githubApi.fetchIssue(repo, m.group("issue"))
                            .thenApply(issue -> {
                                //logger.info("Reached block on issue {}", issue.getId());
                                try {
                                    return new EmbedBuilder()
                                            .setTitle(issue.getRepository().getOwnerName() + "/" + issue.getRepository().getName() + " - Issue #" + issueNumber)
                                            .setUrl(issue.getHtmlUrl().toString())
                                            .setAuthor(issue.getUser().getLogin(), issue.getUser().getHtmlUrl().toString(), issue.getUser().getAvatarUrl())
                                            .setColor(new Color(0, 255, 242))
                                            .addField(issue.getTitle(), StringUtils.substring(issue.getBody(), 0, 250) + "...");
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .thenAccept(embeds::add)
                            .exceptionally(t -> {
                                //t.printStackTrace();
                                return null;
                            }));
                }

                CompletableFuture.allOf(embedsFutures.toArray(new CompletableFuture[0])).join();

                if (!embeds.isEmpty()) {
                    MessageBuilder builder = new MessageBuilder();

                    for (EmbedBuilder embed : embeds) {
                        builder.addEmbed(embed);
                    }

                    builder.replyTo(event.getMessage()).send(event.getChannel()).join();
                }
            }
        });

        DiscordLogger discordLogger = new DiscordLogger();
        api.addMessageDeleteListener(discordLogger);
        api.addMessageEditListener(discordLogger);

        AutoDunceListener autoDunceListener = new AutoDunceListener();
        api.addMessageCreateListener(autoDunceListener);
        api.addMessageEditListener(autoDunceListener);
        api.addServerMemberJoinListener(autoDunceListener);
        api.addUserChangeNameListener(autoDunceListener);
        api.addUserChangeNicknameListener(autoDunceListener);
        api.addUserChangeStatusListener(autoDunceListener);
    }

    public void sendMessage(long serverId, long channelId, String text, EmbedBuilder embedBuilder){
        api.getServerById(serverId).flatMap(s -> s.getTextChannelById(channelId)).ifPresent(tc -> tc.sendMessage(text, embedBuilder));
    }

}
