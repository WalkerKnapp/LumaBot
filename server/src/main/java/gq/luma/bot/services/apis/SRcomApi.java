package gq.luma.bot.services.apis;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import gq.luma.bot.Luma;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;

public class SRcomApi {

    public static void writeConnectionFieldsJson(String userId, JsonGenerator generator) throws IOException {
        Request request = new Request.Builder().url("https://www.speedrun.com/api/v1/users/" + userId).build();
        Call call = Luma.okHttpClient.newCall(request);
        Response response = call.execute();
        if(!response.isSuccessful() || (response.code() < 200 || response.code() >= 300)) {
            return;
        }
        if (response.body() != null) {
            try(InputStream is = response.body().byteStream(); JsonParser jsonParser = Luma.jsonFactory.createParser(is)) {
                // Looking for data.weblink
                int objectDepth = 0;
                while(!jsonParser.isClosed()) {
                    switch (jsonParser.nextToken()) {
                        case START_OBJECT:
                            objectDepth++;
                            break;
                        case END_OBJECT:
                            objectDepth--;
                            break;
                        case FIELD_NAME:
                            if (objectDepth == 2 && "weblink".equals(jsonParser.currentName())) {
                                if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                    return;
                                }
                                generator.writeStringField("link", jsonParser.getValueAsString());
                                jsonParser.close();
                            }
                            break;
                    }
                }
            }
        }

        Request pbsRequest = new Request.Builder().url("https://www.speedrun.com/api/v1/users/" + userId + "/personal-bests?game=Portal_2").build();
        Call pbsCall = Luma.okHttpClient.newCall(pbsRequest);
        Response pbsResponse = pbsCall.execute();
        if(!pbsResponse.isSuccessful() || (pbsResponse.code() < 200 || pbsResponse.code() >= 300)) {
            return;
        }
        if (pbsResponse.body() != null) {
            try(InputStream is = pbsResponse.body().byteStream(); JsonParser jsonParser = Luma.jsonFactory.createParser(is)) {
                // Looking for data[x].place where data[x].run.category = z57o32nm
                int objectDepth = 0;
                int latestPlace = -1;
                int correctDataElement = 0;
                while(!jsonParser.isClosed()) {
                    JsonToken token = jsonParser.nextToken();
                    if(token == null) {
                        //System.err.println("Parsed null token");
                        continue;
                    }
                    switch (token) {
                        case START_OBJECT:
                            objectDepth++;
                            break;
                        case END_OBJECT:
                            objectDepth--;
                            if(correctDataElement != 0 && objectDepth <= correctDataElement) {
                                System.err.println("Could not find 'place' element in run for user " + userId);
                                return;
                            }
                            break;
                        case FIELD_NAME:
                            if ("category".equals(jsonParser.currentName())) {
                                if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                    System.err.println("Category is not a string");
                                    return;
                                }
                                if("jzd33ndn".equals(jsonParser.getValueAsString())) {
                                    if(latestPlace != -1) {
                                        generator.writeNumberField("p2Rank", latestPlace);
                                        return;
                                    } else {
                                        correctDataElement = objectDepth;
                                    }
                                }
                            } else if ("place".equals(jsonParser.currentName())) {
                                if(jsonParser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                                    System.err.println("Place is not an int");
                                    return;
                                }
                                if(correctDataElement != 0) {
                                    generator.writeNumberField("p2Rank", jsonParser.getValueAsInt());
                                    return;
                                } else {
                                    latestPlace = jsonParser.getValueAsInt();
                                }
                            }
                            break;
                    }
                }
                System.err.println("Failed to find any matching run");
            }
        }
    }

    public static boolean requestProfile(long userId, long serverId, String apiKey) throws IOException {
        Request profileRequest = new Request.Builder()
                .url("https://www.speedrun.com/api/v1/profile")
                .addHeader("Accept", "application/json")
                .addHeader("X-API-Key", apiKey)
                .build();
        Call call = Luma.okHttpClient.newCall(profileRequest);
        Response response = call.execute();
        if(!response.isSuccessful() || (response.code() < 200 || response.code() >= 300)) {
            return false;
        }
        if (response.body() != null) {
            try(InputStream is = response.body().byteStream(); JsonParser jsonParser = Luma.jsonFactory.createParser(is)) {
                String srcomId = null;
                String connectionName = null;
                boolean japaneseStored = false;
                int objectDepth = 0;
                // Looking for data.id and data.names.international or data.names.japanese, with international preferred.
                while(!jsonParser.isClosed()) {
                    switch (jsonParser.nextToken()) {
                        case START_OBJECT:
                            objectDepth++;
                            break;
                        case END_OBJECT:
                            objectDepth--;
                            break;
                        case FIELD_NAME:
                            if("id".equals(jsonParser.currentName())) {
                                if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                    return false;
                                }
                                srcomId = jsonParser.getValueAsString();
                                if(connectionName != null && !japaneseStored) {
                                    Luma.database.addVerifiedConnection(userId, serverId, srcomId, "srcom", connectionName, apiKey);
                                    return true;
                                }
                            } else if ("international".equals(jsonParser.currentName()) && objectDepth == 3) {
                                if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                    return false;
                                }
                                connectionName = jsonParser.getValueAsString();
                                japaneseStored = false;
                                if(srcomId != null) {
                                    Luma.database.addVerifiedConnection(userId, serverId, srcomId, "srcom", connectionName, apiKey);
                                    return true;
                                }
                            } else if ("japanese".equals(jsonParser.currentName()) && objectDepth == 3) {
                                if(jsonParser.nextToken() != JsonToken.VALUE_STRING) {
                                    return false;
                                }
                                connectionName = jsonParser.getValueAsString();
                                japaneseStored = true;
                            }
                            break;
                    }
                }
                if(connectionName != null && srcomId != null) {
                    Luma.database.addVerifiedConnection(userId, serverId, srcomId, "srcom", connectionName, apiKey);
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }
}
