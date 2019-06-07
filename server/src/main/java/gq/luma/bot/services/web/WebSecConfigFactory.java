package gq.luma.bot.services.web;

import gq.luma.bot.Luma;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.services.Bot;
import org.javacord.api.Javacord;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.oauth.client.OAuth20Client;
import org.pac4j.oauth.config.OAuth20Configuration;

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

        final Clients clients = new Clients("https://verify.luma.gq/callback", discordClient, steamAuthClient);
        final Config config = new Config(clients);

        config.addAuthorizer("discord", new RequireAnyRoleAuthorizer("ROLE_DISCORD_USER"));
        config.addAuthorizer("discord_admin", new RequireAnyRoleAuthorizer("ROLE_DISCORD_ADMIN"));

        return config;
    }
}
