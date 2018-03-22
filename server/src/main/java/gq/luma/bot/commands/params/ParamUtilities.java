package gq.luma.bot.commands.params;

import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.MessageAttachment;
import gq.luma.bot.commands.params.io.input.*;
import gq.luma.bot.utils.LumaException;
import gq.luma.bot.utils.StringUtilities;
import org.apache.commons.validator.routines.UrlValidator;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ParamUtilities {

    private static UrlValidator urlValidator = new UrlValidator();

    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile("(?<=filename=)([^;=$]*)");

    public static Map<String, String> getParams(String paramsString){
        String[] splitParams = StringUtilities.splitString(paramsString);
        HashMap<String, String> params = new HashMap<>();
        if(splitParams.length > 0 && Stream.of(splitParams).map(String::isEmpty).anyMatch(bool -> !bool)) {
            for (int i = 0; i < splitParams.length; i++) {
                if (splitParams[i].startsWith("-")) {
                    if (splitParams.length > i + 1 && !splitParams[i + 1].startsWith("-")) {
                        params.put(splitParams[i].substring(1).toLowerCase(), splitParams[i + 1]);
                        i++;
                    } else {
                        params.put(splitParams[i].substring(1).toLowerCase(), "");
                    }
                } else {
                    System.err.println("Failed to interpret " + splitParams[i] + ". Expected dash.");
                }
            }
        }
        return params;
    }

    public static FileInput getInput(Message originalMessage, InputType... inputTypes) throws MalformedURLException, LumaException {
        FileInput originalAnalysis = analyzeMessage(originalMessage, inputTypes);
        if(originalAnalysis != null) return originalAnalysis;

        NavigableSet<Message> history = originalMessage.getMessagesBefore(100).join().descendingSet();

        for(Message message : history){
            FileInput analysis = analyzeMessage(message, inputTypes);
            if(analysis != null) return analysis;
        }

        throw new LumaException("Failed to find a valid file in the last 100 messages.");
    }

    public static FileInput analyzeMessage(Message message, InputType... types) throws MalformedURLException {
        ArrayList<String> arrayList = new ArrayList<>();
        Stream.of(types).map(InputType::getExtensions).forEach(strings -> arrayList.addAll(Arrays.asList(strings)));
        String[] allExtensions = arrayList.toArray(new String[0]);

        for(MessageAttachment attachment : message.getAttachments()){
            if(StringUtilities.equalsAny(StringUtilities.getExtension(attachment.getFileName()), allExtensions)){
                return new AttachmentInput(attachment);
            }
        }

        String[] split = StringUtilities.splitString(message.getContent());
        for(String url : split){
            FileInput urlIn = getURLInput(url, allExtensions, types);
            if(urlIn != null)
                return urlIn;
        }
        return null;
    }

    public static FileInput getURLInput(String url, String[] extensions, InputType[] types) throws MalformedURLException {
        if(urlValidator.isValid(url)){
            if(StringUtilities.equalsAny(StringUtilities.getExtension(url), extensions)){
                return new RawUrlInput(new URL(url));
            }
            else if(url.contains("youtu") && Arrays.asList(types).contains(InputType.VIDEO)){
                try{
                    return new YoutubeInput(url);
                }
                catch (MalformedURLException ignored){ }
            }
            else{
                try {
                    URL javaUrl = new URL(url);
                    URLConnection connection = javaUrl.openConnection();
                    if(connection.getHeaderField("Content-Disposition") != null){
                        System.out.println("Found content disposition of: " + connection.getHeaderField("Content-Disposition"));
                        Matcher matcher = CONTENT_DISPOSITION_PATTERN.matcher(connection.getHeaderField("Content-Disposition"));
                        if(matcher.find()){
                            System.out.println("Parsed filename of: " + matcher.group(0));
                            return new IndirectUrlInput(javaUrl, matcher.group(0));
                        }
                    }
                } catch (IOException ignored) { }
            }
        }
        return null;
    }
}
