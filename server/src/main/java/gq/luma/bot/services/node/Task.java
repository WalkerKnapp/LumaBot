package gq.luma.bot.services.node;

import com.eclipsesource.json.JsonObject;
import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.entities.User;

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
