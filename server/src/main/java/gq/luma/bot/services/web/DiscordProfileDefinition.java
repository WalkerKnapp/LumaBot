package gq.luma.bot.services.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.scribejava.core.model.Token;
import org.pac4j.core.profile.converter.Converters;
import org.pac4j.core.profile.definition.CommonProfileDefinition;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.oauth.config.OAuthConfiguration;
import org.pac4j.oauth.profile.JsonHelper;
import org.pac4j.oauth.profile.generic.GenericOAuth20ProfileDefinition;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * @see <a href="https://discord.com/developers/docs/resources/user#user-object">Discord user object</a>
 */
@SuppressWarnings("WeakerAccess")
public class DiscordProfileDefinition extends GenericOAuth20ProfileDefinition {

    public static final String DISCRIMINATOR = "discriminator";
    public static final String AVATAR = "avatar";
    public static final String BOT = "bot";
    public static final String MFA_ENABLED = "mfa_enabled";
    public static final String VERIFIED = "verified";
    public static final String CREATION_TIME = "creation_time";

    protected static final int DISCORD_TIMESTAMP_OFFSET_BITS = 22;
    protected static final long DISCORD_EPOCH_MILLIS = 1420070400000L;

    public DiscordProfileDefinition() {
        super();
        setProfileFactory(x -> new DiscordProfile());
        Arrays.asList(new String[] {
            Pac4jConstants.USERNAME, DISCRIMINATOR, AVATAR
        }).forEach(a -> primary(a, Converters.STRING));
        Arrays.asList(new String[] {
            BOT, MFA_ENABLED, VERIFIED
        }).forEach(a -> primary(a, Converters.BOOLEAN));
    }

    @Override
    public String getProfileUrl(final Token accessToken, final OAuthConfiguration configuration) {
        return "https://discord.com/api/users/@me";
    }

    @Override
    public DiscordProfile extractUserProfile(String body) {
        final DiscordProfile profile = (DiscordProfile) newProfile();
        final JsonNode json = JsonHelper.getFirstNode(body);
        if (json != null) {
            String id = (String) JsonHelper.getElement(json, "id");
            profile.setId(id);

            // Extract the account creation time from its ID.
            long creationTimeMillis = (Long.parseLong(id) >>> DISCORD_TIMESTAMP_OFFSET_BITS) + DISCORD_EPOCH_MILLIS;
            profile.addAttribute(CREATION_TIME, Instant.ofEpochMilli(creationTimeMillis));

            System.out.println(json);

            convertAndAdd(profile, Map.of(
                    Pac4jConstants.USERNAME, JsonHelper.getElement(json, Pac4jConstants.USERNAME),
                    DISCRIMINATOR, JsonHelper.getElement(json, DISCRIMINATOR),
                    AVATAR, JsonHelper.getElement(json, AVATAR) == null ? "" : JsonHelper.getElement(json, AVATAR),
                    CommonProfileDefinition.LOCALE, JsonHelper.getElement(json, CommonProfileDefinition.LOCALE),
                    MFA_ENABLED, JsonHelper.getElement(json, MFA_ENABLED)
            ), null);

            // Get a picture URL image or a fallback image.
            // See <https://discord.com/developers/docs/reference#image-formatting-cdn-endpoints>.
            String avatar;
            String discriminator;
            String pictureURL = null;
            if ((avatar = profile.getAttribute(AVATAR, String.class)) != null) {
                pictureURL = String.format("https://cdn.discordapp.com/avatars/%s/%s.png", id, avatar);
            } else if ((discriminator = profile.getAttribute(DISCRIMINATOR, String.class)) != null) {
                int index = Integer.parseInt(discriminator) % 5;
                pictureURL = String.format("https://cdn.discordapp.com/embed/avatars/%d.png", index);
            }
            if (pictureURL != null) {
                try {
                    profile.addAttribute(PICTURE_URL, new URI(pictureURL));
                } catch (URISyntaxException e) {
                    logger.warn("Invalid picture URL!", e);
                }
            }
        }
        return profile;
    }
}
