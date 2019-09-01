package gq.luma.bot.reference;

import gq.luma.bot.services.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class KeyReference implements Service {
    public static String discordClientId;
    public static String discordClientSecret;
    public static String discordToken;
    public static String youtubeKey;
    public static String steamKey;
    public static String keystorePass;
    public static String clarifai;
    public static String virusTotal;
    public static String twitchClientId;
    public static String twitchClientSecret;

    public static String sqlUser;
    public static String sqlPass;

    public static File gdriveServiceAcc;

    @Override
    public void startService() throws Exception {
        Properties properties = new Properties();
        try(FileInputStream fis = new FileInputStream("keys.properties")){
            properties.load(fis);
        }
        discordClientId = properties.getProperty("discord_client_id");
        discordClientSecret = properties.getProperty("discord_client_secret");
        discordToken = properties.getProperty("discord_bot_token");
        youtubeKey = properties.getProperty("youtube");
        steamKey = properties.getProperty("steam");
        keystorePass = properties.getProperty("keystore");
        clarifai = properties.getProperty("clarifai");
        virusTotal = properties.getProperty("virustotal");
        twitchClientId = properties.getProperty("twitch_client_id");
        twitchClientSecret = properties.getProperty("twitch_client_secret");

        sqlUser = properties.getProperty("sql_user");
        sqlPass = properties.getProperty("sql_pass");

        gdriveServiceAcc = new File(properties.getProperty("gDrive_service_acc"));
    }
}
