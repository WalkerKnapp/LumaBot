package gq.luma.bot.render;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface Task {

    CompletableFuture<File> execute();

    String getType();

    String getStatus();

    void cancel() throws IOException;

    String getThumbnail();
}
