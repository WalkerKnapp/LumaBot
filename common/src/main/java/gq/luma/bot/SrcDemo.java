package gq.luma.bot;

import com.eclipsesource.json.JsonObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.util.Scanner;
import java.util.concurrent.CompletionException;

public class SrcDemo {
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
        demo.game = SrcGame.getByDirName(json.get("game").asString());
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

    public static SrcDemo parse(String associatedFile, InputStream is) throws IOException, LumaException {
        SrcDemo result = new SrcDemo();

        result.filestamp = nextString(is, 8);

        if(!result.filestamp.equals("HL2DEMO\0")){
            throw new LumaException("Unknown demo file: Not HL2 Branch");
        }

        result.protocol = nextInt(is);
        result.networkProtocol = nextInt(is);
        result.serverName = nextString(is, 260);
        result.clientName = nextString(is, 260);
        result.mapName = nextString(is, 260);
        result.game = SrcGame.getByDirName(nextString(is, 260));
        result.playbackTime = nextFloat(is);
        result.playbackTicks = nextInt(is);
        result.playbackFrames = nextInt(is);
        result.signOnLength = nextInt(is);

        result.associatedFile = associatedFile;

        return result;
    }

    private static int nextInt(InputStream is) throws IOException, LumaException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        int read;
        int i = 0;
        while ((read = is.read()) != -1 && i < 4){
            buffer.put((byte) read);
            i++;
        }
        if(read == -1){
            throw new LumaException("Prematurely reached end of demo file.");
        }
        return buffer.getInt();
    }

    private static float nextFloat(InputStream is) throws LumaException, IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        int read;
        int i = 0;
        while ((read = is.read()) != -1 && i < 4){
            buffer.put((byte) read);
            i++;
        }
        if(read == -1){
            throw new LumaException("Prematurely reached end of demo file.");
        }
        return buffer.getFloat();
    }

    private static String nextString(InputStream is, int bytes) throws IOException, LumaException {
        byte[] cBuf = new byte[bytes];
        int read;
        int i = 0;
        while ((read = is.read()) != -1 && i < bytes){
            cBuf[i] = (byte) read;
        }
        if(read == -1){
            throw new LumaException("Prematurely reached end of demo file.");
        }
        return new String(cBuf);
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
