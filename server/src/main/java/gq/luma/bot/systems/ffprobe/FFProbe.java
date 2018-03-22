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
        CompletableFuture<FFProbeResult> cf = new CompletableFuture<>();

        new Thread(() -> analyzeJson(stream).thenAccept(json -> {
            //System.out.println("Caught json: " + json.toString(WriterConfig.PRETTY_PRINT));
            try {
                cf.complete(FFProbeResult.of(json));
            } catch (LumaException e) {
                cf.completeExceptionally(e);
            }
        }).exceptionally(t -> {
            cf.completeExceptionally(t);
            return null;
        })).start();

        return cf;
    }

    private static CompletableFuture<JsonObject> analyzeJson(InputStream stream){
        CompletableFuture<JsonObject> cf = new CompletableFuture<>();
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(FileReference.ffprobe.getAbsolutePath(), "-v", "quiet", "-print_format", "json", "-show_error", "-show_format", "-show_streams", "", "", "-");
                pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                OutputStream os = p.getOutputStream();
                BufferedReader reader = wrapInReader(p);

                IOUtils.copy(stream, os);
                os.close();
                p.waitFor();

                String json = reader.lines().collect(Collectors.joining());
                //System.out.println("Parsed json: " + json);

                cf.complete(Json.parse(json).asObject());

                os.close();
            } catch (IOException | InterruptedException e){
                cf.completeExceptionally(e);
            }
        }).start();
        return cf;
    }

    private static BufferedReader wrapInReader(Process p) {
        return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
    }
}
