package gq.luma.bot.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class FileReference {
    public static final File tempDir;
    public static final File resDir;

    static  {
        Properties properties = new Properties();
        try(FileInputStream fis = new FileInputStream("files.properties")){
            properties.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        }
        tempDir = new File(properties.getProperty("temp"));
        resDir = new File(properties.getProperty("res"));
    }
}
