package gq.luma.bot.services.web;

import org.openid4java.consumer.ConsumerManager;
import org.pac4j.core.client.IndirectClient;

public class SteamAuthClient extends IndirectClient {

    private ConsumerManager consumerManager;

    public String getDiscoveryInformationSessionAttributeName() {
        return getName() + "#discoveryInformation";
    }

    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }

    @Override
    protected void internalInit(boolean forceReinit) {
        this.consumerManager = new ConsumerManager();
        setRedirectionActionBuilder(new SteamRedirectionActionBuilder(this));
        defaultCredentialsExtractor(new SteamCredentialsExtractor(this));
        defaultAuthenticator(new SteamAuthenticator(this));
    }
}
