package gq.luma.bot.systems.ffprobe;

import com.eclipsesource.json.JsonObject;

public class FFProbeVideoStream extends FFProbeStream {
    private int width;
    private int height;
    private int codedWidth;
    private int codedHeight;
    private int bFrames;
    private String sampleAspectRatio;
    private String displayAspectRatio;
    private int level;
    private String chromaLocation;
    private int refs;
    private boolean isAvc;
    private int nalLengthSize;

    @Override
    protected void generate(JsonObject json) {
        this.width = json.get("width").asInt();
        this.height = json.get("height").asInt();
        this.codedWidth = json.get("coded_width").asInt();
        this.codedHeight = json.get("coded_height").asInt();
        this.bFrames = json.get("has_b_frames").asInt();
        this.sampleAspectRatio = json.get("sample_aspect_ratio").asString();
        this.displayAspectRatio = json.get("display_aspect_ratio").asString();
        this.level = json.get("level").asInt();
        this.chromaLocation = json.get("chroma_location").asString();
        this.refs = json.get("refs").asInt();
        this.isAvc = Boolean.parseBoolean(json.get("is_avc").asString());
        this.nalLengthSize = Integer.parseInt(json.get("nal_length_size").asString());
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCodedWidth() {
        return codedWidth;
    }

    public int getCodedHeight() {
        return codedHeight;
    }

    public int getbFrames() {
        return bFrames;
    }

    public String getSampleAspectRatio() {
        return sampleAspectRatio;
    }

    public String getDisplayAspectRatio() {
        return displayAspectRatio;
    }

    public int getLevel() {
        return level;
    }

    public String getChromaLocation() {
        return chromaLocation;
    }

    public int getRefs() {
        return refs;
    }

    public boolean isAvc() {
        return isAvc;
    }

    public int getNalLengthSize() {
        return nalLengthSize;
    }
}
