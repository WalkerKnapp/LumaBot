package gq.luma.bot.utils.embeds;

import de.btobastian.javacord.entities.message.embed.EmbedBuilder;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to create embeds.
 */
public class FilteredEmbed {

    private Color color;
    private String thumbnail;

    private String footer;

    private List<Object[]> fields = new ArrayList<>();

    public EmbedBuilder getBase() {
        return new EmbedBuilder().setColor(color).setThumbnail(thumbnail).setFooter(footer);
    }

    public FilteredEmbed addField(String title, String content, boolean inline){
        fields.add(new Object[]{title, content, inline, false});
        return this;
    }

    public FilteredEmbed addFieldTitle(String title, String content, boolean inline){
        fields.add(new Object[]{title, content, inline, true});
        return this;
    }

    public List<Object[]> getFields(){
        return fields;
    }

    public FilteredEmbed setColor(Color color) {
        this.color = color;
        return this;
    }

    public FilteredEmbed setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
        return this;
    }

    public FilteredEmbed setFooter(String footer) {
        this.footer = footer;
        return this;
    }
}
