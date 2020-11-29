package gq.luma.bot.services.web;

import com.github.scribejava.core.builder.api.DefaultApi20;

public class DiscordAuthApi extends DefaultApi20 {

    protected DiscordAuthApi() {
    }

    private static class InstanceHolder {
        private static final DiscordAuthApi INSTANCE = new DiscordAuthApi();
    }

    public static DiscordAuthApi instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return "https://discord.com/api/oauth2/authorize";
    }

    @Override
    public String getAccessTokenEndpoint() {
        return "https://discord.com/api/oauth2/token";
    }
}
