package gq.luma.bot.render.fs;

import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.render.structure.RenderSettings;

import java.util.concurrent.CompletableFuture;

public interface RenderFS {
    void configure(RenderSettings settings, FFRenderer renderer);

    CompletableFuture<Void> getErrorHandler();

    void waitToFinish();

    void shutdown();

    default int extractIndex(String name){
        char[] nameCharArray = name.toCharArray();
        int total = 0;
        int j = 1;
        for(int i = nameCharArray.length - 5; i >= 0; i--){
            if(nameCharArray[i] == '_')
                break;
            total += Character.getNumericValue(nameCharArray[i]) * j;
            j *= 10;
        }
        return total;
    }
}
