package gq.luma.bot.services.web;

import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.ParameterList;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.extractor.CredentialsExtractor;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.openid.credentials.OpenIdCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SteamCredentialsExtractor implements CredentialsExtractor<OpenIdCredentials> {

    private static final Logger logger = LoggerFactory.getLogger(SteamCredentialsExtractor.class);

    private static final String OPENID_MODE = "openid.mode";

    private static final String CANCEL_MODE = "cancel";

    private SteamAuthClient client;

    public SteamCredentialsExtractor(final SteamAuthClient client) {
        this.client = client;
    }

    @Override
    public OpenIdCredentials extract(WebContext context) {
        final String mode = context.getRequestParameter(OPENID_MODE);
        // cancelled authentication
        if (CommonHelper.areEquals(mode, CANCEL_MODE)) {
            logger.debug("authentication cancelled");
            return null;
        }

        final ParameterList parameterList = new ParameterList(context.getRequestParameters());

        final DiscoveryInformation discoveryInformation = (DiscoveryInformation) context.getSessionStore().get(context, this.client.getDiscoveryInformationSessionAttributeName());

        final OpenIdCredentials credentials = new OpenIdCredentials(discoveryInformation, parameterList);
        logger.debug("credentials: {}", credentials);
        return credentials;

    }
}
