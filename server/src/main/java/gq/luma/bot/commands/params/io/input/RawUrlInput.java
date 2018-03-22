package gq.luma.bot.commands.params.io.input;

import de.btobastian.javacord.Javacord;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;

public class RawUrlInput implements FileInput{

    private URL url;
    private long size;

    public RawUrlInput(URL input){
        this.url = input;
    }

    @Override
    public String getInputName() {
        return "delivery_type_raw_url";
    }

    @Override
    public String getName() {
        Matcher matcher = FULL_NAME_PATTERN.matcher(url.toString());
        if(matcher.find()) {
            return matcher.group(0);
        }
        return "";
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
    public long getSize() {
        return size;
    }
}
