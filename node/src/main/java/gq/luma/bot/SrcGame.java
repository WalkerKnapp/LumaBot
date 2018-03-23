package gq.luma.bot;

import java.io.File;

public enum SrcGame {
    PORTAL_2("portal2", "F:\\SteamLibrary\\steamapps\\common\\Portal 2\\portal2", "F:\\SteamLibrary\\steamapps\\common\\Portal 2\\portal2\\cfg", "F:\\SteamLibrary\\steamapps\\common\\Portal 2\\portal2\\console.log", "F:\\SteamLibrary\\steamapps\\common\\Portal 2\\portal2\\maps\\workshop", "F:\\SteamLibrary\\steamapps\\common\\Portal 2\\portal2.exe", "portal2.exe", 620, 644),
    NONE("", "", "", "", "", "", "", 0, 0);
    //PORTAL("portal"),
    //MEL("portal_stories");

    private String directoryName;
    private String gameDir;
    private String configDir;
    private String log;
    private String workshopDir;
    private String exe;
    private String exeName;
    private int appCode;
    private int publishingApp;

    SrcGame(String directoryName, String gameDir, String configDir, String log, String workshopDir, String exe, String exeName, int appCode, int publishingApp){
        this.directoryName = directoryName;
        this.gameDir = gameDir;
        this.configDir = configDir;
        this.log = log;
        this.workshopDir = workshopDir;
        this.exe = exe;
        this.exeName = exeName;
        this.appCode = appCode;
        this.publishingApp = publishingApp;
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
}
