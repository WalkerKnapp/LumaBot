package gq.luma.bot.services.web;

import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.ParameterList;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.extractor.CredentialsExtractor;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.openid.credentials.OpenIdCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class SteamCredentialsExtractor implements CredentialsExtractor {

    private static final Logger logger = LoggerFactory.getLogger(SteamCredentialsExtractor.class);

    private static final String OPENID_MODE = "openid.mode";

    private static final String CANCEL_MODE = "cancel";

    private SteamAuthClient client;

    public SteamCredentialsExtractor(final SteamAuthClient client) {
        this.client = client;
    }

    @Override
    public Optional<Credentials> extract(WebContext context, SessionStore sessionStore) {
        final String mode = context.getRequestParameter(OPENID_MODE).orElseThrow();
        // cancelled authentication
        if (CommonHelper.areEquals(mode, CANCEL_MODE)) {
            logger.debug("authentication cancelled");
            return null;
        }

        final ParameterList parameterList = new ParameterList(context.getRequestParameters());

        final DiscoveryInformation discoveryInformation = (DiscoveryInformation) sessionStore.get(context, this.client.getDiscoveryInformationSessionAttributeName()).orElseThrow();

        final OpenIdCredentials credentials = new OpenIdCredentials(discoveryInformation, parameterList);
        logger.debug("credentials: {}", credentials);
        return Optional.of(credentials);

    }
}
