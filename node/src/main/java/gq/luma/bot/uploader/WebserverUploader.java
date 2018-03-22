package gq.luma.bot.uploader;

import java.io.File;
import java.io.IOException;

public class WebserverUploader implements Uploader {
    @Override
    public String getType() {
        return "webserver";
    }

    @Override
    public String uploadFile(File file) throws IOException {
        return null;
    }
}
