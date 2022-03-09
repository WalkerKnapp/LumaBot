package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ResourcesCommand {
    @Command(aliases = {"resources"}, description = "resources_description", usage = "", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public EmbedBuilder onResources(CommandEvent event) throws IOException {
        if (event.getCommandRemainder().isEmpty()) {
            var message = event.getChannel().sendMessage("**Resources**").join();

            Files.write(Paths.get("resources_channel.txt"), message.getChannel().getIdAsString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(Paths.get("resources_messages.txt"), message.getIdAsString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        }
        return null;
    }


    @Command(aliases = {"set"}, description = "resources_add_description", parent = "resources", usage = "", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public EmbedBuilder onResourcesSet(CommandEvent event) throws IOException {
        AtomicReference<ListenerManager<MessageCreateListener>> listener = new AtomicReference<>();
        ArrayList<String> resourcesParts = new ArrayList<>();

        listener.set(event.getChannel().addMessageCreateListener(e -> {
            if (e.getMessageAuthor().getId() == event.getAuthor().getId()) {
                if (e.getMessageContent().equals("save")) {

                    try {
                        long channelId = Long.parseLong(Files.readAllLines(Paths.get("resources_channel.txt")).get(0));
                        List<Long> messageIds = Files.readAllLines(Paths.get("resources_messages.txt")).stream().map(Long::parseLong).collect(Collectors.toList());
                        List<String> newMessageIds = new ArrayList<>();

                        while (messageIds.size() < resourcesParts.size()) {
                            messageIds.add(event.getApi().getTextChannelById(channelId).get().sendMessage("**Resources**").join().getId());
                        }

                        for (int i = 0; i < resourcesParts.size(); i++) {
                            event.getApi().getMessageById(messageIds.get(i), event.getApi().getTextChannelById(channelId).get())
                                    .join().edit(resourcesParts.get(i)).join();

                            newMessageIds.add(String.valueOf(messageIds.get(i)));
                        }

                        for (int i = resourcesParts.size(); i < messageIds.size(); i++) {
                            event.getApi().getMessageById(messageIds.get(i), event.getApi().getTextChannelById(channelId).get())
                                    .join().delete().join();
                        }

                        Files.write(Paths.get("resources_messages.txt"), newMessageIds,
                                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }

                    listener.get().remove();
                } else if (e.getMessageContent().equals("quit")) {
                    listener.get().remove();
                } else {
                    resourcesParts.add(e.getMessageContent());
                }
            }
        }));

        return new EmbedBuilder()
                .setDescription("Send the resources message in multiple messages. Send a message just containing `save` to update resources, or `quit` to cancel.");
    }
}
