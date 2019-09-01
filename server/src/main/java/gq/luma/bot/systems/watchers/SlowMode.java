package gq.luma.bot.systems.watchers;


import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.logging.ExceptionLogger;

import java.util.HashMap;
import java.util.Map;

public class SlowMode implements MessageCreateListener {
    public static Map<Long, Integer> slowModes = new HashMap<>();
    private static Map<Long, HashMap<Long, Long>> latestUserMessage = new HashMap<>();

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        event.getMessage().getAuthor().asUser().ifPresent(user -> event.getMessage().getServer().ifPresent(server -> {
            if(user.getRoles(server).stream().noneMatch(r -> r.getName().equalsIgnoreCase("Serious"))) {
                if (!latestUserMessage.containsKey(event.getChannel().getId()))
                    latestUserMessage.put(event.getChannel().getId(), new HashMap<>());
                if (!latestUserMessage.get(event.getChannel().getId()).containsKey(event.getMessage().getAuthor().getId()))
                    latestUserMessage.get(event.getChannel().getId()).put(event.getMessage().getAuthor().getId(), 0L);
                if (slowModes.containsKey(event.getChannel().getId()) && (System.currentTimeMillis() - latestUserMessage.get(event.getChannel().getId()).get(event.getMessage().getAuthor().getId())) < slowModes.get(event.getChannel().getId())) {
                    System.out.println("Deleting due to " + (System.currentTimeMillis() - latestUserMessage.get(event.getChannel().getId()).get(event.getMessage().getAuthor().getId())));
                    event.getMessage().delete().exceptionally(ExceptionLogger.get());
                } else {
                    Map<Long, Long> userHm = latestUserMessage.get(event.getChannel().getId());
                    if (userHm == null) {
                        System.err.println("userhm is null.");
                        return;
                    }
                    userHm.put(event.getMessage().getAuthor().getId(), System.currentTimeMillis());
                }
            }
        }));
    }
}
