package gq.luma.bot.services;

import com.eclipsesource.json.JsonObject;
import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import gq.luma.bot.Luma;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.commands.subsystem.Localization;
import gq.luma.bot.services.node.Task;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class TaskScheduler implements Service {

    private static Queue<Task> localQueue = new ConcurrentLinkedQueue<>();
    private static Map<Task, CompletableFuture<JsonObject>> callbacks = new HashMap<>();
    private static boolean isExecuting = false;

    @Override
    public void startService() {
        Luma.lumaExecutorService.scheduleWithFixedDelay(() -> {
            if(!localQueue.isEmpty() && (!BotReference.USE_NODES || Luma.nodeServer.hasOpenNode()) && (BotReference.USE_NODES || !isExecuting)) {
                getFirstMatching(localQueue, t -> !t.isRendering()).ifPresent(task -> Luma.nodeServer.openNode().scheduleTask(task)
                        .thenAccept(val -> callbacks.get(task).complete(val))
                        .exceptionally(e -> {
                            callbacks.get(task).completeExceptionally(e);
                            return null;
                        })
                        .thenRun(() -> {
                            localQueue.remove(task);
                            callbacks.remove(task);
                        }));
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public static CompletableFuture<JsonObject> scheduleTask(Task task){
        CompletableFuture<JsonObject> cf = new CompletableFuture<>();
        callbacks.put(task, cf);
        localQueue.add(task);
        return cf;
    }

    public static EmbedBuilder generateStatusEmbed(Localization loc, DiscordApi api){
        EmbedBuilder eb = new EmbedBuilder().setColor(BotReference.LUMA_COLOR).setTitle("Tasks:");
        if(BotReference.USE_NODES){
            final AtomicInteger i = new AtomicInteger(0);
            for(Task t : localQueue){
                i.incrementAndGet();
                Luma.nodeServer.getNodeRunningTask(t)
                        .ifPresentOrElse(node -> eb.addField(String.format(loc.get("queue_entry_title"), i.get(), t.getType(), t.getName()),
                                String.format(loc.get("queue_entry_message_node"), t.getRequester(api).getMentionTag(), node.requestStatus().join(), node.getLatestName()),
                                false),
                                () -> eb.addField(String.format(loc.get("queue_entry_title"), i.get(), t.getType(), t.getName()),
                                        String.format(loc.get("queue_entry_message"), t.getRequester(api).getMentionTag(), "Waiting..."),
                                        false));
            }
            return eb;
        }
        int i = 0;
        for (Task t : localQueue) {
            i++;
            eb.addField(String.format(loc.get("queue_entry_title"), i, t.getType(), t.getName()),
                    String.format(loc.get("queue_entry_message"), t.getRequester(api).getMentionTag(), t.getStatus()),
                    false);
        }
        return eb;
    }

    private static <T> Optional<T> getFirstMatching(Queue<T> queue, Predicate<T> predicate){
        for(T t : queue){
            if(predicate.test(t)){
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
