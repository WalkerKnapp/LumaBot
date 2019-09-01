package gq.luma.bot.services;

import com.lukaspradel.steamapi.webapi.client.SteamWebApiClient;
import gq.luma.bot.reference.KeyReference;

public class SteamApi implements Service {

    public SteamWebApiClient steamWebApiClient;

    @Override
    public void startService() throws Exception {
        steamWebApiClient = new SteamWebApiClient.SteamWebApiClientBuilder(KeyReference.steamKey).build();
    }
}
