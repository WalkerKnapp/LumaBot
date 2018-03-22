package gq.luma.bot.commands.params.io.input;

import gq.luma.bot.utils.LumaException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface FileInput {

    Pattern EXTENSION_PATTERN = Pattern.compile("(?<=\\.)[0-9a-z]+$");
    Pattern FULL_NAME_PATTERN = Pattern.compile("[.\\w]+?(?=$)");
    Pattern BASE_NAME_PATTERN = Pattern.compile("[.\\w]+?(?=\\.\\w+$)");

    String getInputName();

    String getName() throws IOException, LumaException;

    default String getExtension() throws IOException, LumaException {
        Matcher matcher = EXTENSION_PATTERN.matcher(this.getName());
        if(matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }

    default String getBaseName() throws IOException, LumaException {
        Matcher matcher = BASE_NAME_PATTERN.matcher(this.getName());
        if(matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }

    default InputType getInputType() throws IOException, LumaException {
        String extension = getExtension();
        for(InputType type : InputType.values()){
            for(String ext : type.getExtensions()){
                if(ext.equalsIgnoreCase(extension))
                    return type;
            }
        }
        return InputType.OTHER;
    }

    InputStream getStream() throws IOException;

    default File download(File destination) throws IOException, InterruptedException, LumaException {
        File download = new File(destination, getName());
        FileUtils.copyToFile(getStream(), download);
        return download;
    }

    long getSize() throws IOException, LumaException;
}
