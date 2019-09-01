package gq.luma.bot.services.web;

import org.openid4java.consumer.ConsumerManager;
import org.pac4j.core.client.IndirectClient;
import org.pac4j.openid.credentials.OpenIdCredentials;
import org.pac4j.openid.profile.OpenIdProfile;

public class SteamAuthClient extends IndirectClient<OpenIdCredentials, OpenIdProfile> {

    private ConsumerManager consumerManager;

    @Override
    protected void clientInit() {
        this.consumerManager = new ConsumerManager();
        defaultRedirectActionBuilder(new SteamRedirectionActionBuilder(this));
        defaultCredentialsExtractor(new SteamCredentialsExtractor(this));
        defaultAuthenticator(new SteamAuthenticator(this));
    }

    public String getDiscoveryInformationSessionAttributeName() {
        return getName() + "#discoveryInformation";
    }

    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }
}
