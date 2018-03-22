package gq.luma.bot;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.utils.LumaException;

import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SrcDemo {

    private static final Pattern PACKET_PATTERN = Pattern.compile("(?<=Packet\\t\\[)(?<pointer>\\d*):(?<size>\\d*)(?=]\\t)");

    private String filestamp;
    private int protocol;
    private int networkProtocol;
    private SrcGame game;
    private String mapName;
    private String serverName;
    private String clientName;
    private float playbackTime;
    private int playbackTicks;
    private int playbackFrames;
    private int signOnLength;

    private String signOnString;
    private int firstRealTick;

    private String associatedFile;

    public static SrcDemo of(JsonObject json) throws LumaException {
        SrcDemo demo = new SrcDemo();
        demo.filestamp = json.get("filestamp").asString();
        demo.protocol = json.get("protocol").asInt();
        demo.networkProtocol = json.get("networkProtocol").asInt();
        demo.game = Stream.of(SrcGame.values()).filter(g -> g.name().equals(json.get("game").asString())).findAny().orElseThrow(() -> new LumaException("Unable to parse game."));
        demo.mapName = json.get("mapName").asString();
        demo.serverName = json.get("serverName").asString();
        demo.clientName = json.get("clientName").asString();
        demo.playbackTime = json.get("playbackTime").asFloat();
        demo.playbackTicks = json.get("playbackTicks").asInt();
        demo.playbackFrames = json.get("playbackFrames").asInt();
        demo.signOnLength = json.get("signOnLength").asInt();
        demo.signOnString = json.get("signOnString").asString();
        demo.firstRealTick = json.get("firstRealTick").asInt();
        demo.associatedFile = json.get("associatedFile").asString();
        return demo;
    }

    public static SrcDemo ofUnchecked(JsonObject json){
        try {
            return of(json);
        } catch (LumaException e) {
            throw new CompletionException(e);
        }
    }

    public String getFilestamp() {
        return filestamp;
    }

    public int getProtocol() {
        return protocol;
    }

    public int getNetworkProtocol() {
        return networkProtocol;
    }

    public SrcGame getGame() {
        return game;
    }

    public String getMapName() {
        return mapName;
    }

    public String getServerName() {
        return serverName;
    }

    public String getClientName() {
        return clientName;
    }

    public float getPlaybackTime() {
        return playbackTime;
    }

    public int getPlaybackTicks() {
        return playbackTicks;
    }

    public int getPlaybackFrames() {
        return playbackFrames;
    }

    public int getSignOnLength() {
        return signOnLength;
    }

    public String getSignOnString() {
        return signOnString;
    }

    public int getFirstPlaybackTick() {
        return firstRealTick;
    }

    public String getAssociatedFile() {
        return associatedFile;
    }
}
