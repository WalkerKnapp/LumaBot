package gq.luma.bot.systems.demorender;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import gq.luma.bot.RenderSettings;
import gq.luma.bot.systems.srcdemo.SrcDemo;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.user.User;

import java.io.File;
import java.util.List;

public class CoalescedSrcDemoRenderTask extends SrcRenderTask {

    private String name;
    private long requester;

    private List<SrcDemo> srcDemos;
    private RenderSettings settings;
    private File baseDir;

    public CoalescedSrcDemoRenderTask(String name, long requester, File baseDir, RenderSettings settings, List<SrcDemo> demos){
        this.name = name;
        this.requester = requester;
        this.baseDir = baseDir;
        this.settings = settings;
        this.srcDemos = demos;
    }

    @Override
    public JsonObject serialize(){
        JsonArray demosArray = Json.array();
        for (SrcDemo srcDemo : srcDemos) {
            JsonObject serialize = srcDemo.serialize();
            demosArray.add(serialize);
        }
        JsonArray requiredFilesArray = Json.array();
        srcDemos.stream().map(SrcDemo::getAssociatedFile).forEach(requiredFilesArray::add);
        return new JsonObject()
                .set("type", "coalesced")
                .set("requester", requester)
                .set("demos", demosArray)
                .set("settings", settings.serialize())
                .set("dir", baseDir.getName())
                .set("name", this.name)
                .set("requiredFiles", requiredFilesArray);
    }

    @Override
    public String getType() {
        return "Coalesced Source Demo Render";
    }

    @Override
    public String getName() {
        return this.name;
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
        return object instanceof CoalescedSrcDemoRenderTask && ((CoalescedSrcDemoRenderTask) object).baseDir.getName().equalsIgnoreCase(this.baseDir.getName());
    }
}
