package gq.luma.bot.reference;

import gq.luma.bot.services.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class FileReference implements Service {
    public static File tempDir;
    public static File resDir;
    public static File webRoot;
    public static File localesDir;

    public static File ffprobe;
    public static File youtubeDL;
    public static File sourceDemoParser;

    public static String mySQLLocation;

    @Override
    public void startService() throws Exception {
        Properties properties = new Properties();
        try(FileInputStream fis = new FileInputStream("files.properties")){
            properties.load(fis);
        }
        tempDir = new File(properties.getProperty("temp"));
        resDir = new File(properties.getProperty("res"));
        webRoot = new File(properties.getProperty("web_root"));
        localesDir = new File(properties.getProperty("locales"));
        ffprobe = new File(properties.getProperty("ffprobe"));
        youtubeDL = new File(properties.getProperty("youtube_dl"));
        sourceDemoParser = new File(properties.getProperty("source_demo_parser"));

        mySQLLocation = properties.getProperty("my_sql_location");
    }
}
