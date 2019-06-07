package gq.luma.bot;

import com.eclipsesource.json.JsonObject;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RenderSettings implements Cloneable {

    private static final Pattern RESOLUTION_REGEX = Pattern.compile("(?<=^)(\\d+)x((\\d+)(?=$))");
    private static final Pattern RESOLUTION_P_REGEX = Pattern.compile("(?<=^)(\\d+)p(?=$)");

    private int width;
    private int height;
    private int fps;
    private int frameblendIndex = 1;
    private int crf = 18;

    private boolean startOdd = true;
    private boolean specifiedStartOdd = false;
    private boolean hq = false;
    private boolean interpolate = true;
    private boolean oob = false;
    private boolean removeBroken = true;
    private boolean demohack = false;
    private boolean pretify = false;
    private boolean twoPass = true;
    private boolean noUpload = false;

    private VideoOutputFormat format = VideoOutputFormat.H264;
    private RenderWeighterType weighterType = RenderWeighterType.LINEAR;

    public JsonObject serialize(){
        return new JsonObject()
                .add("width", this.width)
                .add("height", this.height)
                .add("fps", this.fps)
                .add("frameblendIndex", this.frameblendIndex)
                .add("crf", this.crf)
                .add("startOdd", this.startOdd)
                .add("specifiedStartOdd", this.specifiedStartOdd)
                .add("hq", this.hq)
                .add("interpolate", this.interpolate)
                .add("oob", this.oob)
                .add("removeBroken", this.removeBroken)
                .add("demohack", this.demohack)
                .add("pretify", this.pretify)
                .add("twoPass", this.twoPass)
                .add("format", this.format.name())
                .add("weighterType", this.weighterType.name());
    }

    public static RenderSettings parse(Map<String, String> map) throws LumaException {
        RenderSettings settings = new RenderSettings();

        if(map.containsKey("preset") && !map.get("preset").isEmpty()){
            RenderPreset preset = Stream.of(RenderPreset.values())
                    .filter(e -> e.name().equalsIgnoreCase(map.get("preset")))
                    .findAny()
                    .orElseThrow(() -> new LumaException("Invalid or missing preset."));
            settings.width = preset.getWidth();
            settings.height = preset.getHeight();
            settings.fps = preset.getFramerate();
            settings.frameblendIndex = preset.getFrameblendIndex();
            settings.startOdd = preset.isStartOdd();
            settings.hq = preset.isHq();
            settings.crf = preset.getCrf();
        }
        if(map.containsKey("width") && !map.get("width").isEmpty()){
            settings.width = Integer.parseInt(map.get("width"));
        }
        if(map.containsKey("height") && !map.get("height").isEmpty()){
            settings.height = Integer.parseInt(map.get("height"));
        }
        if(map.containsKey("resolution") && !map.get("resolution").isEmpty()){
            Matcher xMatcher = RESOLUTION_REGEX.matcher(map.get("resolution"));
            if(xMatcher.find()){
                settings.width = Integer.parseInt(xMatcher.group(1));
                settings.height = Integer.parseInt(xMatcher.group(2));
            }

            Matcher pMatcher = RESOLUTION_P_REGEX.matcher(map.get("resolution"));
            if(pMatcher.find()){
                settings.height = Integer.parseInt(pMatcher.group(1));
                settings.width = (int)(settings.height * (16d/9d));
            }

        }
        if(map.containsKey("fps") && !map.get("fps").isEmpty()){
            settings.fps = Integer.parseInt(map.get("fps"));
        }
        if(map.containsKey("frameblend") && !map.get("frameblend").isEmpty()){
            settings.frameblendIndex = Integer.parseInt(map.get("frameblend"));
        }
        if(map.containsKey("startodd") && !map.get("startodd").isEmpty()){
            settings.specifiedStartOdd = true;
            settings.startOdd = Boolean.parseBoolean(map.get("startodd").toLowerCase());
        }
        if(map.containsKey("hq") && !map.get("hq").isEmpty()){
            settings.hq = Boolean.parseBoolean(map.get("hq").toLowerCase());
        }
        if(map.containsKey("interpolate") && !map.get("interpolate").isEmpty()){
            settings.interpolate = Boolean.parseBoolean(map.get("interpolate").toLowerCase());
        }
        if(map.containsKey("format") && !map.get("format").isEmpty()){
            settings.format = Stream.of(VideoOutputFormat.values()).filter(f -> f.name().equalsIgnoreCase(map.get("format"))).findAny().orElseThrow(() -> new LumaException("Invalid format: " + map.get("format")));
        }
        if(map.containsKey("oob")){
            settings.oob = true;
        }
        if(map.containsKey("removebroken") && !map.get("removebroken").isEmpty()){
            settings.removeBroken = Boolean.parseBoolean(map.get("removebroken"));
        }
        if(map.containsKey("demohack") && !map.get("demohack").isEmpty()){
            settings.demohack = Boolean.parseBoolean(map.get("demohack"));
        }
        if(map.containsKey("crf") && !map.get("crf").isEmpty()){
            settings.crf = Integer.parseInt(map.get("crf"));
        }
        if(map.containsKey("weighter") && !map.get("weighter").isEmpty()){
            settings.weighterType = Stream.of(RenderWeighterType.values()).filter(w -> w.name().equalsIgnoreCase(map.get("weighter"))).findAny().orElseThrow(() -> new LumaException("Unable to find weigher of type: " + map.get("weighter")));
        }
        if(map.containsKey("pretify")){
            settings.pretify = true;
        }
        if(map.containsKey("twopass") && !map.get("twopass").isEmpty()){
            settings.twoPass = Boolean.parseBoolean(map.get("twopass"));
        }
        if(map.containsKey("no-upload")){
            settings.noUpload = true;
        }

        StringBuilder sb = new StringBuilder();
        if(settings.width == 0) sb.append("\n\t-Width");
        if(settings.height == 0) sb.append("\n\t-Height");
        if(settings.fps == 0) sb.append("\n\t-FPS");

        if(sb.length() > 0){
            throw new LumaException("Missing the following essential parameters: " + sb.toString());
        }

        return settings;
    }

    public static RenderSettings of(JsonObject json) throws LumaException {
        RenderSettings renderSettings = new RenderSettings();
        renderSettings.width = json.get("width").asInt();
        renderSettings.height = json.get("height").asInt();
        renderSettings.fps = json.get("fps").asInt();
        renderSettings.frameblendIndex = json.get("frameblendIndex").asInt();
        renderSettings.crf = json.get("crf").asInt();
        renderSettings.startOdd = json.get("startOdd").asBoolean();
        renderSettings.specifiedStartOdd = json.get("specifiedStartOdd").asBoolean();
        renderSettings.hq = json.get("hq").asBoolean();
        renderSettings.interpolate = json.get("interpolate").asBoolean();
        renderSettings.oob = json.get("oob").asBoolean();
        renderSettings.removeBroken = json.get("removeBroken").asBoolean();
        renderSettings.demohack = json.get("demohack").asBoolean();
        renderSettings.pretify = json.get("pretify").asBoolean();
        renderSettings.twoPass = json.get("twoPass").asBoolean();
        renderSettings.format = Stream.of(VideoOutputFormat.values()).filter(f -> f.name().equals(json.get("format").asString())).findAny().orElseThrow(() -> new LumaException("Unable to find specified format"));
        renderSettings.weighterType = Stream.of(RenderWeighterType.values()).filter(t -> t.name().equals(json.get("weighterType").asString())).findAny().orElseThrow(() -> new LumaException("Unable to find specified weighter type"));
        return renderSettings;
    }

    public RenderSettings duplicate() throws CloneNotSupportedException {
        return (RenderSettings) this.clone();
    }

    public RenderSettings setFormat(VideoOutputFormat format){
        this.format = format;
        return this;
    }

    public int getWidth(){
        return this.width;
    }

    public int getHeight(){
        return this.height;
    }

    public int getFps(){
        return this.fps;
    }

    public int getFrameblendIndex(){
        return this.frameblendIndex;
    }

    public int getCrf(){
        return this.crf;
    }

    public boolean specifiedStartOdd() {
        return specifiedStartOdd;
    }

    public boolean isStartOdd(){
        return this.startOdd;
    }

    public boolean shouldReallyStartOdd(SrcDemo demo){
        return (this.specifiedStartOdd() && this.isStartOdd()) || (!this.specifiedStartOdd() && demo.getFirstPlaybackTick() % 2 == 0);
    }

    public boolean isHq(){
        return this.hq;
    }

    public boolean isInterpolate(){
        return this.interpolate;
    }

    public boolean isOob() {
        return this.oob;
    }

    public boolean shouldRemoveBroken() {
        return this.removeBroken;
    }

    public boolean isDemohack(){
        return this.demohack;
    }

    public boolean isTwoPass(){
        return this.twoPass;
    }

    public VideoOutputFormat getFormat() {
        return format;
    }

    public RenderWeighterType getWeighterType() {
        return weighterType;
    }

    public boolean isPretify() {
        return pretify;
    }

    public int getKBBBitrate(){
        return (int) (width * height * (-32.0487 + 6.574417*crf - 0.5035833*Math.pow(crf, 2) + 0.01708333*Math.pow(crf, 3) - 0.0002166667*Math.pow(crf, 4)));
    }

    public boolean isNoUpload() {
        return noUpload;
    }

    public static void main(String[] args){
        int crf = 18;
        double intermediate = (-32.0487 + 6.574417*crf - 0.5035833*Math.pow(crf, 2) + 0.01708333*Math.pow(crf, 3) - 0.0002166667*Math.pow(crf, 4));
        double finalD = intermediate * 1280 * 720;
        System.out.println(finalD);
    }
}
