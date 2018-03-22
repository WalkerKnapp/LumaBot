package gq.luma.bot.utils;

import java.util.regex.Matcher;

import static gq.luma.bot.commands.params.io.input.FileInput.EXTENSION_PATTERN;

public class StringUtilities {
    public static String[] splitString(String string){
        return string.split("\\s+");
    }

    public static String getExtension(String path){
        Matcher matcher = EXTENSION_PATTERN.matcher(path);
        if(matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }

    public static boolean equalsAny(String original, String[] matchers){
        for(String match : matchers){
            if(original.equalsIgnoreCase(match))
                return true;
        }
        return false;
    }

    public static String lastOf(String[] array){
        return array[array.length - 1];
    }
}
