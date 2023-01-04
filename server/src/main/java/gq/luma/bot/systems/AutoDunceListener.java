package gq.luma.bot.systems;

import gq.luma.bot.Luma;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.message.MessageEditEvent;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.event.user.UserChangeNameEvent;
import org.javacord.api.event.user.UserChangeNicknameEvent;
import org.javacord.api.event.user.UserChangeStatusEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.message.MessageEditListener;
import org.javacord.api.listener.server.member.ServerMemberJoinListener;
import org.javacord.api.listener.user.UserChangeNameListener;
import org.javacord.api.listener.user.UserChangeNicknameListener;
import org.javacord.api.listener.user.UserChangeStatusListener;
import org.javacord.api.util.logging.ExceptionLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;

public class AutoDunceListener implements MessageCreateListener, MessageEditListener, ServerMemberJoinListener,
        UserChangeNameListener, UserChangeNicknameListener, UserChangeStatusListener {

    private static final long P2_SERVER_ID = 146404426746167296L;
    private static final long MOD_NOTIFICATIONS_CHANNEL_ID = 432229671711670272L;
    private static final long THE_CORNER_CHANNEL_ID = 357947932139585537L;
    private static final long DUNCE_ROLE_ID = 312324674275115008L;
    private static final long MOD_NOTIFICATIONS_ROLE_ID = 797178777754140722L;

    private HashSet<Pattern> slursRegex = new HashSet<>();

    public AutoDunceListener() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("slurs.txt"))) {
            reader.lines().filter(str -> !str.isEmpty()).map(Pattern::compile).forEach(slursRegex::add);
        }
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        if (!event.getServer().map(s -> s.getId() == P2_SERVER_ID).orElse(false)) {
            return;
        }

        Luma.executorService.submit(() -> event.getMessageAuthor().asUser().ifPresent(user -> {
            // Check if message content contains a slur
            if (containsSlur(event.getMessageContent())) {
                autoDunceUser(user, "slur", "message", event.getMessageContent(),
                        event.getChannel().asServerTextChannel().orElseThrow(AssertionError::new).getMentionTag());
                event.deleteMessage("Slur");
            }

            // Check if message pings 10 or more people
            if (event.getMessage().getMentionedUsers().size() >= 10) {
                autoDunceUser(user, "excessive pings", "message", event.getMessageContent(),
                        event.getChannel().asServerTextChannel().orElseThrow(AssertionError::new).getMentionTag());
            }
        }));
    }

    @Override
    public void onMessageEdit(MessageEditEvent event) {
        if (!event.getServer().map(s -> s.getId() == P2_SERVER_ID).orElse(false)) {
            return;
        }

        Message message = event.getMessage();

        Luma.executorService.submit(() ->
                        message.getAuthor().asUser().ifPresent(user -> {
                            // Check if message content contains a slur
                            if (containsSlur(message.getContent())) {
                                autoDunceUser(user, "slur", "message", message.getContent(),
                                        event.getChannel().asServerTextChannel().orElseThrow(AssertionError::new).getMentionTag());
                                event.deleteMessage("Slur");
                            }
                        }));
    }


    @Override
    public void onUserChangeName(UserChangeNameEvent event) {
        Server p2srServer = event.getApi().getServerById(P2_SERVER_ID).orElseThrow(AssertionError::new);
        if (p2srServer.getNickname(event.getUser()).isPresent()) {
            return;
        }

        Luma.executorService.submit(() -> {
            // Check if name contains a slur
            if (containsSlur(event.getNewName())) {
                autoDunceUser(event.getUser(), "slur", "name", event.getNewName());
            }
        });
    }

    @Override
    public void onUserChangeNickname(UserChangeNicknameEvent event) {
        if (event.getServer().getId() != P2_SERVER_ID) {
            return;
        }

        Luma.executorService.submit(() -> {
            // Check if name contains a slur
            event.getNewNickname().ifPresentOrElse(nickname -> {
                if (containsSlur(nickname)) {
                    autoDunceUser(event.getUser(), "slur", "nickname", nickname);
                }
            }, () -> {
                if (containsSlur(event.getUser().getName())) {
                    autoDunceUser(event.getUser(), "slur", "name", event.getUser().getName());
                }
            });
        });
    }


    @Override
    public void onUserChangeStatus(UserChangeStatusEvent event) {
        Server p2srServer = event.getApi().getServerById(P2_SERVER_ID).orElseThrow(AssertionError::new);

        Luma.executorService.submit(() -> {
            User user = event.requestUser().join();
            if (!p2srServer.getMembers().contains(user)) {
                return;
            }

            // Check if status contains slur
            if (containsSlur(event.getNewDesktopStatus().getStatusString())) {
                autoDunceUser(user, "slur", "status", event.getNewDesktopStatus().getStatusString());
                return;
            }
            if (containsSlur(event.getNewMobileStatus().getStatusString())) {
                autoDunceUser(user, "slur", "status", event.getNewMobileStatus().getStatusString());
                return;
            }
            if (containsSlur(event.getNewWebStatus().getStatusString())) {
                autoDunceUser(user, "slur", "status", event.getNewWebStatus().getStatusString());
            }
        });
    }

    @Override
    public void onServerMemberJoin(ServerMemberJoinEvent event) {
        if (event.getServer().getId() != P2_SERVER_ID) {
            return;
        }

        Luma.executorService.submit(() -> {
            // Check if name contains slur
            if (containsSlur(event.getUser().getName())) {
                autoDunceUser(event.getUser(), "slur", "name", event.getUser().getName());
                return;
            }

            // Check if status contains a slur
            if (containsSlur(event.getUser().getDesktopStatus().getStatusString())) {
                autoDunceUser(event.getUser(), "slur", "status", event.getUser().getDesktopStatus().getStatusString());
                return;
            }
            if (containsSlur(event.getUser().getMobileStatus().getStatusString())) {
                autoDunceUser(event.getUser(), "slur", "status", event.getUser().getMobileStatus().getStatusString());
                return;
            }
            if (containsSlur(event.getUser().getWebStatus().getStatusString())) {
                autoDunceUser(event.getUser(), "slur", "status", event.getUser().getWebStatus().getStatusString());
            }
        });
    }

    public void autoDunceUser(User targetUser, String filter, String source, String offendingContent) {
        this.autoDunceUser(targetUser, filter, source, offendingContent, null);
    }

    public void autoDunceUser(User targetUser, String filter, String source, String offendingContent, String contentReference) {
        if (Luma.database.isDunced(targetUser.getId())) {
            return;
        }

        int dunceUntilRaw = 24;
        String dunceUntilRawUnit = "hours";
        Instant dunceUntil = Instant.now().plus(24, ChronoUnit.HOURS);

        // Notify mod-actions
        TextChannel modActions = targetUser.getApi()
                .getTextChannelById(MOD_NOTIFICATIONS_CHANNEL_ID).orElseThrow(AssertionError::new);
        modActions.sendMessage(new EmbedBuilder()
                .setTitle("AutoDunced " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ").")
                .setDescription("For " + dunceUntilRaw + " " + dunceUntilRawUnit + " (until " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                        .withLocale(Locale.US)
                        .withZone(ZoneId.systemDefault())
                        .format(dunceUntil) + " EST).")
                .addField("Reason", "Detected " + filter + " in " + source)
                .addField("Offending Content", offendingContent));

        // Add this dunce's existing roles to the database
        Luma.database.addDunceStoredRoles(targetUser, targetUser.getApi().getServerById(P2_SERVER_ID).orElseThrow(AssertionError::new));

        // Insert a new instant in the database
        Luma.database.insertUndunceInstant(targetUser.getId(), dunceUntil);

        // Remove user's existing roles
        targetUser.getApi().getServerById(P2_SERVER_ID).orElseThrow(AssertionError::new)
                .getRoles(targetUser)
                .stream().filter(r -> r.getId() != DUNCE_ROLE_ID)
                .forEach(targetUser::removeRole);

        // Add dunce role
        targetUser.addRole(targetUser.getApi().getServerById(P2_SERVER_ID).orElseThrow(AssertionError::new)
                .getRoleById(DUNCE_ROLE_ID).orElseThrow(AssertionError::new)).join();

        // Notify the-corner
        TextChannel theCorner = targetUser.getApi()
                .getTextChannelById(THE_CORNER_CHANNEL_ID).orElseThrow(AssertionError::new);
        Role modNotifs = targetUser.getApi()
                        .getRoleById(MOD_NOTIFICATIONS_ROLE_ID).orElseThrow(AssertionError::new);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ") has been automatically dunced.")
                .setDescription("For " + dunceUntilRaw + " " + dunceUntilRawUnit + " (until " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                        .withLocale(Locale.US)
                        .withZone(ZoneId.systemDefault())
                        .format(dunceUntil) + " EST).")
                .addField("Reason", "Detected " + filter + " in " + source)
                .addField("Offending Content", offendingContent);
        if (contentReference != null) {
            embed.addField("From", contentReference);
        }
        theCorner.sendMessage(modNotifs.getMentionTag() + " " + targetUser.getMentionTag(), embed);
    }

    public boolean containsSlur(String text) {
        for (Pattern p : slursRegex) {
            if (p.matcher(text).matches()) {
                return true;
            }
        }
        return false;
    }
}
