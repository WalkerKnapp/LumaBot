package gq.luma.bot.commands.params.io.input;

import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.entities.message.MessageAttachment;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;

public class AttachmentInput implements FileInput {

    private MessageAttachment attachment;

    public AttachmentInput(MessageAttachment attachment){
        this.attachment = attachment;
    }

    long size = 0;

    @Override
    public String getInputName() {
        return "delivery_type_attachment";
    }

    @Override
    public String getName() {
        return attachment.getFileName();
    }

    @Override
    public InputStream getStream() throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) attachment.getUrl().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", Javacord.USER_AGENT);
        size = conn.getContentLengthLong();
        return conn.getInputStream();
    }

    @Override
    public long getSize() {
        return size;
    }
}
