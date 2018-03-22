package gq.luma.bot.systems.ffprobe;

import com.eclipsesource.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class FFProbeDisposition {
    private List<String> tags;

    public static FFProbeDisposition of(JsonObject object){
        FFProbeDisposition disposition = new FFProbeDisposition();
        disposition.tags = new ArrayList<>();
        for(String name : object.names()){
            if(object.get(name).asInt() == 1){
                disposition.tags.add(name);
            }
        }
        return disposition;
    }
}
