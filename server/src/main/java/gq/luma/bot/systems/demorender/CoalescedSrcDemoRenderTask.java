package gq.luma.bot.systems.demorender;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.entities.User;
import gq.luma.bot.systems.srcdemo.SrcDemo;

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
        //System.out.println("Started serialize");
        JsonArray demosArray = Json.array();
        //System.out.println("Serialize 1");
        for (SrcDemo srcDemo : srcDemos) {
            //System.out.println("Serializno: " + srcDemo.getAssociatedFile());
            JsonObject serialize = srcDemo.serialize();
            //System.out.println("Serializno 2");
            demosArray.add(serialize);
            //System.out.println("Serizlizno 3");
        }
        //System.out.println("Serialize 2");
        JsonArray requiredFilesArray = Json.array();
        //System.out.println("Serialize 3");
        srcDemos.stream().map(SrcDemo::getAssociatedFile).forEach(requiredFilesArray::add);
        //System.out.println("Serialize 4");
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
    public boolean equals(Object object) {
        return object instanceof CoalescedSrcDemoRenderTask && ((CoalescedSrcDemoRenderTask) object).baseDir.getName().equalsIgnoreCase(this.baseDir.getName());
    }
}
