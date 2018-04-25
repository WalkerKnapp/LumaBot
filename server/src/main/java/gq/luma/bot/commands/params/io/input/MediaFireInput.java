package gq.luma.bot.commands.params.io.input;

import gq.luma.bot.utils.LumaException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class MediaFireInput implements FileInput {
    private URL pageURL;

    public MediaFireInput(URL pageURL){
        this.pageURL = pageURL;
    }

    @Override
    public String getInputName() {
        return "delivery_type_mediafire";
    }

    @Override
    public String getName() throws IOException, LumaException {
        return null;
    }

    @Override
    public InputStream getStream() throws IOException {
        return null;
    }

    @Override
    public long getSize() throws IOException, LumaException {
        return 0;
    }
}
