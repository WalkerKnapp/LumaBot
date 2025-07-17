package gq.luma.bot.reference;

import gq.luma.bot.services.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class KeyReference implements Service {
    public static String discordClientId;
    public static String discordClientSecret;
    public static String discordToken;
    public static String steamKey;
    public static String twitchClientId;
    public static String twitchClientSecret;

    public static String sqlUser;
    public static String sqlPass;

    private String loadKey(String propName, String envName, Properties props) {
        if (props != null && props.containsKey(propName)) {
            return props.getProperty(propName).trim();
        }

        if (System.getenv(envName) != null) {
            return System.getenv(envName).trim();
        }

        if (System.getenv(envName + "_FILE") != null) {
            try {
                return Files.readString(Path.of(System.getenv(envName + "_FILE"))).trim();
            } catch (IOException e) {
                System.err.println("Encountered error while trying to read from the contents of " + envName + "_FILE");
            }
        }

        throw new IllegalArgumentException("Failed to load property " + propName + " from keys.properties, " + envName + " from env variables, or contents of " + envName + "_FILE.");
    }


    @Override
    public void startService() throws Exception {
        Properties properties = null;
        if (Files.exists(Paths.get("keys.properties"))) {
            // Read from traditional "keys.properties" file
            properties = new Properties();
            try (FileInputStream fis = new FileInputStream("keys.properties")) {
                properties.load(fis);
            }
        }

        discordClientId = loadKey("discord_client_id", "DISCORD_CLIENT_ID", properties);
        discordClientSecret = loadKey("discord_client_secret", "DISCORD_CLIENT_SECRET", properties);
        discordToken = loadKey("discord_bot_token", "DISCORD_BOT_TOKEN", properties);

        steamKey = loadKey("steam", "STEAM_KEY", properties);

        twitchClientId = loadKey("twitch_client_id", "TWITCH_CLIENT_ID", properties);
        twitchClientSecret = loadKey("twitch_client_secret", "TWITCH_CLIENT_SECRET", properties);

        sqlUser = loadKey("sql_user", "MYSQL_USER", properties);
        sqlPass = loadKey("sql_pass", "MYSQL_PASS", properties);
    }
}
