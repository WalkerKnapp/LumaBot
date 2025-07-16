package gq.luma.bot.reference;

import java.io.File;

public class FileReference {
    public static File webRoot = new File("../web/dist");
    public static File localesDir = new File("locales");

    public static String mySQLLocation = System.getenv().getOrDefault("MYSQL_HOST", "localhost");
}
