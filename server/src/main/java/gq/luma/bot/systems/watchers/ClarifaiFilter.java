package gq.luma.bot.systems.watchers;

import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.events.message.MessageCreateEvent;
import de.btobastian.javacord.listeners.message.MessageCreateListener;
import gq.luma.bot.Luma;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.params.io.input.FileInput;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.services.Database;
import gq.luma.bot.utils.LumaException;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ClarifaiFilter implements MessageCreateListener {
    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        event.getServer().ifPresent(server -> Luma.lumaExecutorService.submit(() -> {
            try {
                ResultSet rs = Luma.database.getServerFilters(server);
                if(rs.next()) {
                    FileInput input = ParamUtilities.analyzeMessage(event.getMessage(), InputType.IMAGE, InputType.VIDEO);
                    if (input != null) {
                        do {
                            String thresholds = rs.getString("thresholds");
                            String application = rs.getString("application");
                            switch (application){
                                case "image":
                                    if(input.getInputType() != InputType.IMAGE) break;
                                    System.out.println("Found image filter+input, analyzing...");
                                    try(InputStream is = input.getStream()) {
                                        List<ClarifaiOutput<Concept>> outputList = Luma.clarifai.analyzeImageModeration(is.readAllBytes());
                                        AtomicInteger removalCount = new AtomicInteger();
                                        outputList.forEach(output -> output.data().forEach(concept -> Stream.of(thresholds.split(";")).forEach(threshold -> {
                                            System.out.println("Found concept: " + concept.name() + " - " + concept.value());
                                            if(threshold.split(":")[0].equals(concept.name())){
                                                char delim = threshold.split(":")[1].charAt(0);
                                                float thresholdFloat = Float.parseFloat(threshold.split(":")[1].substring(1));
                                                if((delim == '>' && concept.value() >= thresholdFloat) || (delim == '<' && concept.value() <= thresholdFloat)){
                                                    System.out.println("Adding on " + concept.value() + " and " + thresholdFloat);
                                                    removalCount.getAndIncrement();
                                                }
                                            }
                                        })));
                                        if(removalCount.get() > 0){
                                            event.getMessage().delete().exceptionally(Javacord::exceptionLogger);
                                            event.getChannel().sendMessage("<@" + event.getMessage().getAuthor().getIdAsString() + ">, your image, **" + input.getName() + "**, has been removed for violating the servers filters on **" + removalCount.get() + "** " + (removalCount.get() > 1 ? "counts" : "count") + ". If this is in error, please contact a moderator.");
                                        }
                                    }
                                    break;
                                case "video":
                                    if(input.getInputType() != InputType.VIDEO) break;
                                    File tempDl = new File(FileReference.tempDir, input.getName());
                                    try(InputStream is = input.getStream();
                                        FileOutputStream fos = new FileOutputStream(tempDl)){
                                        IOUtils.copy(is, fos);
                                    }
                                    Luma.clarifai.analyzeVideoNsfw(tempDl);
                                    break;
                            }
                        } while(rs.next());
                    }
                }
            } catch (LumaException | IOException | SQLException e) {
                e.printStackTrace();
            }
        }));

    }
}
