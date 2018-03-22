package gq.luma.bot.reference;

import gq.luma.bot.services.Service;

import java.io.FileInputStream;
import java.util.Properties;

public class KeyReference implements Service {
    public static String discordToken;
    public static String youtubeKey;
    public static String imgurId;
    public static String steamKey;
    public static String keystorePass;
    public static String smbPass;
    public static String gDriveToken;
    public static String clarifai;

    @Override
    public void startService() throws Exception {
        Properties properties = new Properties();
        try(FileInputStream fis = new FileInputStream("keys.properties")){
            properties.load(fis);
        }
        discordToken = properties.getProperty("discord");
        youtubeKey = properties.getProperty("youtube");
        imgurId = properties.getProperty("imgur");
        steamKey = properties.getProperty("steam");
        keystorePass = properties.getProperty("keystore");
        smbPass = properties.getProperty("smb");
        gDriveToken = properties.getProperty("gDrive");
        clarifai = properties.getProperty("clarifai");
    }
}
