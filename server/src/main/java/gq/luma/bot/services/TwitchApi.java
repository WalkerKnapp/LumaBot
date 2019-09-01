package gq.luma.bot.services;

import gq.luma.bot.reference.KeyReference;
import me.philippheuer.twitch4j.TwitchClient;
import me.philippheuer.twitch4j.TwitchClientBuilder;

public class TwitchApi implements Service {

    public TwitchClient client;

    @Override
    public void startService() throws Exception {
        client = TwitchClientBuilder.init()
                .withClientId(KeyReference.twitchClientId)
                .withClientSecret(KeyReference.twitchClientSecret)
                .build();
        //client.connect();
    }
}
