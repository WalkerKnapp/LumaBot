package gq.luma.bot.systems.demorender;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.entities.User;
import gq.luma.bot.systems.srcdemo.SrcDemo;
import gq.luma.bot.utils.FileUtilities;
import org.apache.commons.io.FilenameUtils;

import java.io.*;

public class SingleSrcRenderTask extends SrcRenderTask{

    private SrcDemo demo;
    private RenderSettings settings;

    private File demoFile;
    private File baseDir;

    private long requester;

    public SingleSrcRenderTask(long requester, SrcDemo demo, RenderSettings settings, File demoFile, File baseDir){
        this.demo = demo;
        this.settings = settings;
        this.demoFile = demoFile;
        this.baseDir = baseDir;
        this.requester = requester;
    }

    public JsonObject serialize() {
        return new JsonObject()
                .set("type", "single-source")
                .set("requester", requester)
                .set("demo", demo.serialize())
                .set("settings", settings.serialize())
                .set("name", demoFile.getName())
                .set("dir", baseDir.getName())
                .set("requiredFiles", Json.array().add(FileUtilities.relatavizePathToTemp(demoFile)));
    }

    @Override
    public String getType() {
        return "Source Demo Render";
    }

    @Override
    public String getName() {
        return FilenameUtils.removeExtension(demoFile.getName());
    }

    @Override
    public User getRequester(DiscordApi api) {
        return api.getUserById(requester).join();
    }

    @Override
    public String getStatus() {
        return null;
    }

    @Override
    public boolean isNoUpload() {
        return settings.isNoUpload();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof SingleSrcRenderTask && ((SingleSrcRenderTask) object).baseDir.getName().equalsIgnoreCase(this.baseDir.getName()) && ((SingleSrcRenderTask) object).demoFile.getName().equalsIgnoreCase(this.demoFile.getName());
    }
}
