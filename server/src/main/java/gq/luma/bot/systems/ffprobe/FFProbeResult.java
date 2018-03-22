package gq.luma.bot.systems.ffprobe;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import gq.luma.bot.utils.LumaException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FFProbeResult {
    private List<FFProbeStream> streams;

    private String filename;
    private int numStreams;
    private int numPrograms;
    private String formatName;
    private String formatLongName;
    private double duration;
    private int probeScore;
    private Map<String, String> tags;

    public List<FFProbeStream> getStreams(){
        return this.streams;
    }

    public String getFilename() {
        return filename;
    }

    public int getNumStreams() {
        return numStreams;
    }

    public int getNumPrograms() {
        return numPrograms;
    }

    public String getFormatName() {
        return formatName;
    }

    public String getFormatLongName() {
        return formatLongName;
    }

    public double getDuration() {
        return duration;
    }

    public int getProbeScore() {
        return probeScore;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public static FFProbeResult of(JsonObject json) throws LumaException {
        FFProbeResult result = new FFProbeResult();

        result.streams = new ArrayList<>();
        for (JsonValue jsonValue : json.get("streams").asArray().values()) {
            JsonObject asObject = jsonValue.asObject();
            FFProbeStream of = FFProbeStream.of(asObject);
            result.streams.add(of);
        }

        JsonObject format = json.get("format").asObject();
        result.filename = format.get("filename").asString();
        result.numStreams = format.get("nb_streams").asInt();
        result.numPrograms = format.get("nb_programs").asInt();
        result.formatName = format.get("format_name").asString();
        result.formatLongName = format.get("format_long_name").asString();
        result.duration = Double.parseDouble(format.get("duration").asString());
        result.probeScore = format.get("probe_score").asInt();

        JsonObject tags = format.get("tags").asObject();
        result.tags = new HashMap<>();
        List<String> tagNames = tags.names();
        for(String name : tagNames){
            result.tags.put(name, tags.get(name).asString());
        }
        return result;
    }
}
