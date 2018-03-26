package gq.luma.bot.reference;

import gq.luma.bot.services.Service;

import java.io.FileInputStream;
import java.util.Properties;

public class KeyReference implements Service {
    public static String discordToken;
    public static String youtubeKey;
    public static String steamKey;
    public static String keystorePass;
    public static String clarifai;

    public static String sqlUser;
    public static String sqlPass;

    @Override
    public void startService() throws Exception {
        Properties properties = new Properties();
        try(FileInputStream fis = new FileInputStream("keys.properties")){
            properties.load(fis);
        }
        discordToken = properties.getProperty("discord");
        youtubeKey = properties.getProperty("youtube");
        steamKey = properties.getProperty("steam");
        keystorePass = properties.getProperty("keystore");
        clarifai = properties.getProperty("clarifai");

        sqlUser = properties.getProperty("sql_user");
        sqlPass = properties.getProperty("sql_pass");
    }
}
