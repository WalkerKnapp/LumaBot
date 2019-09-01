package gq.luma.bot.services.web;

import org.openid4java.consumer.ConsumerException;
import org.openid4java.discovery.DiscoveryException;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.MessageException;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.redirect.RedirectAction;
import org.pac4j.core.redirect.RedirectActionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SteamRedirectionActionBuilder implements RedirectActionBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SteamRedirectionActionBuilder.class);

    private static final String STEAM_USER_IDENT = "http://steamcommunity.com/openid";

    private final SteamAuthClient steamClient;

    public SteamRedirectionActionBuilder(SteamAuthClient steamAuthApi) {
        this.steamClient = steamAuthApi;
    }

    @Override
    public RedirectAction redirect(WebContext context) {
        try {
            final List discoveries = this.steamClient.getConsumerManager().discover(STEAM_USER_IDENT);

            final DiscoveryInformation discoveryInformation = this.steamClient.getConsumerManager().associate(discoveries);

            context.getSessionStore().set(context, this.steamClient.getDiscoveryInformationSessionAttributeName(), discoveryInformation);

            final AuthRequest authRequest = this.steamClient.getConsumerManager().authenticate(discoveryInformation,
                    this.steamClient.computeFinalCallbackUrl(context));

            final String redirectionUrl = authRequest.getDestinationUrl(true);
            logger.debug("redirectionUrl: {}", redirectionUrl);
            return RedirectAction.redirect(redirectionUrl);
        } catch (DiscoveryException | ConsumerException | MessageException e) {
            throw new TechnicalException("OpenID exception", e);
        }
    }
}
