package gq.luma.bot.systems.filtering;

import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.entities.channels.ServerChannel;
import de.btobastian.javacord.entities.channels.TextChannel;
import de.btobastian.javacord.entities.message.MessageAuthor;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import de.btobastian.javacord.events.message.MessageCreateEvent;
import de.btobastian.javacord.listeners.message.MessageCreateListener;
import gq.luma.bot.Luma;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.params.io.input.FileInput;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.services.Service;
import gq.luma.bot.systems.filtering.filters.types.FileFilter;
import gq.luma.bot.systems.filtering.filters.types.Filter;
import gq.luma.bot.systems.filtering.filters.types.TextFilter;
import gq.luma.bot.utils.LumaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class FilterManager implements Service, MessageCreateListener {
    private static final Logger logger = LoggerFactory.getLogger(FilterManager.class);
    private Map<Long, Collection<Filter>> filterCache;

    @Override
    public void startService() throws Exception {
        filterCache = Luma.database.getAllFilters();
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        MessageAuthor author = event.getMessage().getAuthor();
        event.getServer().ifPresent(server -> {
            try {
                if (filterCache.containsKey(server.getId())) {
                    InputType[] neededTypes = filterCache.get(server.getId()).stream()
                            .filter(filter -> filter instanceof FileFilter)
                            .map(filter -> (FileFilter) filter)
                            .flatMap(fileFilter -> fileFilter.checkTypes().stream())
                            .distinct()
                            .toArray(InputType[]::new);
                    logger.debug("Needed types: {}", Arrays.toString(neededTypes));
                    ArrayList<FileInput> inputs = ParamUtilities.analyzeMessage(event.getMessage(), neededTypes);
                    logger.debug("Found types: {}", Arrays.toString(inputs.stream().map(in -> {
                        try {
                            return in.getInputType();
                        } catch (IOException | LumaException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }).map(InputType::name).toArray()));

                    for (Filter filter : filterCache.get(server.getId())) {
                        if (canRunFilter(filter, event)) {
                            ArrayList<FilteringResult> results = new ArrayList<>();
                            if (filter instanceof FileFilter) {
                                FileFilter fileFilter = (FileFilter) filter;
                                for(FileInput input : inputs){
                                    if(fileFilter.checkTypes().contains(input.getInputType())){
                                        results.add(fileFilter.checkInputStream(input.getStream()));
                                    }
                                }
                            } else if (filter instanceof TextFilter) {
                                TextFilter textFilter = (TextFilter) filter;
                                results.add(textFilter.checkText(event.getMessage().getContent()));
                            }
                            for(FilteringResult result : results) {
                                if (!result.isOkay()) {
                                    int actionType = 0;
                                    int strikesRemaining = 0;
                                    List<String> logConsequences = new ArrayList<>();
                                    List<String> addressedConsequences = new ArrayList<>();
                                    List<String> generalConsequences = new ArrayList<>();
                                    if (filter.getEffect().get("should_remove_message").asBoolean()) {
                                        if (filter.getEffect().contains("remove_message_strikes")) {
                                            actionType = 1;
                                            //TODO: Filter Strikes
                                        } else {
                                            actionType = 2;
                                            logConsequences.add("Removed message from");
                                            addressedConsequences.add("Your message has been removed");
                                            generalConsequences.add("Message removed");
                                            event.getMessage().delete().exceptionally(Javacord::exceptionLogger);
                                        }
                                    }
                                    if (filter.getEffect().get("should_give_role").asBoolean()) {
                                        if (filter.getEffect().contains("give_role_strikes")) {
                                            actionType = 1;
                                            //TODO: Filter Strikes
                                        } else {
                                            actionType = 2;
                                            server.getRoleById(filter.getEffect().get("given_role").asLong()).ifPresentOrElse(role -> {
                                                if(!author.asUser().map(user -> server.getRolesOf(user).contains(role)).orElse(true)) {
                                                    logConsequences.add("Gave role " + role.getName() + " to");
                                                    addressedConsequences.add("You have been given the " + role.getName() + " role");
                                                    generalConsequences.add("Given the " + role.getName() + " role");
                                                    author.asUser().ifPresent(user -> server.getUpdater().addRoleToUser(user, role).update().exceptionally(Javacord::exceptionLogger));
                                                }
                                            }, () -> logger.error("Unable to find role by id: " +
                                                    filter.getEffect().get("given_role").asLong() +
                                                    " for filter: " + filter.getId() +
                                                    " on server: " + filter.getServer()));
                                        }
                                    }
                                    if (filter.getEffect().get("should_kick").asBoolean()) {
                                        if (filter.getEffect().contains("kick_strikes")) {
                                            actionType = 1;
                                            //TODO: Filter Strikes
                                        } else {
                                            actionType = 2;
                                            author.asUser().ifPresent(server::kickUser);
                                            logConsequences.add("Kicked");
                                            addressedConsequences.add("You have been kicked");
                                            generalConsequences.add("Kicked");
                                        }
                                    }
                                    if (filter.getEffect().get("should_ban").asBoolean()) {
                                        if (filter.getEffect().contains("ban_strikes")) {
                                            actionType = 1;
                                            //TODO: Filter Strikes
                                        } else {
                                            actionType = 2;
                                            author.asUser().ifPresent(server::banUser);
                                            logConsequences.add("Banned");
                                            addressedConsequences.add("You have been banned");
                                            generalConsequences.add("Banned");
                                        }
                                    }

                                    String logConsequence = constructList(logConsequences) + " ";
                                    String addressedConsequence = constructList(addressedConsequences);
                                    String generalConsequence = constructList(generalConsequences);

                                    if (filter.getEffect().get("should_warn_chat").asBoolean() || filter.getEffect().get("should_warn_pm").asBoolean()) {
                                        String message = "";
                                        switch (actionType) {
                                            case 0:
                                                message = filter.getEffect().get("chat_no_action_taken_message").asString();
                                                break;
                                            case 1:
                                                message = filter.getEffect().get("chat_strikes_remaining_message").asString()
                                                        .replace("%STRIKES_REMAINING%", String.valueOf(strikesRemaining));
                                                break;
                                            case 2:
                                                message = filter.getEffect().get("chat_action_taken_message").asString()
                                                        .replace("%ADDRESSED_ACTION_TAKEN%", addressedConsequence)
                                                        .replace("%GENERAL_ACTION_TAKEN%", generalConsequence);
                                        }
                                        message = message.replace("%USER_TAG%", "<@" + author.getId() + ">")
                                                .replace("%FILTER_NAME%", filter.getName());
                                        if (filter.getEffect().get("should_warn_chat").asBoolean()) {
                                            event.getChannel().sendMessage(message);
                                        }
                                        if (filter.getEffect().get("should_warn_pm").asBoolean()) {
                                            final String finalMessage = message;
                                            author.asUser().ifPresent(user -> user.sendMessage(finalMessage));
                                        }
                                    }
                                    if (filter.getEffect().contains("log")) {
                                        server.getTextChannelById(filter.getEffect().get("log").asLong())
                                                .ifPresentOrElse(channel -> channel.sendMessage(generateLogEmbed(logConsequence, author, filter, result, channel)),
                                                        () -> logger.error("Unabled to find text channel for id: " + filter.getEffect().get("log").asLong()));
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (LumaException | IOException e) {
                logger.error("Encountered error while trying to process filter", e);
            }
        });
    }

    private String constructList(List<String> list){
        StringBuilder sb = new StringBuilder(list.get(0));
        for(int i = 1; i < list.size(); i++){
            if(list.size() > 2) {
                sb.append(",");
            }
            sb.append(" ");
            if(i + 1 == list.size()){
                sb.append("and ");
            }
            sb.append(Character.toLowerCase(list.get(1).charAt(0)));
            sb.append(list.get(i).substring(1));
        }
        return sb.toString();
    }

    private EmbedBuilder generateLogEmbed(String consequence, MessageAuthor author, Filter filter, FilteringResult result, TextChannel channel){
        EmbedBuilder eb =  new EmbedBuilder()
                .setTitle(consequence + author.getDiscriminatedName())
                .addField("Violated filter: " + filter.getName(), result.getMessage(), false)
                .setFooter("#" + channel.asServerChannel().map(ServerChannel::getName).orElse("(invalid channel)"))
                .setTimestamp();
        return eb;
    }

    private boolean canRunFilter(Filter filter, MessageCreateEvent event){
        if(!filter.isEnabled())
            return false;

        if(filter.getServerScope() == 1){
            if(!filter.getServerScopeParams().contains(event.getChannel().getId())){
                return false;
            }
        }

        if(filter.getUserScope() == 1){
            return filter.getUserScopeParams().contains(event.getMessage().getAuthor().getId());
        } else if(filter.getUserScope() == 2){
            return !filter.getUserScopeParams().contains(event.getMessage().getAuthor().getId());
        }

        return true;
    }
}
