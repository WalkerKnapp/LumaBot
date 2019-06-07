package gq.luma.bot.render;

import gq.luma.bot.LumaException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface Task {

    CompletableFuture<File> execute();

    String getType();

    String getStatus();

    void cancel() throws IOException, LumaException;

    String getThumbnail();
}
