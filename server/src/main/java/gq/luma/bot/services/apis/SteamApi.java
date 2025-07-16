package gq.luma.bot.services.apis;

import com.lukaspradel.steamapi.webapi.client.SteamWebApiClient;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.services.Service;

public class SteamApi implements Service {

    public SteamWebApiClient steamWebApiClient;

    @Override
    public void startService() {
        steamWebApiClient = new SteamWebApiClient.SteamWebApiClientBuilder(KeyReference.steamKey).build();
    }
}
