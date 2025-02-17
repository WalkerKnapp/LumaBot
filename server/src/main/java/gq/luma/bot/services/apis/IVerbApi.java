package gq.luma.bot.services.apis;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import gq.luma.bot.Luma;
import gq.luma.bot.services.SkillRoleService;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.input.CountingInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

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
    private static Logger logger = LoggerFactory.getLogger(IVerbApi.class);

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

    public static Long2IntMap getAggregatedSpRanks() throws IOException {
        Long2IntMap aggregatedSpRanks = new Long2IntOpenHashMap();

        Request request = new Request.Builder().url("https://board.portal2.sr/aggregated/sp/json").build();
        Call call = Luma.okHttpClient.newCall(request);
        Response response = call.execute();
        if (!response.isSuccessful() || (response.code() < 200 || response.code() >= 300)) {
            logger.error("Request for aggregated sp ranks failed: success: " + response.isSuccessful() + ", code: " + response.code());
            return null;
        }

        ResponseBody body = response.body();

        if (body == null) {
            logger.error("Request for aggregated sp ranks failed: no body.");
            return null;
        }

        try (InputStream is = body.byteStream(); JsonParser jsonParser = new JsonFactory().createParser(is)) {
            // Looking for points.[uid].scoreData.score
            int objectDepth = 0;
            int depthMatched = 0;

            String currentSteamId = null;

            while (!jsonParser.isClosed()) {
                if (jsonParser.nextToken() == null) {
                    break;
                }

                switch (jsonParser.currentToken()) {
                    case START_OBJECT:
                        objectDepth++;
                        break;
                    case END_OBJECT:
                        objectDepth--;
                        if (depthMatched >= objectDepth) {
                            depthMatched = objectDepth - 1;
                        }
                        break;
                    case FIELD_NAME:
                        switch (objectDepth) {
                            case 1:
                                if ("points".equalsIgnoreCase(jsonParser.currentName())) {
                                    depthMatched = 1;
                                }
                                break;
                            case 2:
                                if (depthMatched == 1) {
                                    currentSteamId = jsonParser.currentName();
                                    depthMatched = 2;
                                }
                                break;
                            case 3:
                                if (depthMatched == 2 && "scoreData".equalsIgnoreCase(jsonParser.currentName())) {
                                    depthMatched = 3;
                                }
                                break;
                            case 4:
                                if (depthMatched == 3 && "score".equalsIgnoreCase(jsonParser.currentName())) {
                                    if(jsonParser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                                        System.err.println("points." + currentSteamId + ".scoreData.score is not a number in https://board.portal2.sr/aggregated/sp/json");
                                        return null;
                                    }
                                    aggregatedSpRanks.put(Long.parseLong(currentSteamId), jsonParser.getValueAsInt());
                                }
                        }
                        break;
                }
            }
        }

        return aggregatedSpRanks;
    }

    public static Long2IntMap getAggregatedCoopRanks() throws IOException {
        Long2IntMap aggregatedSpRanks = new Long2IntOpenHashMap();

        Request request = new Request.Builder().url("https://board.portal2.sr/aggregated/coop/json").build();
        Call call = Luma.okHttpClient.newCall(request);
        Response response = call.execute();
        if (!response.isSuccessful() || (response.code() < 200 || response.code() >= 300)) {
            logger.error("Request for aggregated coop ranks failed: success: " + response.isSuccessful() + ", code: " + response.code());
            return null;
        }

        ResponseBody body = response.body();

        if (body == null) {
            logger.error("Request for aggregated coop ranks failed: no body.");
            return null;
        }

        try (InputStream is = body.byteStream(); JsonParser jsonParser = new JsonFactory().createParser(is)) {
            // Looking for points.[uid].scoreData.score
            int objectDepth = 0;
            int depthMatched = 0;

            String currentSteamId = null;

            while (!jsonParser.isClosed()) {
                if (jsonParser.nextToken() == null) {
                    break;
                }

                switch (jsonParser.currentToken()) {
                    case START_OBJECT:
                        objectDepth++;
                        break;
                    case END_OBJECT:
                        objectDepth--;
                        if (depthMatched >= objectDepth) {
                            depthMatched = objectDepth - 1;
                        }
                        break;
                    case FIELD_NAME:
                        switch (objectDepth) {
                            case 1:
                                if ("points".equalsIgnoreCase(jsonParser.currentName())) {
                                    depthMatched = 1;
                                }
                                break;
                            case 2:
                                if (depthMatched == 1) {
                                    currentSteamId = jsonParser.currentName();
                                    depthMatched = 2;
                                }
                                break;
                            case 3:
                                if (depthMatched == 2 && "scoreData".equalsIgnoreCase(jsonParser.currentName())) {
                                    depthMatched = 3;
                                }
                                break;
                            case 4:
                                if (depthMatched == 3 && "score".equalsIgnoreCase(jsonParser.currentName())) {
                                    if(jsonParser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                                        System.err.println("points." + currentSteamId + ".scoreData.score is not a number in https://board.portal2.sr/aggregated/coop/json");
                                        return null;
                                    }
                                    aggregatedSpRanks.put(Long.parseLong(currentSteamId), jsonParser.getValueAsInt());
                                }
                        }
                        break;
                }
            }
        }

        return aggregatedSpRanks;
    }

    /*public static int getOverallRankFromUserJsonLink(String link) throws IOException {
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
    }*/

    public static long fetchScoreUpdates(int maxDaysAgo, Predicate<ScoreUpdate> takeWhile, Consumer<ScoreUpdate> consumer) throws IOException {
        final String changelogUrl = "https://board.portal2.sr/changelog/json?maxDaysAgo=" + maxDaysAgo;

        Request request = new Request.Builder().url(changelogUrl).header("User-Agent", "LumaBot").build();

        Call call = Luma.okHttpClient.newCall(request);
        Response response = call.execute();

        if(!response.isSuccessful()) {
            throw new IllegalStateException("Request to " + changelogUrl + " failed.");
        }

        if(response.code() < 200 || response.code() >= 300) {
            throw new IllegalStateException("Request to " + changelogUrl + " returned status code " + response.code());
        }

        ResponseBody body = response.body();

        if(body == null) {
            throw new IllegalStateException("Request to " + changelogUrl + " returned no data.");
        }

        try (CountingInputStream is = new CountingInputStream(body.byteStream());
             JsonParser jsonParser = Luma.jsonFactory.createParser(is)) {
            int objectDepth = 0;
            ScoreUpdate currentScoreUpdate = null;

            while(!jsonParser.isClosed() && jsonParser.nextToken() != null) {
                switch (jsonParser.currentToken()) {
                    case START_OBJECT:
                        objectDepth++;
                        if(objectDepth == 1) { // Score update object is starting
                            currentScoreUpdate = new ScoreUpdate();
                            currentScoreUpdate.metadata = new ScoreMetadata();
                        }
                        break;
                    case FIELD_NAME:
                        if(objectDepth == 1) { // The field name is a param in the score update object
                            switch (jsonParser.getCurrentName()) {
                                case "score":
                                    if(currentScoreUpdate == null) {
                                        throw new IllegalStateException("Encountered \"score\" parameter at an unexpected point.");
                                    }
                                    if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                        throw new IllegalStateException("\"score\" param is not a String, instead is a " + jsonParser.currentToken().name());
                                    }
                                    currentScoreUpdate.score = Integer.parseInt(jsonParser.getValueAsString());
                                    break;
                                case "mapid":
                                    if(currentScoreUpdate == null) {
                                        throw new IllegalStateException("Encountered \"mapid\" parameter at an unexpected point.");
                                    }
                                    if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                        throw new IllegalStateException("\"mapid\" param is not a String, instead is a " + jsonParser.currentToken().name());
                                    }
                                    currentScoreUpdate.mapId = Integer.parseInt(jsonParser.getValueAsString());
                                    break;
                                case "post_rank":
                                    if(currentScoreUpdate == null) {
                                        throw new IllegalStateException("Encountered \"post_rank\" parameter at an unexpected point.");
                                    }
                                    jsonParser.nextToken();
                                    if(jsonParser.currentToken() == JsonToken.VALUE_NULL) {
                                        break;
                                    }
                                    if(jsonParser.currentToken() != JsonToken.VALUE_STRING) {
                                        throw new IllegalStateException("\"post_rank\" param is not a String, instead is a " + jsonParser.currentToken().name());
                                    }
                                    currentScoreUpdate.postRank = Integer.parseInt(jsonParser.getValueAsString());
                                    break;
                                case "pre_rank":
                                    if(currentScoreUpdate == null) {
                                        throw new IllegalStateException("Encountered \"pre_rank\" parameter at an unexpected point.");
                                    }
                                    jsonParser.nextToken();
                                    if(jsonParser.currentToken() == JsonToken.VALUE_NULL) {
                                        break;
                                    }
                                    if(jsonParser.currentToken() != JsonToken.VALUE_STRING) {
                                        throw new IllegalStateException("\"pre_rank\" param is not a String, instead is a " + jsonParser.currentToken().name());
                                    }
                                    currentScoreUpdate.preRank = Integer.parseInt(jsonParser.getValueAsString());
                                    break;
                                case "profile_number":
                                    if(currentScoreUpdate == null) {
                                        throw new IllegalStateException("Encountered \"profile_number\" parameter at an unexpected point.");
                                    }
                                    if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                        throw new IllegalStateException("\"profile_number\" param is not a String, instead is a " + jsonParser.currentToken().name());
                                    }
                                    currentScoreUpdate.metadata.steamId = Long.parseLong(jsonParser.getValueAsString());
                                    break;
                                case "time_gained":
                                    if(currentScoreUpdate == null) {
                                        throw new IllegalStateException("Encountered \"time_gained\" parameter at an unexpected point.");
                                    }
                                    if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                        throw new IllegalStateException("\"time_gained\" param is not a String, instead is a " + jsonParser.currentToken().name());
                                    }
                                    String originalDate = jsonParser.getValueAsString();
                                    // Change the date format to be compliant with ISO instants
                                    String isoDate = originalDate.replace(' ', 'T') + ".00Z";
                                    currentScoreUpdate.metadata.timeGained = Instant.parse(isoDate);
                                    break;
                                default:
                                    break;
                            }
                        }
                        break;
                    case END_OBJECT:
                        objectDepth--;
                        if(objectDepth == 0) { // Score update object is finished
                            if(currentScoreUpdate == null) {
                                throw new IllegalStateException("Unexpected data format.");
                            }
                            if(currentScoreUpdate.score == -1) {
                                throw new IllegalStateException("Score update did not contain \"score\" parameter.");
                            }
                            if(currentScoreUpdate.mapId == -1) {
                                throw new IllegalStateException("Score update did not contain \"mapid\" parameter.");
                            }
                            if(currentScoreUpdate.postRank == -1) {
                                logger.debug("Score update did not contain \"post_rank\" parameter.");
                                break;
                            }
                            if(currentScoreUpdate.metadata.steamId == -1) {
                                throw new IllegalStateException("Score update did not contain \"profile_number\" parameter.");
                            }
                            if(currentScoreUpdate.metadata.timeGained == null) {
                                throw new IllegalStateException("Score update did not contain \"time_gained\" parameter.");
                            }

                            // Keep taking scores while takeWhile returns true, otherwise end
                            if(takeWhile.test(currentScoreUpdate)) {
                                // Pass the score to the caller.
                                consumer.accept(currentScoreUpdate);
                            } else {
                                return is.getByteCount();
                            }
                        }
                        break;
                    default:
                        break;
                }
            }

            return is.getByteCount();
        }
    }

    public static long fetchMapScores(int mapId, BiConsumer<Integer, ScoreMetadata> consumer) throws IOException {
        final String mapDataUrl = "https://board.portal2.sr/chamber/" + mapId + "/json";
        logger.debug("Making request to " + mapDataUrl);

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

        try (CountingInputStream is = new CountingInputStream(response.body().byteStream()); JsonParser jsonParser = Luma.jsonFactory.createParser(is)) {

            int objectDepth = 0;
            ScoreMetadata currentScoreMetadata = null;
            int currentScore = -1;

            while(!jsonParser.isClosed() && jsonParser.nextToken() != null) {
                switch (jsonParser.currentToken()) {
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
                                    jsonParser.nextToken();
                                    if (jsonParser.currentToken() == JsonToken.VALUE_STRING) {
                                        currentScore = Integer.parseInt(jsonParser.getValueAsString());
                                    } else if (jsonParser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                                        currentScore = jsonParser.getValueAsInt();
                                    } else {
                                        throw new IllegalStateException("\"score\" param is not a String or int, instead is a " + jsonParser.currentToken().name());
                                    }
                                    break;
                                case "date":
                                    if(currentScoreMetadata == null) {
                                        throw new IllegalStateException("Encountered \"date\" parameter at an unexpected point.");
                                    }
                                    jsonParser.nextToken();
                                    if(jsonParser.currentToken() == JsonToken.VALUE_NULL) {
                                        break;
                                    }
                                    if(jsonParser.currentToken() != JsonToken.VALUE_STRING) {
                                        throw new IllegalStateException("\"date\" param is not a String, instead is a " + jsonParser.currentToken().name());
                                    }
                                    String originalDate = jsonParser.getValueAsString();
                                    String isoDate = originalDate.replace(' ', 'T') + ".00Z"; // Change the date format to be compliant with ISO Instants.
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

            return is.getByteCount();
        }
    }
}
