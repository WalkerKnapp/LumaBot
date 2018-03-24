package gq.luma.bot.systems.srcdemo;

import java.io.File;
import java.util.Optional;
import java.util.stream.Stream;

public enum SrcGame {
    PORTAL_2("portal2"),
    PORTAL("portal"),
    NONE("");

    private String directoryName;

    SrcGame(String directoryName){
        this.directoryName = directoryName;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    public static Optional<SrcGame> getByDirectory(String dir){
        return Stream.of(SrcGame.values()).filter(g -> g.directoryName.equalsIgnoreCase(dir)).findAny();
    }
}
