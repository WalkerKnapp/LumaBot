package gq.luma.bot.utils.embeds;

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.reaction.ReactionAddEvent;
import org.javacord.api.listener.message.reaction.ReactionAddListener;
import gq.luma.bot.reference.BotReference;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class EmbeddedMessage implements ReactionAddListener {
    private Message message;
    private List<EmbedPage> pages;
    private AtomicInteger index;
    private FilteredEmbed filteredEmbed;

    EmbeddedMessage(Message message, FilteredEmbed embed, List<EmbedPage> pages){
        this.message = message;
        this.pages = pages;
        this.filteredEmbed = embed;
        this.index = new AtomicInteger(0);

        updatePage();
        if(pages.size() > 1){
            message.addReactionAddListener(this);
            message.addReaction(BotReference.LEFT_ARROW);
            message.addReaction(BotReference.RIGHT_ARROW);
        }
    }

    public void addPage(EmbedPage page){
        pages.add(page);
        updatePage();
        if(pages.size() == 2){
            message.addReactionAddListener(this);
            message.addReaction(BotReference.LEFT_ARROW);
            message.addReaction(BotReference.RIGHT_ARROW);
        }
    }

    private void updatePage(){
        try {
            message.edit("", createBuilder(filteredEmbed.setFooter((index.get() + 1) + "/" + pages.size()).getBase(), pages.get(index.get()))).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReactionAdd(ReactionAddEvent event) {
        event.getEmoji().asUnicodeEmoji()
                .filter(s -> !event.getUser().isYourself())
                .filter(s -> s.equals(BotReference.LEFT_ARROW) || s.equals(BotReference.RIGHT_ARROW))
                .ifPresent(s -> {
                    event.removeReaction();
                    if(s.equals(BotReference.LEFT_ARROW) && index.get() > 0){
                        index.getAndDecrement();
                        updatePage();
                    }
                    else if(s.equals(BotReference.RIGHT_ARROW) && index.get() < pages.size() + 1){
                        index.getAndIncrement();
                        updatePage();
                    }
                });
    }

    private static EmbedBuilder createBuilder(EmbedBuilder builder, EmbedPage page){
        page.getFields().forEach(array -> builder.addField((String)array[0], (String)array[1], (boolean)array[2]));
        return builder;
    }
}
