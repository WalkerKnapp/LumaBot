package gq.luma.bot.commands.params.io.input;

import gq.luma.bot.LumaException;
import org.javacord.api.Javacord;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class IndirectUrlInput implements FileInput {

    private URL url;
    private String filename;

    private long size;

    public IndirectUrlInput(URL url, String filename){
        this.url = url;
        this.filename = filename;
    }

    @Override
    public String getInputName() {
        return "delivery_type_indirect_url";
    }

    @Override
    public String getName() throws IOException, LumaException {
        return filename;
    }

    @Override
    public InputStream getStream() throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", Javacord.USER_AGENT);
        size = conn.getContentLengthLong();
        return conn.getInputStream();
    }

    @Override
    public long getSize() throws IOException, LumaException {
        return size;
    }
}
