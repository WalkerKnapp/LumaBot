package gq.luma.bot.services;

import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import clarifai2.api.request.model.PredictRequest;
import clarifai2.dto.PointF;
import clarifai2.dto.input.ClarifaiImage;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.input.ClarifaiInputValue;
import clarifai2.dto.model.DefaultModels;
import clarifai2.dto.model.LogoModel;
import clarifai2.dto.model.Model;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;
import clarifai2.dto.prediction.Frame;
import clarifai2.dto.prediction.Logo;
import clarifai2.dto.prediction.Prediction;
import gq.luma.bot.Luma;
import gq.luma.bot.reference.KeyReference;

import javax.xml.crypto.Data;
import java.io.File;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Clarifai implements Service {
    private ClarifaiClient client;

    private Model<Concept> generalModel;
    private Model<Concept> moderationModel;
    private Model<Concept> nsfwModel;
    private Model<Logo> logoModel;

    private Model<Frame> nsfwVideoModel;

    @Override
    public void startService() {
        client = new ClarifaiBuilder(KeyReference.clarifai).buildSync();

        generalModel = client.getDefaultModels().generalModel();
        moderationModel = client.getDefaultModels().moderationModel();
        nsfwModel = client.getDefaultModels().nsfwModel();
        logoModel = client.getDefaultModels().logoModel();
        nsfwVideoModel = client.getDefaultModels().nsfwVideoModel();

        Luma.schedulerService.scheduleAtFixedRate(() -> Bot.api.getServers().forEach(server -> {
            try {
                if(ChronoUnit.MONTHS.between(Instant.ofEpochSecond(Luma.database.getServerClarifaiResetDate(server)), Instant.now()) > 1){
                    Luma.database.setServerClarifaiResetDate(server, Instant.now().getEpochSecond());
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }), 0, 3, TimeUnit.HOURS);
    }

    public List<? extends ClarifaiOutput<? extends Prediction>> analyzeImage(Model<? extends Prediction> model, byte[] image){
        PredictRequest<? extends Prediction> request = model.predict().withInputs(ClarifaiInput.forImage(image));
        return request.executeSync().get();
    }

    public List<ClarifaiOutput<Frame>> analyzeVideoNsfw(File videoFile){
        PredictRequest<Frame> videoRequest = nsfwVideoModel.predict().withInputs(ClarifaiInput.forVideo(videoFile));
        return videoRequest.executeSync().get();
    }

    public Model<Concept> getGeneralModel() {
        return generalModel;
    }

    public Model<Concept> getModerationModel() {
        return moderationModel;
    }

    public Model<Concept> getNsfwModel() {
        return nsfwModel;
    }

    public Model<Logo> getLogoModel() {
        return logoModel;
    }
}
