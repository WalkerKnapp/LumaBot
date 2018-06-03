package gq.luma.bot.systems.filtering.filters;

import clarifai2.dto.model.Model;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import clarifai2.dto.prediction.Logo;
import clarifai2.dto.prediction.Prediction;
import gq.luma.bot.Luma;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.systems.filtering.filters.types.FileFilter;
import gq.luma.bot.systems.filtering.FilteringResult;
import gq.luma.bot.LumaException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ImageFilter extends FileFilter {
    private static final Logger logger = LoggerFactory.getLogger(ImageFilter.class);

    private String modelName;
    private String concept;
    private float threshold;
    private boolean greaterThan;
    private Model<? extends Prediction> model;

    public ImageFilter(ResultSet rs) throws SQLException {
        super(rs);
        this.modelName = typeSettings.get("model_name").asString();
        switch (modelName){
            case "general":
                this.model = Luma.clarifai.getGeneralModel();
                break;
            case "moderation":
                this.model = Luma.clarifai.getModerationModel();
                break;
            case "nsfw":
                this.model = Luma.clarifai.getNsfwModel();
                break;
            case "logo":
                this.model = Luma.clarifai.getLogoModel();
                break;
        }
        this.concept = typeSettings.get("concept").asString();
        this.threshold = typeSettings.get("threshold").asFloat();
        this.greaterThan = typeSettings.get("greater_than").asBoolean();
    }

    @Override
    public Collection<InputType> checkTypes() {
        return Collections.singleton(InputType.IMAGE);
    }

    @Override
    public FilteringResult checkInputStream(InputStream is) throws IOException, LumaException {
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            IOUtils.copy(is, baos);
            logger.debug("Scanning image...");
            List<? extends ClarifaiOutput<? extends Prediction>> outputList = Luma.clarifai.analyzeImage(model, baos.toByteArray());
            for(ClarifaiOutput<? extends Prediction> output : outputList){
                for(Prediction prediction : output.data()){
                    Collection<Concept> conceptList;
                    if(prediction.isConcept()){
                        conceptList = Collections.singleton(prediction.asConcept());
                    } else if(prediction instanceof Logo){
                        conceptList = ((Logo)prediction).concepts();
                    } else {
                        throw new LumaException("Unknown prediction type: " + prediction.getClass().getSimpleName());
                    }
                    for(Concept testConcept : conceptList){
                        logger.debug("Concept {} returned score {}", testConcept.name(), testConcept.value());
                        if(Objects.requireNonNull(testConcept.name()).equalsIgnoreCase(concept) && (greaterThan ? testConcept.value() >= threshold : testConcept.value() <= threshold)){
                            return new FilteringResult(false, "Message predicted to have " +
                                    testConcept.value() + " " + testConcept.name() + ", " +
                                    (greaterThan ? "greater than " : "less than ") + "the threshold of " + threshold);
                        }
                    }
                }
            }
            return new FilteringResult(true, "Message passed all Clarifai tests.");
        }
    }
}
