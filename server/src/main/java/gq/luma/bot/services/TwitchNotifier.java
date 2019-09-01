package gq.luma.bot.services;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import gq.luma.bot.Luma;
import gq.luma.bot.reference.KeyReference;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class TwitchNotifier implements Service {
    private static final Logger logger = LoggerFactory.getLogger(TwitchNotifier.class);

    private static final String STREAM_LOOKUP_URL = "https://api.twitch.tv/helix/streams";
    private static final String USER_LOOKUP_URL = "https://api.twitch.tv/helix/users";

    private class StreamAnnouncement {
        private long serverId;
        private long messageId;
        private long liveUser;

        private StreamAnnouncement(long serverId, long messageId, long liveUser){
            this.serverId = serverId;
            this.messageId = messageId;
            this.liveUser = liveUser;
        }
    }

    private class LiveUser {
        private long userId;
        private Set<Long> serversToNotify;

        private LiveUser(long userId, Set<Long> serversToNotify){
            this.userId = userId;
            this.serversToNotify = serversToNotify;
        }
    }

    private List<StreamAnnouncement> notifiedStreams = new CopyOnWriteArrayList<>();

    @Override
    public void startService() throws Exception {
        Luma.schedulerService.scheduleWithFixedDelay(this::checkStreams, 30, 10, TimeUnit.SECONDS);
    }

    private void checkStreams(){
        try {
            Map<String, List<Long>> trackedCommunityStreams = Luma.database.getCommunityStreams();
            Set<LiveUser> liveCommunityStreams = fetchLiveCommunityUsers(trackedCommunityStreams);

            Set<LiveUser> currentlyLiveStreams = new HashSet<>(liveCommunityStreams);
            //TODO: Implement other types

            for(StreamAnnouncement stream : notifiedStreams){
                if(currentlyLiveStreams.stream().anyMatch(u -> u.userId == stream.liveUser && u.serversToNotify.contains(stream.serverId))){
                    //User is still live, do nothing.
                } else {
                    //User is no longer live
                    //TODO: Implement user going offline
                    notifiedStreams.remove(stream);
                }
            }

            ArrayList<LiveUser> usersToBeAnnounced = new ArrayList<>();
            for(LiveUser liveStream : currentlyLiveStreams){
                if(notifiedStreams.stream().anyMatch(ns -> ns.liveUser == liveStream.userId) &&
                        liveStream.serversToNotify.stream()
                                .allMatch(stream ->
                                        notifiedStreams.stream().anyMatch(notif ->
                                                notif.liveUser == liveStream.userId && stream == notif.serverId))){
                    //A live stream is already notified everywhere, do nothing.
                } else {
                    usersToBeAnnounced.add(liveStream);
                    //A live stream is not notified everywhere, notify away!
                }
            }

            ArrayList<JsonObject> userData = fetchUserData(usersToBeAnnounced);

            for(int i = 0; i < usersToBeAnnounced.size(); i++){
                JsonObject announceData = userData.get(i);
                LiveUser announceUser = usersToBeAnnounced.get(i);
                for(long serverId : announceUser.serversToNotify){
                    if(notifiedStreams.stream().noneMatch(ann -> ann.liveUser == announceUser.userId && ann.serverId == serverId)){
                        String name = announceData.getString("display_name", announceData.getString("login", "<information query failed>"));
                        Luma.database.getServerStreamsChannel(serverId).ifPresent(channelId ->
                                Luma.bot.api.getTextChannelById(channelId).ifPresent(textChannel -> {
                                    long messageId = new MessageBuilder()
                                            .setEmbed(new EmbedBuilder()
                                                    .addField("Live", name))
                                            .send(textChannel).join().getId();
                                    notifiedStreams.add(new StreamAnnouncement(serverId, messageId, announceUser.userId));
                                }));
                    }
                }
            }

        } catch (SQLException | IOException | URISyntaxException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<JsonObject> fetchUserData(ArrayList<LiveUser> userList) throws URISyntaxException, IOException, InterruptedException {
        ArrayList<JsonObject> returnData = new ArrayList<>();
        int i = 0;
        do {
            HashMap<String, List<String>> queryParams = new HashMap<>();
            for(int j = i; j < (userList.size() < (i + 100) ? userList.size() : (i + 100)); j++){
                queryParams.computeIfAbsent("id", str -> new ArrayList<>()).add(String.valueOf(userList.get(j).userId));
            }
            URI uri = createURI(USER_LOOKUP_URL, queryParams);
            logger.debug("Sending lookup request to: " + uri.toString());
            //HttpRequest request = HttpRequest.newBuilder().uri(uri)
                    //.header("Accept", "application/vnd.twitchtv.v5+json")
                    //.header("Client-ID", KeyReference.twitchId).build();
            JsonObject responseObject = null;//Json.parse(Luma.httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()).asObject();
            logger.debug("Got response: " + responseObject.toString());
            for(JsonValue val : responseObject.get("data").asArray().values()){
                returnData.add(val.asObject());
            }
            i += 100;
        } while(i < userList.size());
        return returnData;
    }

    private Set<LiveUser> fetchLiveCommunityUsers(Map<String, List<Long>> tracked) throws IOException, URISyntaxException, InterruptedException {
        HashMap<Long, Set<Long>> liveUsers = new HashMap<>();
        List<String> trackedCommunities = new ArrayList<>(tracked.keySet());
        int i = 0;
        do {
            HashMap<String, List<String>> queryParams = new HashMap<>();
            queryParams.computeIfAbsent("first", key -> new ArrayList<>()).add("100");
            for(int j = i; j < (trackedCommunities.size() < (i + 100) ? trackedCommunities.size() : (i + 100)); j++){
                queryParams.computeIfAbsent("community_id", key -> new ArrayList<>()).add(trackedCommunities.get(j));
            }
            JsonArray dataArray = null;
            do {/*
                URI uri = createURI(STREAM_LOOKUP_URL, queryParams);
                logger.debug(queryParams.toString());
                logger.debug("Sending lookup request to: " + uri.toString());
                HttpRequest request = HttpRequest.newBuilder().uri(uri)
                        .header("Accept", "application/vnd.twitchtv.v5+json")
                        .header("Client-ID", KeyReference.twitchId).build();
                JsonObject responseJson = Json.parse(Luma.httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()).asObject();
                logger.debug("Got response: " + responseJson.toString());
                dataArray = responseJson.get("data").asArray();
                for(JsonValue live : dataArray){
                    JsonObject liveObject = live.asObject();
                    long userId = Long.valueOf(liveObject.get("user_id").asString());
                    liveUsers.computeIfAbsent(userId, key -> new HashSet<>());
                    for(JsonValue communityId : liveObject.get("community_ids").asArray()){
                        if(tracked.containsKey(communityId.asString())) {
                            liveUsers.get(userId).addAll(tracked.get(communityId.asString()));
                        }
                    }
                }
                if(responseJson.contains("pagination") && responseJson.get("pagination").asObject().contains("cursor")){
                    ArrayList<String> afterParam = new ArrayList<>();
                    afterParam.add(responseJson.get("pagination").asObject().get("cursor").asString());
                    queryParams.put("after", afterParam);
                }*/
            } while (dataArray.size() == 100);
            i += 100;
        } while(i < trackedCommunities.size());
        Set<LiveUser> retSet = new HashSet<>();
        liveUsers.forEach((userId, servers) -> retSet.add(new LiveUser(userId, servers)));
        return retSet;
    }

    private String createQuery(String param, List<?> values){
        return createQuery(param, values, 0, values.size());
    }

    private String createQuery(String param, List<?> values, int startIndex, int length){
        StringBuilder sb = new StringBuilder("?").append(param).append("=").append(values.get(startIndex).toString());
        for(int i = startIndex + 1; i < length + startIndex; i++){
            sb.append("&").append(param).append("=").append(values.get(i).toString());
        }
        return sb.toString();
    }

    private URI createURI(String baseUrl, Map<String, List<String>> queryParameters) throws URISyntaxException {
        StringBuilder sb = new StringBuilder(baseUrl);
        if(queryParameters.size() > 0){
            sb.append("?");
            List<String> keySet = new ArrayList<>(queryParameters.keySet());
            for(int i = 0; i < keySet.size(); i++){
                for(int j = 0; j < queryParameters.get(keySet.get(i)).size(); j++){
                    sb.append(keySet.get(i)).append("=").append(queryParameters.get(keySet.get(i)).get(j));
                    if(j + 1 < queryParameters.get(keySet.get(i)).size()){
                        sb.append("&");
                    }
                }
                if(i + 1 < keySet.size()){
                    sb.append("&");
                }
            }
        }
        return new URI(sb.toString());
    }
}
