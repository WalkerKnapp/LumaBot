package gq.luma.bot.render.fs;

import gq.luma.bot.LumaException;
import gq.luma.bot.RenderSettings;
import gq.luma.bot.render.renderer.FFRenderer;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface RenderFS {
    void configure(RenderSettings settings, FFRenderer renderer);

    CompletableFuture<Void> getErrorHandler();

    void waitToFinish() throws IOException, InterruptedException, LumaException;

    void shutdown();

    default int extractIndex(char[] name){
        int total = 0;
        int j = 1;
        for(int i = name.length - 5; i >= 0; i--){
            if(name[i] == '_')
                break;
            total += (name[i] - '0') * j;
            j *= 10;
        }
        return total;
    }
}
