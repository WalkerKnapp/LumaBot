package gq.luma.bot.services.apis;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import gq.luma.bot.Luma;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;

public class IVerbApi {
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
}
