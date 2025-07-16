package gq.luma.bot.services.web;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.user.User;
import org.pac4j.oauth.profile.OAuth20Profile;

import java.time.Instant;
import java.util.Optional;

public class DiscordProfile extends OAuth20Profile {

    /**
     * @return the user's 4-digit discord-tag.
     */
    public String getDiscriminator() {
        return getAttribute(DiscordProfileDefinition.DISCRIMINATOR, String.class);
    }

    /**
     * @return the user's avatar hash.
     */
    public String getAvatar() {
        return getAttribute(DiscordProfileDefinition.AVATAR, String.class);
    }

    /**
     * @return whether the user belongs to an OAuth2 application.
     */
    public Boolean isBot() {
        return getAttribute(DiscordProfileDefinition.BOT, Boolean.class);
    }

    /**
     * @return whether the user has two factor enabled on their account.
     */
    public Boolean isMFAEnabled() {
        return getAttribute(DiscordProfileDefinition.MFA_ENABLED, Boolean.class);
    }

    /**
     * @return whether the email on this account has been verified.
     */
    public Boolean isVerified() {
        return getAttribute(DiscordProfileDefinition.VERIFIED, Boolean.class);
    }

    /**
     * @return the creation time of this account.
     */
    public Instant getCreationTime() {
        return getAttribute(DiscordProfileDefinition.CREATION_TIME, Instant.class);
    }

    public Optional<User> getUser(DiscordApi api) {
        return api.getCachedUserById(getId());
    }
}
