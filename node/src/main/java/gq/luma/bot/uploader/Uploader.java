package gq.luma.bot.uploader;

import java.io.File;
import java.io.IOException;

public interface Uploader {
    String getType();

    String uploadFile(File file) throws IOException;
}
