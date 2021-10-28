package gq.luma.bot.systems;

import gq.luma.bot.Luma;
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
import java.util.HashSet;
import java.util.regex.Pattern;

public class AutoDunceListener implements MessageCreateListener, MessageEditListener, ServerMemberJoinListener,
        UserChangeNameListener, UserChangeNicknameListener, UserChangeStatusListener {

    private final long P2_SERVER_ID = 146404426746167296L;

    private HashSet<Pattern> slursRegex = new HashSet<>();

    public AutoDunceListener() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("slurs.txt"))) {
            reader.lines().map(Pattern::compile).forEach(slursRegex::add);
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
                autoDunceUser(user);
            }
        }));
    }

    @Override
    public void onMessageEdit(MessageEditEvent event) {
        if (!event.getServer().map(s -> s.getId() == P2_SERVER_ID).orElse(false)) {
            return;
        }

        Luma.executorService.submit(() -> event.requestMessage().thenAccept(message ->
                        message.getAuthor().asUser().ifPresent(user -> {
                            // Check if message content contains a slur
                            if (containsSlur(message.getContent())) {
                                autoDunceUser(user);
                            }
                        })).exceptionally(ExceptionLogger.get()));
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
                autoDunceUser(event.getUser());
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
            if (containsSlur(event.getNewNickname().orElse(event.getUser().getName()))) {
                autoDunceUser(event.getUser());
            }
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
                autoDunceUser(user);
                return;
            }
            if (containsSlur(event.getNewMobileStatus().getStatusString())) {
                autoDunceUser(user);
                return;
            }
            if (containsSlur(event.getNewWebStatus().getStatusString())) {
                autoDunceUser(user);
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
                autoDunceUser(event.getUser());
                return;
            }

            // Check if status contains a slur
            if (containsSlur(event.getUser().getDesktopStatus().getStatusString())) {
                autoDunceUser(event.getUser());
                return;
            }
            if (containsSlur(event.getUser().getMobileStatus().getStatusString())) {
                autoDunceUser(event.getUser());
                return;
            }
            if (containsSlur(event.getUser().getWebStatus().getStatusString())) {
                autoDunceUser(event.getUser());
            }
        });
    }

    public void autoDunceUser(User user) {
        // TODO: Impl
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
