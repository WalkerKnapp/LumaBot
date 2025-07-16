package gq.luma.bot.services.web;

import org.openid4java.consumer.ConsumerException;
import org.openid4java.discovery.DiscoveryException;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.MessageException;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.exception.http.FoundAction;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.redirect.RedirectionActionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class SteamRedirectionActionBuilder implements RedirectionActionBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SteamRedirectionActionBuilder.class);

    private static final String STEAM_USER_IDENT = "http://steamcommunity.com/openid";

    private final SteamAuthClient steamClient;

    public SteamRedirectionActionBuilder(SteamAuthClient steamAuthApi) {
        this.steamClient = steamAuthApi;
    }

    @Override
    public Optional<RedirectionAction> getRedirectionAction(WebContext context, SessionStore sessionStore) {
        try {
            final List<?> discoveries = this.steamClient.getConsumerManager().discover(STEAM_USER_IDENT);

            final DiscoveryInformation discoveryInformation = this.steamClient.getConsumerManager().associate(discoveries);

            sessionStore.set(context, this.steamClient.getDiscoveryInformationSessionAttributeName(), discoveryInformation);

            final AuthRequest authRequest = this.steamClient.getConsumerManager().authenticate(discoveryInformation,
                    this.steamClient.computeFinalCallbackUrl(context));

            final String redirectionUrl = authRequest.getDestinationUrl(true);
            logger.debug("redirectionUrl: {}", redirectionUrl);
            return Optional.of(new FoundAction(redirectionUrl));
        } catch (DiscoveryException | ConsumerException | MessageException e) {
            throw new TechnicalException("OpenID exception", e);
        }
    }
}
