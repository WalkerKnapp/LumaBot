package gq.luma.bot.systems.demorender;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.utils.LumaException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RenderSettings {

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

    private VideoOutputFormat format = VideoOutputFormat.H264;
    private RenderWeighterType weighterType = RenderWeighterType.GAUSSIAN;

    JsonObject serialize(){
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

        StringBuilder sb = new StringBuilder();
        if(settings.width == 0) sb.append("\n\t-Width");
        if(settings.height == 0) sb.append("\n\t-Height");
        if(settings.fps == 0) sb.append("\n\t-FPS");

        if(sb.length() > 0){
            throw new LumaException("Missing the following essential parameters: " + sb.toString());
        }

        return settings;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
