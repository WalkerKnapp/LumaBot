package gq.luma.bot.systems;

import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoDunceListener implements MessageCreateListener {

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
