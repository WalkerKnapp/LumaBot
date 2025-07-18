package gq.luma.bot.commands.subsystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Localization {
    private final Properties properties = new Properties();
    private final String name;

    public Localization(InputStream stream, String name) throws IOException {
        properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        this.name = name;
    }

    public String get(String name){
        if(!properties.containsKey(name)){
            System.err.println("Localization " + this.name + " doesn't contain key " + name);
        }
        return properties.getProperty(name, name + " (No locale. Please contact the developer.)");
    }
}
