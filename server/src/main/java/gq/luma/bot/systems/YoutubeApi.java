package gq.luma.bot.systems;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeRequestInitializer;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.Video;
import gq.luma.bot.services.Service;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.utils.LumaException;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class YoutubeApi implements Service {
    private static YouTube youTube;

    private static final String APPLICATION_NAME = "MelissaBot";

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static HttpTransport HTTP_TRANSPORT;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void startService() {
        youTube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, request -> { })
                .setYouTubeRequestInitializer(new YouTubeRequestInitializer(KeyReference.youtubeKey))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static Video getVideo(String id) throws IOException, LumaException {
        List<Video> videos = youTube.videos().list("snippet,contentDetails,statistics").setId(id).execute().getItems();

        if(videos.isEmpty() || videos.size() > 1){
            throw new LumaException("Invalid video ID");
        }

        return videos.get(0);
    }

    public static String getIDFromUrl(URL url){
        return url.toString().split("v=")[1].split("&")[0];
    }

    public static Channel getChannel(String id) throws IOException, LumaException {
        List<Channel> channels = youTube.channels().list("snippet").setId(id).execute().getItems();

        if(channels.isEmpty() || channels.size() > 1){
            throw new LumaException("Invalid channel ID");
        }

        return channels.get(0);
    }
}
