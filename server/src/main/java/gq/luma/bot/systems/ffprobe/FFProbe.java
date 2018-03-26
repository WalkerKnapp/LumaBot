package gq.luma.bot.systems.ffprobe;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.utils.LumaException;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FFProbe {

    public static CompletableFuture<FFProbeResult> analyzeByStream(InputStream stream) {
        return analyzeJson(stream).thenApply(json -> {
            try {
                return FFProbeResult.of(json);
            } catch (LumaException e) {
                throw  new RuntimeException(e);
            }
        });
    }

    private static CompletableFuture<JsonObject> analyzeJson(InputStream stream){
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(FileReference.ffprobe.getAbsolutePath(), "-v", "quiet", "-print_format", "json", "-show_error", "-show_format", "-show_streams", "", "", "-");
                pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                BufferedReader reader = wrapInReader(p);
                try(OutputStream os = p.getOutputStream()) {
                    IOUtils.copy(stream, os);
                    p.waitFor();
                }
                String json = reader.lines().collect(Collectors.joining());

                return Json.parse(json).asObject();
            } catch (IOException | InterruptedException e){
                throw new RuntimeException(e);
            }
        });
    }

    private static BufferedReader wrapInReader(Process p) {
        return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
    }
}
