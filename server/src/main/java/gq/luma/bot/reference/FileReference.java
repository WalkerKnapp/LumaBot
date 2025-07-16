package gq.luma.bot.reference;

import gq.luma.bot.services.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class FileReference implements Service {
    public static File webRoot;
    public static File localesDir;

    public static String mySQLLocation;

    @Override
    public void startService() throws Exception {
        Properties properties = new Properties();
        try(FileInputStream fis = new FileInputStream("files.properties")){
            properties.load(fis);
        }
        webRoot = new File(properties.getProperty("web_root"));
        localesDir = new File(properties.getProperty("locales"));

        mySQLLocation = properties.getProperty("my_sql_location");
    }
}
