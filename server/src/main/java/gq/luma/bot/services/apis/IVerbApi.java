package gq.luma.bot.services.apis;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import gq.luma.bot.Luma;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.*;
import java.util.stream.*;

public class IVerbApi {
    public static class ScoreUpdate {
        public int score = -1;
        public int preRank = -1;
        public int postRank = -1;
        public int mapId = -1;

        public ScoreMetadata metadata = null;
    }

    public static class ScoreMetadata {
        public long steamId = -1;
        public Instant timeGained = null;
    }

    public static int getOverallRankFromUserJsonLink(String link) throws IOException {
        Request request = new Request.Builder().url(link).build();
        Call call = Luma.okHttpClient.newCall(request);
        Response response = call.execute();
        if(!response.isSuccessful() || (response.code() < 200 || response.code() >= 300)) {
            return -1;
        }
        if (response.body() != null) {
            try(InputStream is = response.body().byteStream(); JsonParser jsonParser = Luma.jsonFactory.createParser(is)) {
                // Looking for points."global"."score"
                int objectDepth = 0;
                int pointsIdentified = 0;
                while(!jsonParser.isClosed()) {
                    switch (jsonParser.nextToken()) {
                        case START_OBJECT:
                            objectDepth++;
                            break;
                        case END_OBJECT:
                            objectDepth--;
                            if(objectDepth <= pointsIdentified) {
                                System.err.println("Could not find points.global.score in iverb result for user " + link);
                                return -1;
                            }
                            break;
                        case FIELD_NAME:
                            switch (objectDepth) {
                                case 1:
                                    if("points".equals(jsonParser.currentName())) {
                                        pointsIdentified = 1;
                                    }
                                    break;
                                case 2:
                                    if("global".equals(jsonParser.getCurrentName()) && pointsIdentified == 1) {
                                        pointsIdentified = 2;
                                    }
                                case 3:
                                    if("scoreRank".equals(jsonParser.getCurrentName()) && pointsIdentified == 2) {
                                        if(jsonParser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                                            System.err.println("points.global.score is not a number user " + link);
                                            return -1;
                                        }
                                        return jsonParser.getValueAsInt();
                                    }
                            }
                            break;
                    }
                }
                System.err.println("Could not find points.global.score in iverb result for user " + link);
                return -1;
            }
        } else {
            return -1;
        }
    }

    public static long fetchScoreUpdates(int maxDaysAgo, Predicate<ScoreUpdate> takeWhile, Consumer<ScoreUpdate> consumer) throws IOException {
        final String changelogUrl = "https://board.iverb.me/changelog/json?maxDaysAgo=" + maxDaysAgo;

        Request request = new Request.Builder().url(changelogUrl).header("User-Agent", "LumaBot").build();

        Call call = Luma.okHttpClient.newCall(request);
        Response response = call.execute();

        if(!response.isSuccessful()) {
            throw new IllegalStateException("Request to " + changelogUrl + " failed.");
        }

        if(response.code() < 200 || response.code() >= 300) {
            throw new IllegalStateException("Request to " + changelogUrl + " returned status code " + response.code());
        }

        if(response.body() == null) {
            throw new IllegalStateException("Request to " + changelogUrl + " returned no data.");
        }

        try (InputStream is = response.body().byteStream(); JsonParser jsonParser = Luma.jsonFactory.createParser(is)) {
            int objectDepth = 0;
            ScoreUpdate currentScoreUpdate = null;

            while(!jsonParser.isClosed()) {
                switch (jsonParser.nextToken()) {
                    case START_OBJECT:
                        objectDepth++;
                        if(objectDepth == 1) { // Score update object is starting
                            currentScoreUpdate = new ScoreUpdate();
                            currentScoreUpdate.metadata = new ScoreMetadata();
                        }
                        break;
                    case END_OBJECT:
                        objectDepth--;
                        if(objectDepth == 0) { // Score update object is finished
                            if(currentScoreUpdate == null) {
                                throw new IllegalStateException("Unexpected data format.");
                            }

                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public static long fetchMapScores(int mapId, BiConsumer<Integer, ScoreMetadata> consumer) throws IOException {
        final String mapDataUrl = "https://board.iverb.me/chamber/" + mapId + "/json";

        Request request = new Request.Builder().url(mapDataUrl).header("User-Agent", "LumaBot").build();

        Call call = Luma.okHttpClient.newCall(request);
        Response response = call.execute();

        if(!response.isSuccessful()) {
            throw new IllegalStateException("Request to " + mapDataUrl + " failed.");
        }

        if(response.code() < 200 || response.code() >= 300) {
            throw new IllegalStateException("Request to " + mapDataUrl + " returned status code " + response.code());
        }

        if(response.body() == null) {
            throw new IllegalStateException("Request to " + mapDataUrl + " returned no data.");
        }

        try (InputStream is = response.body().byteStream(); JsonParser jsonParser = Luma.jsonFactory.createParser(is)) {

            int objectDepth = 0;
            ScoreMetadata currentScoreMetadata = null;
            int currentScore = -1;

            while(!jsonParser.isClosed()) {
                switch (jsonParser.nextToken()) {
                    case START_OBJECT:
                        objectDepth++;
                        break;
                    case FIELD_NAME:
                        if(objectDepth == 1) { // The field name is a user ID, the object is the score info.
                            currentScoreMetadata = new ScoreMetadata();
                            currentScoreMetadata.steamId = Long.parseLong(jsonParser.getCurrentName());
                        } else if (objectDepth == 3) { // The field name is a param inside the scoreData or userData object.
                            switch (jsonParser.getCurrentName()) {
                                case "score":
                                    if(currentScoreMetadata == null) {
                                        throw new IllegalStateException("Encountered \"score\" parameter at an unexpected point.");
                                    }
                                    if(!(jsonParser.nextToken() == JsonToken.VALUE_STRING)) {
                                        throw new IllegalStateException("\"score\" param is not a String.");
                                    }
                                    currentScore = Integer.parseInt(jsonParser.getValueAsString());
                                    break;
                                case "date":
                                    if(currentScoreMetadata == null) {
                                        throw new IllegalStateException("Encountered \"score\" parameter at an unexpected point.");
                                    }
                                    if(!(jsonParser.nextToken() == JsonToken.VALUE_STRING)) {
                                        throw new IllegalStateException("\"date\" param is not a String.");
                                    }
                                    String originalDate = jsonParser.getValueAsString();
                                    String isoDate = originalDate.replace(' ', 'T'); // Change the date format to be compliant with ISO Instants.
                                    currentScoreMetadata.timeGained = Instant.parse(isoDate);
                                    break;
                                default:
                                    break;
                            }
                        }
                        break;
                    case END_OBJECT:
                        objectDepth--;
                        if(objectDepth == 1) { // Finished score object
                            if(currentScoreMetadata == null) {
                                throw new IllegalStateException("Unexpected Data Formatting");
                            }
                            if(currentScoreMetadata.timeGained == null) {
                                throw new IllegalStateException("Score returned from " + mapDataUrl + " request has no date parameter.");
                            }
                            if(currentScoreMetadata.steamId == -1) {
                                throw new IllegalStateException("Score returned from " + mapDataUrl + " had no associated player.");
                            }
                            if(currentScore == -1) {
                                throw new IllegalStateException("Score returned from " + mapDataUrl + " had no score parameter.");
                            }

                            consumer.accept(currentScore, currentScoreMetadata);

                            currentScoreMetadata = null;
                            currentScore = -1;
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        return response.body().contentLength();
    }
}
