package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.reference.BotReference;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.util.DiscordRegexPattern;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;

public class DunceCommand {

    private static final long MOD_NOTIFICATIONS_CHANNEL_ID = 432229671711670272L;

    private static final long DUNCE_ROLE_ID = 312324674275115008L;

    public static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    @Command(aliases = {"warn"}, description = "warn_description", usage = "", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public EmbedBuilder onWarn(CommandEvent event) {
        User targetUser = null;

        if (event.getCommandArgs().length >= 1) {

            // Interpret user reference
            String userReference = event.getCommandArgs()[0];

            Matcher mentionMatcher = DiscordRegexPattern.USER_MENTION.matcher(userReference);
            String[] referenceSplitByHash = userReference.split("#");

            if (mentionMatcher.matches()) {
                // Reference is a mention, pull out the user id and get the user
                String userId = mentionMatcher.group("id");
                targetUser = event.getApi().getUserById(userId).exceptionally(t -> null).join();
            } else if (isLong(userReference)) {
                // Reference is a user id
                targetUser = event.getApi().getUserById(userReference).exceptionally(t -> null).join();
            } else if (referenceSplitByHash.length > 1) {
                // Reference could be a nick
                targetUser = event.getServer().orElseThrow(AssertionError::new)
                        .getMemberByDiscriminatedNameIgnoreCase(userReference)
                        .orElse(null);
            }
        }

        if (targetUser == null) {
            return new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Invalid Syntax")
                    .setDescription("Syntax: ?L warn (user) [reason]");
        }

        String reason;
        if (event.getCommandRemainder().length() > event.getCommandArgs()[0].length() + 1) {
            reason = event.getCommandRemainder().substring(event.getCommandArgs()[0].length() + 1);
        } else {
            reason = "";
        }

        // Add warning to the database
        Luma.database.warnUser(targetUser.getId(), event.getMessage().getCreationTimestamp(), event.getMessage().getLink().toString(), reason);
        int warnings = Luma.database.countUserWarnings(targetUser.getId());

        // Notify mod-actions
        TextChannel modActions = event.getServer().orElseThrow(AssertionError::new)
                .getTextChannelById(MOD_NOTIFICATIONS_CHANNEL_ID).orElseThrow(AssertionError::new);
        modActions.sendMessage(new EmbedBuilder()
                .setAuthor(event.getAuthor())
                .setDescription("Warned " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ").")
                .addField("Reason", reason.isEmpty() ? "*No reason given*" : reason)
                .setFooter("This is their " + warnings + (warnings == 1 ? "st" : warnings == 2 ? "nd" : warnings == 3 ? "rd" : "th") + " warning."));

        // DM User (if able)
        targetUser.sendMessage(new EmbedBuilder()
                .setColor(BotReference.LUMA_COLOR)
                .setTitle("You have been warned by a moderator")
                .addField("Reason", reason.isEmpty() ? "*No reason given*" : reason)
                .setFooter("Portal 2 Speedrun Server")
                .setTimestampToNow());

        // If necessary, dunce this user
        if (warnings >= 2) {
            String dunceUntilRaw = "24";
            String dunceUntilRawUnit = "hours";
            Instant dunceUntil = Instant.now().plus(24L, ChronoUnit.HOURS);

            // Notify mod-actions
            modActions.sendMessage(new EmbedBuilder()
                    .setAuthor(event.getAuthor())
                    .setTitle("Autodunced " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ").")
                    .setDescription("For " + dunceUntilRaw + " " + dunceUntilRawUnit + " (until " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.US)
                            .withZone(ZoneId.systemDefault())
                            .format(dunceUntil) + " EST)."));

            // Add this dunce's existing roles to the database
            Luma.database.addDunceStoredRoles(targetUser, event.getServer().orElseThrow(AssertionError::new));

            // Insert a new instant in the database
            Luma.database.insertUndunceInstant(targetUser.getId(), dunceUntil);

            // Remove user's existing roles
            event.getServer().orElseThrow(AssertionError::new)
                    .getRoles(targetUser)
                    .stream().filter(r -> r.getId() != DUNCE_ROLE_ID)
                    .forEach(targetUser::removeRole);

            // Add dunce role
            targetUser.addRole(event.getServer().orElseThrow(AssertionError::new)
                    .getRoleById(DUNCE_ROLE_ID).orElseThrow(AssertionError::new)).join();
        }

        // Send response
        return new EmbedBuilder()
                .setColor(BotReference.LUMA_COLOR)
                .setDescription("Warned " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ").")
                .addField("Reason", reason.isEmpty() ? "*No reason given*" : reason);
    }

    @Command(aliases = {"warnings"}, description = "warnings_description", usage = "", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public EmbedBuilder onWarnings(CommandEvent event) {
        User targetUser = null;

        if (event.getCommandArgs().length >= 1) {

            // Interpret user reference
            String userReference = event.getCommandArgs()[0];

            Matcher mentionMatcher = DiscordRegexPattern.USER_MENTION.matcher(userReference);
            String[] referenceSplitByHash = userReference.split("#");

            if (mentionMatcher.matches()) {
                // Reference is a mention, pull out the user id and get the user
                String userId = mentionMatcher.group("id");
                targetUser = event.getApi().getUserById(userId).exceptionally(t -> null).join();
            } else if (isLong(userReference)) {
                // Reference is a user id
                targetUser = event.getApi().getUserById(userReference).exceptionally(t -> null).join();
            } else if (referenceSplitByHash.length > 1) {
                // Reference could be a nick
                targetUser = event.getServer().orElseThrow(AssertionError::new)
                        .getMemberByDiscriminatedNameIgnoreCase(userReference)
                        .orElse(null);
            }
        }

        if (targetUser == null) {
            return new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Invalid Syntax")
                    .setDescription("Syntax: ?L warnings (user)");
        }

        EmbedBuilder response = new EmbedBuilder()
                .setColor(BotReference.LUMA_COLOR)
                .setDescription(targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ")'s previous warnings:");

        Luma.database.enumerateWarnings(targetUser.getId(), response);

        return response;
    }
}
