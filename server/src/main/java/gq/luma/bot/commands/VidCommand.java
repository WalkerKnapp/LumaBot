package gq.luma.bot.commands;

import com.eclipsesource.json.Json;
import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import okhttp3.Request;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.Charset;

public class VidCommand {
    @Command(aliases = {"vid"}, description = "vid_description", usage = "vid_usage")
    public String lookupVid(CommandEvent event) throws IOException {
        String prompt = URLEncoder.encode(event.getCommandRemainder(), Charset.defaultCharset());

        InputStream responseStream = Luma.okHttpClient.newCall(
                new Request.Builder().url("https://autorender.portal2.sr/api/v1/search?q=" + prompt).build()
                ).execute().body().byteStream();

        var resultsObject = Json.parse(new InputStreamReader(responseStream))
                .asObject().get("results");

        if (resultsObject.isArray() && resultsObject.asArray().size() > 0) {
            int videoId = resultsObject.asArray().get(0).asObject().get("id").asInt();

            return "https://autorender.portal2.sr/video.html?v=" + videoId;
        } else {
            return "No videos found matching this query :( https://tenor.com/view/hamster-crying-hamster-crying-rip-hamster-hamster-die-gif-19350296";
        }
    }
}
