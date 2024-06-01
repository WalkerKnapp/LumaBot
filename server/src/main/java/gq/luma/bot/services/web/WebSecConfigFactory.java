package gq.luma.bot.services.web;

import com.github.scribejava.apis.DiscordApi;
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
import org.pac4j.oauth.client.OAuth20Client;
import org.pac4j.oauth.config.OAuth20Configuration;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.oidc.credentials.OidcCredentials;
import org.pac4j.oidc.credentials.authenticator.OidcAuthenticator;
import org.pac4j.undertow.context.UndertowSessionStore;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class WebSecConfigFactory implements ConfigFactory {
    @Override
    public Config build(Object... parameters) {
        final OAuth20Configuration oauthConfig = new OAuth20Configuration();
        oauthConfig.setApi(DiscordApi.instance());
        oauthConfig.setProfileDefinition(new DiscordProfileDefinition());
        oauthConfig.setWithState(false);
        oauthConfig.setScope("identify connections");
        oauthConfig.setKey(KeyReference.discordClientId);
        oauthConfig.setSecret(KeyReference.discordClientSecret);
        oauthConfig.setTokenAsHeader(true);

        final OAuth20Client discordClient = new OAuth20Client();
        discordClient.setMultiProfile(true);
        discordClient.setConfiguration(oauthConfig);
        discordClient.setName("discord");
        discordClient.setAuthorizationGenerator((((context, sessionStore, profile) -> {
            profile.addRole("ROLE_DISCORD_USER");

            Bot.api.getServerById(146404426746167296L).ifPresent(server -> Bot.api.getUserById(profile.getId()).thenAccept(user -> {
                if (server.getRoles(user).stream().anyMatch(role -> role.getId() == 147134984484945921L)) {
                    profile.addRole("ROLE_DISCORD_ADMIN");
                }
            }));

            return Optional.of(profile);
        })));

        final SteamAuthClient steamAuthClient = new SteamAuthClient();
        steamAuthClient.setMultiProfile(true);
        steamAuthClient.setCallbackUrl("https://luma.portal2.sr/callback");
        steamAuthClient.setName("steam");

        System.out.println("STEAM MULTI PROFILE: " + steamAuthClient.isMultiProfile(null, null));

        OidcConfiguration twitchConfig = new OidcConfiguration();
        twitchConfig.setClientId(KeyReference.twitchClientId);
        twitchConfig.setDiscoveryURI("https://id.twitch.tv/oauth2/.well-known/openid-configuration");
        twitchConfig.setSecret(KeyReference.twitchClientSecret);
        twitchConfig.setScope("openid");
        final OidcClient twitchClient = new OidcClient(twitchConfig);
        twitchClient.setMultiProfile(true);
        //twitchClient.setRedirectActionBuilder(new TwitchRedirectActionBuilder(twitchConfig, twitchClient));
        twitchClient.setAuthenticator(new OidcAuthenticator(twitchConfig, twitchClient));
        twitchClient.setName("twitch");

        final Clients clients = new Clients("https://luma.portal2.sr/callback", discordClient, steamAuthClient, twitchClient);
        final Config config = new Config(clients);

        config.addAuthorizer("discord", new RequireAnyRoleAuthorizer("ROLE_DISCORD_USER"));
        config.addAuthorizer("discord_admin", new RequireAnyRoleAuthorizer("ROLE_DISCORD_ADMIN"));

        return config;
    }
}
