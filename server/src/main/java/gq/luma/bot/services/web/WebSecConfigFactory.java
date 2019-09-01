package gq.luma.bot.services.web;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.AccessTokenType;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import gq.luma.bot.Luma;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.services.Bot;
import net.minidev.json.JSONObject;
import org.javacord.api.Javacord;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.redirect.RedirectAction;
import org.pac4j.core.redirect.RedirectActionBuilder;
import org.pac4j.oauth.client.OAuth20Client;
import org.pac4j.oauth.config.OAuth20Configuration;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.credentials.OidcCredentials;
import org.pac4j.oidc.credentials.authenticator.OidcAuthenticator;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class WebSecConfigFactory implements ConfigFactory {
    @Override
    public Config build(Object... parameters) {
        final OAuth20Configuration oauthConfig = new OAuth20Configuration();
        oauthConfig.setApi(new DiscordAuthApi());
        oauthConfig.setProfileDefinition(new DiscordProfileDefinition());
        oauthConfig.setWithState(false);
        oauthConfig.setScope("identify connections");
        oauthConfig.setKey(KeyReference.discordClientId);
        oauthConfig.setSecret(KeyReference.discordClientSecret);
        oauthConfig.setTokenAsHeader(true);

        final OAuth20Client<DiscordProfile> discordClient = new OAuth20Client<>();
        discordClient.setConfiguration(oauthConfig);
        discordClient.setName("discord");
        discordClient.setAuthorizationGenerator(((context, profile) -> {
            profile.addRole("ROLE_DISCORD_USER");
            Bot.api.getServerById(146404426746167296L).ifPresent(server -> Bot.api.getUserById(profile.getId()).thenAccept(user -> {
                if(server.getRoles(user).stream().anyMatch(role -> role.getId() == 147134984484945921L)) {
                    profile.addRole("ROLE_DISCORD_ADMIN");
                }
            }));
            return profile;
        }));

        final SteamAuthClient steamAuthClient = new SteamAuthClient();
        steamAuthClient.setCallbackUrl("https://verify.luma.gq/callback");
        steamAuthClient.setName("steam");

        OidcConfiguration twitchConfig = new OidcConfiguration();
        twitchConfig.setClientId(KeyReference.twitchClientId);
        twitchConfig.setDiscoveryURI("https://id.twitch.tv/oauth2/.well-known/openid-configuration");
        twitchConfig.setSecret(KeyReference.twitchClientSecret);
        twitchConfig.setScope("openid");
        final OidcClient twitchClient = new OidcClient(twitchConfig);
        //twitchClient.setRedirectActionBuilder(new TwitchRedirectActionBuilder(twitchConfig, twitchClient));
        twitchClient.setAuthenticator(new OidcAuthenticator(twitchConfig, twitchClient) {
            @Override
            public void validate(final OidcCredentials credentials, final WebContext context) {
                final AuthorizationCode code = credentials.getCode();
                // if we have a code
                if (code != null) {
                    try {
                        final String computedCallbackUrl = client.computeFinalCallbackUrl(context);
                        // Token request
                        final TokenRequest request = new TokenRequest(configuration.findProviderMetadata().getTokenEndpointURI(),
                                this.getClientAuthentication(), new AuthorizationCodeGrant(code, new URI(computedCallbackUrl)));
                        HTTPRequest tokenHttpRequest = request.toHTTPRequest();
                        tokenHttpRequest.setConnectTimeout(configuration.getConnectTimeout());
                        tokenHttpRequest.setReadTimeout(configuration.getReadTimeout());

                        final HTTPResponse httpResponse = tokenHttpRequest.send();

                        final TokenResponse response;
                        /* Here's the hard part. */

                        if (httpResponse.getStatusCode() == HTTPResponse.SC_OK) {
                            httpResponse.ensureStatusCode(HTTPResponse.SC_OK);
                            JSONObject jsonObject = httpResponse.getContentAsJSONObject();

                            OIDCTokens tokens;

                            JWT idToken;

                            try {
                                idToken = JWTParser.parse(JSONObjectUtils.getString(jsonObject, "id_token"));

                            } catch (java.text.ParseException e) {

                                throw new ParseException("Couldn't parse ID token: " + e.getMessage(), e);
                            }

                            // Parse and verify type
                            AccessTokenType tokenType = new AccessTokenType(JSONObjectUtils.getString(jsonObject, "token_type"));

                            if (! tokenType.equals(AccessTokenType.BEARER))
                                throw new ParseException("Token type must be \"Bearer\"");


                            // Parse value
                            String accessTokenValue = JSONObjectUtils.getString(jsonObject, "access_token");


                            // Parse lifetime
                            long lifetime = 0;

                            if (jsonObject.containsKey("expires_in")) {

                                // Lifetime can be a JSON number or string

                                if (jsonObject.get("expires_in") instanceof Number) {

                                    lifetime = JSONObjectUtils.getLong(jsonObject, "expires_in");
                                }
                                else {
                                    String lifetimeStr = JSONObjectUtils.getString(jsonObject, "expires_in");

                                    try {
                                        lifetime = Long.valueOf(lifetimeStr);

                                    } catch (NumberFormatException e) {

                                        throw new ParseException("Invalid \"expires_in\" parameter, must be integer");
                                    }
                                }
                            }


                            // Parse scope
                            Scope scope = Scope.parse("");

                            //if (jsonObject.containsKey("scope"))
                            //    scope = Scope.parse(JSONObjectUtils.getString(jsonObject, "scope"));

                            tokens = new OIDCTokens(idToken, new BearerAccessToken(accessTokenValue, lifetime, scope), RefreshToken.parse(jsonObject));

                            // Parse the custom parameters
                            Map<String,Object> customParams = new HashMap<>();
                            customParams.putAll(jsonObject);
                            for (String tokenParam: tokens.getParameterNames()) {
                                customParams.remove(tokenParam);
                            }

                            if (customParams.isEmpty()) {
                                response = new OIDCTokenResponse(tokens);
                            } else {
                                response = new OIDCTokenResponse(tokens, customParams);
                            }

                        } else {
                            response = TokenErrorResponse.parse(httpResponse);
                        }

                        /* The end of the hard part */
                        if (response instanceof TokenErrorResponse) {
                            throw new TechnicalException("Bad token response, error=" + ((TokenErrorResponse) response).getErrorObject());
                        }
                        final OIDCTokenResponse tokenSuccessResponse = (OIDCTokenResponse) response;

                        // save tokens in credentials
                        final OIDCTokens oidcTokens = tokenSuccessResponse.getOIDCTokens();
                        credentials.setAccessToken(oidcTokens.getAccessToken());
                        credentials.setRefreshToken(oidcTokens.getRefreshToken());
                        credentials.setIdToken(oidcTokens.getIDToken());

                    } catch (final URISyntaxException | IOException | ParseException e) {
                        throw new TechnicalException(e);
                    }
                }
            }
        });
        twitchClient.setName("twitch");

        final Clients clients = new Clients("https://verify.luma.gq/callback", discordClient, steamAuthClient, twitchClient);
        final Config config = new Config(clients);

        config.addAuthorizer("discord", new RequireAnyRoleAuthorizer("ROLE_DISCORD_USER"));
        config.addAuthorizer("discord_admin", new RequireAnyRoleAuthorizer("ROLE_DISCORD_ADMIN"));

        return config;
    }
}
