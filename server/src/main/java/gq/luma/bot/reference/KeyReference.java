package gq.luma.bot.reference;

import gq.luma.bot.services.Service;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Objects;
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


    @Override
    public void startService() throws Exception {
        if (Files.exists(Paths.get("keys.properties"))) {
            // Read from traditional "keys.properties" file
            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream("keys.properties")) {
                properties.load(fis);
            }

            discordClientId = properties.getProperty("discord_client_id");
            discordClientSecret = properties.getProperty("discord_client_secret");
            discordToken = properties.getProperty("discord_bot_token");

            steamKey = properties.getProperty("steam");

            twitchClientId = properties.getProperty("twitch_client_id");
            twitchClientSecret = properties.getProperty("twitch_client_secret");

            sqlUser = properties.getProperty("sql_user");
            sqlPass = properties.getProperty("sql_pass");
        } else {
            // Try to read from docker secrets, if we're running in docker
            try {
                discordClientId = Files.readString(Paths.get("/run/secrets/luma_discord_client_id"));
                discordClientSecret = Files.readString(Paths.get("/run/secrets/luma_discord_client_secret"));
                discordToken = Files.readString(Paths.get("/run/secrets/luma_discord_bot_token"));

                steamKey = Files.readString(Paths.get("/run/secrets/luma_steam_key"));

                twitchClientId = Files.readString(Paths.get("/run/secrets/luma_twitch_client_id"));
                twitchClientSecret = Files.readString(Paths.get("/run/secrets/luma_twitch_client_secret"));

                sqlUser = Files.readString(Paths.get("/run/secrets/mysql_user"));
                sqlPass = Files.readString(Paths.get("/run/secrets/mysql_pass"));

            } catch (NoSuchFileException e) {
                System.err.println("Docker secrets not found (" + e + "). Falling back to environment variables");

                discordClientId = Objects.requireNonNull(System.getenv("LUMA_DISCORD_CLIENT_ID"), "LUMA_DISCORD_CLIENT_ID not set (and keys.properties, docker secrets are not set instead)");
                discordClientSecret = Objects.requireNonNull(System.getenv("LUMA_DISCORD_CLIENT_SECRET"), "LUMA_DISCORD_CLIENT_SECRET not set (and keys.properties, docker secrets are not set instead)");
                discordToken = Objects.requireNonNull(System.getenv("LUMA_DISCORD_BOT_TOKEN"), "LUMA_DISCORD_BOT_TOKEN not set (and keys.properties, docker secrets are not set instead)");

                steamKey = Objects.requireNonNull(System.getenv("LUMA_STEAM_KEY"), "LUMA_STEAM_KEY not set (and keys.properties, docker secrets are not set instead)");

                twitchClientId = Objects.requireNonNull(System.getenv("LUMA_TWITCH_CLIENT_ID"), "LUMA_TWITCH_CLIENT_ID not set (and keys.properties, docker secrets are not set instead)");
                twitchClientSecret = Objects.requireNonNull(System.getenv("LUMA_TWITCH_CLIENT_SECRET"), "LUMA_TWITCH_CLIENT_SECRET not set (and keys.properties, docker secrets are not set instead)");

                sqlUser = Objects.requireNonNull(System.getenv("MYSQL_USER"), "MYSQL_USER not set (and keys.properties, docker secrets are not set instead)");
                sqlPass = Objects.requireNonNull(System.getenv("MYSQL_PASS"), "MYSQL_PASS not set (and keys.properties, docker secrets are not set instead)");
            }
        }

        discordClientId = discordClientId.trim();
        discordClientSecret = discordClientSecret.trim();
        discordToken = discordToken.trim();
        steamKey = steamKey.trim();
        twitchClientId = twitchClientId.trim();
        twitchClientSecret = twitchClientSecret.trim();
        sqlUser = sqlUser.trim();
        sqlPass = sqlPass.trim();
    }
}
