package gq.luma.bot.systems.srcdemo;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.utils.FileUtilities;
import gq.luma.bot.LumaException;

import java.io.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public JsonObject serialize(){
        try {
            return new JsonObject()
                    .add("filestamp", this.filestamp)
                    .add("protocol", this.protocol)
                    .add("networkProtocol", this.networkProtocol)
                    .add("game", this.game.getDirectoryName())
                    .add("mapName", this.mapName)
                    .add("serverName", this.serverName)
                    .add("clientName", this.clientName)
                    .add("playbackTime", Float.isFinite(this.playbackTime) ? this.playbackTime : 0)
                    .add("playbackTicks", this.playbackTicks)
                    .add("playbackFrames", this.playbackFrames)
                    .add("signOnLength", this.signOnLength)
                    .add("signOnString", this.signOnString)
                    .add("firstRealTick", this.firstRealTick)
                    .add("associatedFile", this.associatedFile);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private SrcDemo(BufferedReader reader, boolean requireKnownGame) throws IOException, LumaException {
        String line;
        while((line = reader.readLine()) != null) {
            System.out.println("Line: " + line);
            String[] splitLine = line.split("\t");
            if(splitLine.length < 2){
                throw new LumaException("Unable to parse demo file. Unknown or invalid format");
            }
            switch (splitLine[0]) {
                case "FileStamp":
                    filestamp = splitLine[1];
                    break;
                case "Protocol":
                    protocol = Integer.parseInt(splitLine[1]);
                    break;
                case "NetworkProtocol":
                    networkProtocol = Integer.parseInt(splitLine[1]);
                    break;
                case "GameDirectory":
                    Optional<SrcGame> gameTest = SrcGame.getByDirectory(splitLine[1]);
                    if(requireKnownGame) {
                        game = gameTest.orElseThrow(() -> new LumaException("Unknown game directory: " + splitLine[1]));
                    } else gameTest.ifPresent(srcGame -> game = srcGame);
                    break;
                case "MapName":
                    mapName = splitLine[1];
                    break;
                case "ServerName":
                    serverName = splitLine[1];
                    break;
                case "ClientName":
                    clientName = splitLine[1];
                    break;
                case "PlaybackTime":
                    playbackTime = Float.parseFloat(splitLine[1]);
                    break;
                case "PlaybackTicks":
                    playbackTicks = Integer.parseInt(splitLine[1]);
                    break;
                case "PlaybackFrames":
                    playbackFrames = Integer.parseInt(splitLine[1]);
                    break;
                case "SignOnLength":
                    signOnLength = Integer.parseInt(splitLine[1]);
                    break;
                default:
                    throw new LumaException("Unable to parse demo file. Unknown or invalid format");
            }
        }
        reader.close();
    }


    public static SrcDemo of(File file, boolean requireKnownGame) throws IOException, LumaException {
        ProcessBuilder pb;
        String operSys = System.getProperty("os.name").toLowerCase();
        System.out.println("OS: " + operSys);
        if(operSys.contains("nix") || operSys.contains("nux")
                || operSys.contains("aix")){
            pb = new ProcessBuilder("sudo", "mono", FileReference.sourceDemoParser.getAbsolutePath(), "adjust;header", file.getAbsolutePath());
        } else {
            pb = new ProcessBuilder(FileReference.sourceDemoParser.getAbsolutePath(), "adjust;header", file.getAbsolutePath());
        }
        System.out.println("Sending command: " + String.join(" ", pb.command()));
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        Process p = pb.start();
        SrcDemo srcDemo = new SrcDemo(new BufferedReader(new InputStreamReader(p.getInputStream())), requireKnownGame);
        p.destroyForcibly();

        srcDemo.associatedFile = FileUtilities.relatavizePathToTemp(file);

        byte[] signOnBuffer = new byte[76];
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.seek(0x4F4L);
        raf.readFully(signOnBuffer);
        srcDemo.signOnString = new String(signOnBuffer);

        System.out.println(srcDemo.signOnString);

        ProcessBuilder pb2;
        if(operSys.contains("nix") || operSys.contains("nux")
                || operSys.contains("aix")) {
            pb2 = new ProcessBuilder("sudo", "mono", FileReference.sourceDemoParser.getAbsolutePath(), "packets", file.getAbsolutePath());
        } else {
            pb2 = new ProcessBuilder(FileReference.sourceDemoParser.getAbsolutePath(), "packets", file.getAbsolutePath());
        }
        System.out.println("Sending command: " + String.join(" ", pb2.command()));
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        Process p2 = pb2.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p2.getInputStream()));
        String line;
        while((line = br.readLine()) != null){
            Matcher matcher = PACKET_PATTERN.matcher(line);
            if(matcher.find()){
                if(!matcher.group("pointer").equalsIgnoreCase("0")) {
                    srcDemo.firstRealTick = Integer.parseInt(matcher.group("pointer"));
                    break;
                }
            }
        }
        br.close();
        p2.destroyForcibly();

        return srcDemo;
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
