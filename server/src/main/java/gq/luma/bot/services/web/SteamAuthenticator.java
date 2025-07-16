package gq.luma.bot.services.web;

import org.openid4java.OpenIDException;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.ParameterList;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.openid.credentials.OpenIdCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SteamAuthenticator implements Authenticator {
    private static final Logger logger = LoggerFactory.getLogger(SteamAuthenticator.class);

    private final Pattern STEAM_REGEX = Pattern.compile("(\\d+)");

    private final SteamAuthClient client;

    public SteamAuthenticator(final SteamAuthClient client) {
        this.client = client;
    }

    @Override
    public void validate(Credentials credentials, WebContext context, SessionStore sessionStore) {
        OpenIdCredentials oidCredentials = (OpenIdCredentials) credentials;

        final ParameterList parameterList = oidCredentials.getParameterList();
        final DiscoveryInformation discoveryInformation = oidCredentials.getDiscoveryInformation();
        logger.debug("parameterList: {}", parameterList);
        logger.debug("discoveryInformation: {}", discoveryInformation);

        try {
            final VerificationResult verification = this.client.getConsumerManager().verify(this.client.computeFinalCallbackUrl(context),
                    parameterList, discoveryInformation);

            final Identifier verified = verification.getVerifiedId();
            if(verified != null) {
                final AuthSuccess authSuccess = (AuthSuccess) verification.getAuthResponse();
                logger.debug("authSuccess: {}", authSuccess);

                final SteamOpenIdProfile profile = new SteamOpenIdProfile();

                Matcher matcher = STEAM_REGEX.matcher(verified.getIdentifier());
                if (matcher.find()) {
                    profile.setId(matcher.group(1));
                }
                logger.debug("profile: {}", profile);
                credentials.setUserProfile(profile);
                return;
            }
        } catch (OpenIDException e) {
            throw new TechnicalException("OpenID exception", e);
        }

        final String message = "No verifiedId found";
        throw new TechnicalException(message);
    }
}
