package gq.luma.bot.systems.ffprobe;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.LumaException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class FFProbeStream {
    private int index;
    private String codecName;
    private String codecLongName;
    private FFProbeCodecType codecType;
    private String codecTimeBase;
    private String codecTagString;
    private String codecTag;

    private String rFrameRate;
    private String avgFrameRate;
    private String timeBase;
    private long durationTs;
    private double duration;
    private long bitRate;
    private long maxBitRate;
    private int numberFrames;
    private FFProbeDisposition disposition;
    private Map<String, String> tags;

    public int getIndex() {
        return index;
    }

    public String getCodecName() {
        return codecName;
    }

    public String getCodecLongName(){
        return codecLongName;
    }

    public FFProbeCodecType getCodecType() {
        return codecType;
    }

    public String getCodecTimeBase() {
        return codecTimeBase;
    }

    public String getCodecTagString() {
        return codecTagString;
    }

    public String getCodecTag() {
        return codecTag;
    }

    public String getrFrameRate(){
        return rFrameRate;
    }

    public double getDoubleFrameRate(){
        return Integer.valueOf(avgFrameRate.split("/")[0]) / Integer.valueOf(avgFrameRate.split("/")[1]);
    }

    public String getAvgFrameRate() {
        return avgFrameRate;
    }

    public String getTimeBase() {
        return timeBase;
    }

    public long getDurationTs() {
        return durationTs;
    }

    public double getDuration() {
        return duration;
    }

    public long getBitRate() {
        return bitRate;
    }

    public long getMaxBitRate() {
        return maxBitRate;
    }

    public int getNumberFrames() {
        return numberFrames;
    }

    public FFProbeDisposition getDisposition() {
        return disposition;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public FFProbeVideoStream asVideoStream(){
        return (FFProbeVideoStream) this;
    }

    public FFProbeAudioStream asAudioStream(){
        return (FFProbeAudioStream) this;
    }

    public static FFProbeStream of(JsonObject json) throws LumaException {
        FFProbeStream stream;
        if(json.get("codec_type").asString().equalsIgnoreCase("audio")){
            stream = new FFProbeAudioStream();
        } else if(json.get("codec_type").asString().equalsIgnoreCase("video")) {
            stream = new FFProbeVideoStream();
        } else {
            throw new LumaException("Unable to parse codec type: " + json.get("codec_type").asString());
        }

        stream.index = json.get("index").asInt();
        stream.codecName = json.get("codec_name").toString();
        stream.codecLongName = json.get("codec_long_name").asString();
        stream.codecType = Stream.of(FFProbeCodecType.values()).filter(t -> t.name().equalsIgnoreCase(json.get("codec_type").asString())).findAny().orElseThrow(() -> new LumaException("Unable to find codec type."));
        stream.codecTimeBase = json.get("codec_time_base").asString();
        stream.codecTagString = json.get("codec_tag_string").asString();
        stream.codecTag = json.get("codec_tag").asString();

        stream.rFrameRate = json.get("r_frame_rate").asString();
        stream.avgFrameRate = json.get("avg_frame_rate").asString();
        stream.timeBase = json.get("time_base").asString();
        stream.durationTs = json.get("duration_ts").asLong();
        stream.duration = Double.parseDouble(json.get("duration").asString());
        stream.bitRate = Long.parseLong(json.get("bit_rate").asString());
        stream.maxBitRate = json.get("max_bit_rate") != null ? Long.parseLong(json.get("max_bit_rate").asString()) : -1;
        stream.numberFrames = Integer.parseInt(json.get("nb_frames").asString());
        stream.disposition = FFProbeDisposition.of(json.get("disposition").asObject());

        JsonObject tags = json.get("tags").asObject();
        stream.tags = new HashMap<>();
        List<String> tagNames = tags.names();
        for(String name : tagNames){
            stream.tags.put(name, tags.get(name).asString());
        }
        stream.generate(json);

        return stream;
    }

    protected abstract void generate(JsonObject json);
}
