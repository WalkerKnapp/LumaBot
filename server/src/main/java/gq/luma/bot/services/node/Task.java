package gq.luma.bot.services.node;

import com.eclipsesource.json.JsonObject;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.user.User;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface Task {

    JsonObject serialize() throws IOException;

    String getType();

    String getName();

    User getRequester(DiscordApi api);

    String getStatus();

    boolean isRendering();

    void setRendering(boolean b);

    boolean isNoUpload();
}
