package gq.luma.bot;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import gq.luma.bot.utils.LumaException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.IntFunction;

public class SrcGame {

    private static SrcGame[] games;

    static {
        try(FileReader fr = new FileReader("games.json")) {
            games = Json.parse(fr).asArray().values().stream().map(JsonValue::asObject).map(object -> {
                SrcGame game = new SrcGame();
                game.directoryName = object.get("directoryName").asString();
                game.appCode = object.get("appCode").asInt();
                game.publishingApp = object.get("publishingAppCode").asInt();
                game.gameDir = object.get("gameDir").asString();
                game.configDir = object.get("configDir").asString();
                game.log = object.get("logPath").asString();
                game.workshopDir = object.get("workshopDir").asString();
                game.exe = object.get("exePath").asString();
                game.exeName = object.get("exeName").asString();
                return game;
            }).toArray(SrcGame[]::new);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String directoryName;
    private String gameDir;
    private String configDir;
    private String log;
    private String workshopDir;
    private String exe;
    private String exeName;
    private int appCode;
    private int publishingApp;

    private SrcGame(){
        //Unused
    }

    public String getDirectoryName() {
        return directoryName;
    }

    public File getGameDir(){
        return new File(gameDir);
    }

    public File getConfigDir(){
        return new File(configDir);
    }

    public File getLog() {
        return new File(log);
    }

    public File getWorkshopDir() {
        return new File(workshopDir);
    }

    public String getExe() {
        return exe;
    }

    public String getExeName() {
        return exeName;
    }

    public int getAppCode() {
        return appCode;
    }

    public int getPublishingApp(){
        return publishingApp;
    }

    static SrcGame getByDirName(String dirName) throws LumaException {
        for(SrcGame value : games){
            if(dirName.equalsIgnoreCase(value.directoryName)){
                return value;
            }
        }
        throw new LumaException("Unable to parse game: " + dirName);
    }
}
