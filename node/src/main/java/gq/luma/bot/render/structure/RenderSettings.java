package gq.luma.bot.render.structure;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.SrcDemo;
import gq.luma.bot.utils.LumaException;

import java.util.stream.Stream;

public class RenderSettings {

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

    private VideoOutputFormat format = VideoOutputFormat.H264;
    private RenderWeighterType weighterType = RenderWeighterType.GAUSSIAN;

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
}
