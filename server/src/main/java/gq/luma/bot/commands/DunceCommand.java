package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.services.Database;
import org.apache.commons.validator.GenericValidator;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.util.DiscordRegexPattern;

import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Locale;
import java.util.regex.Matcher;

public class DunceCommand {

    private static final long MOD_NOTIFICATIONS_CHANNEL_ID = 432229671711670272L;

    private static final long DUNCE_ROLE_ID = 312324674275115008L;

    @Command(aliases = {"dunce"}, description = "dunce_description", usage = "", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public EmbedBuilder onDunce(CommandEvent event) {
        User targetUser = null;
        Instant dunceUntil = null;

        long dunceUntilRaw = -1;
        String dunceUntilRawUnit = null;

        if (event.getCommandArgs().length >= 1) {

            // Interpret user reference
            String userReference = event.getCommandArgs()[0];

            Matcher mentionMatcher = DiscordRegexPattern.USER_MENTION.matcher(userReference);
            String[] referenceSplitByHash = userReference.split("#");

            if (mentionMatcher.matches()) {
                // Reference is a mention, pull out the user id and get the user
                String userId = mentionMatcher.group("id");
                targetUser = event.getApi().getUserById(userId).exceptionally(t -> null).join();
            } else if (GenericValidator.isLong(userReference)) {
                // Reference is a user id
                targetUser = event.getApi().getUserById(userReference).exceptionally(t -> null).join();
            } else if (referenceSplitByHash.length > 1) {
                // Reference could be a nick
                targetUser = event.getServer().orElseThrow(AssertionError::new)
                        .getMemberByDiscriminatedNameIgnoreCase(userReference)
                        .orElse(null);
            }

            // Interpret time
            if (event.getCommandArgs().length >= 2) {
                // Timeout with explicit time
                //String qualifier = event.getCommandArgs()[1];

                //if (qualifier.equalsIgnoreCase("for")) {
                    String period = event.getCommandArgs()[1];

                    // TODO: Maybe upgrade this to recognize more than single character times?

                    String amountString = period.substring(0, period.length() - 1);

                    if (GenericValidator.isLong(amountString)) {
                        long amount = Long.parseLong(amountString);
                        dunceUntilRaw = amount;
                        char unit = period.toCharArray()[period.length() - 1];

                        switch (unit) {
                            case 'h':
                                dunceUntil = Instant.now().plus(amount, ChronoUnit.HOURS);
                                dunceUntilRawUnit = amount == 1 ? "hour" : "hours";
                                break;
                            case 'd':
                                dunceUntil = Instant.now().plus(amount, ChronoUnit.DAYS);
                                dunceUntilRawUnit = amount == 1 ? "day" : "days";
                                break;
                            case 'w':
                                dunceUntil = Instant.now().plus(amount, ChronoUnit.WEEKS);
                                dunceUntilRawUnit = amount == 1 ? "week" : "weeks";
                                break;
                            case 'm':
                                dunceUntil = Instant.now().plus(amount, ChronoUnit.MONTHS);
                                dunceUntilRawUnit = amount == 1 ? "month" : "months";
                                break;
                            case 'y':
                                dunceUntil = Instant.now().plus(amount, ChronoUnit.YEARS);
                                dunceUntilRawUnit = amount == 1 ? "year" : "years";
                                break;
                        }
                    }

                //} else if (qualifier.equalsIgnoreCase("until")) {
                //    String date = event.getCommandArgs()[2];
//
               //     LocalDateTime.parse()
                //}

            } else if (event.getCommandArgs().length == 1) {
                // Timeout without explicit time
                dunceUntil = Instant.now().plus(24L, ChronoUnit.HOURS);
                dunceUntilRawUnit = "hours";
                dunceUntilRaw = 24;
            }
        }

        if (targetUser == null || dunceUntil == null) {
            return new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Invalid Syntax")
                    .setDescription("Syntax: ?L dunce (user) [for/until] [period/date]\nExamples:")
                    .addField("?L dunce SovietPropaganda#3655", "Dunces SovietPropaganda for 24 hours.")
                    .addField("?L dunce 152559951930327040 for 5d", "Dunces SovietPropaganda for 5 days.")
                    .addField("?L dunce <@152559951930327040> for 1y", "Dunces SovietPropaganda for 1 year.");
        }

        if (Luma.database.isDunced(targetUser.getId())) {
            // Notify mod-actions
            TextChannel modActions = event.getServer().orElseThrow(AssertionError::new)
                    .getTextChannelById(MOD_NOTIFICATIONS_CHANNEL_ID).orElseThrow(AssertionError::new);
            modActions.sendMessage(new EmbedBuilder()
                    .setAuthor(event.getAuthor())
                    .setTitle("Updating dunce time for " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ").")
                    .setDescription("For " + dunceUntilRaw + " " + dunceUntilRawUnit + " (until " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.US)
                            .withZone(ZoneId.systemDefault())
                            .format(dunceUntil) + " EST)."));

            // Set new instant in the database
            Luma.database.updateUndunceInstant(targetUser.getId(), dunceUntil);

            // Send response
            return new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle("Updated " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ") dunce time.")
                    .setDescription("For " + dunceUntilRaw + " " + dunceUntilRawUnit + " (until " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.US)
                            .withZone(ZoneId.systemDefault())
                            .format(dunceUntil) + " EST).");
        } else {
            // Notify mod-actions
            TextChannel modActions = event.getServer().orElseThrow(AssertionError::new)
                    .getTextChannelById(MOD_NOTIFICATIONS_CHANNEL_ID).orElseThrow(AssertionError::new);
            modActions.sendMessage(new EmbedBuilder()
                    .setAuthor(event.getAuthor())
                    .setTitle("Dunced " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ").")
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

            // Send response
            return new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle("Dunced " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ").")
                    .setDescription("For " + dunceUntilRaw + " " + dunceUntilRawUnit + " (until " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale.US)
                            .withZone(ZoneId.systemDefault())
                            .format(dunceUntil) + " EST).");
        }
    }

    @Command(aliases = {"undunce"}, description = "undunce_description", usage = "", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public EmbedBuilder onUndunce(CommandEvent event) {
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
            } else if (GenericValidator.isLong(userReference)) {
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
                    .setDescription("Syntax: ?L undunce (user)");
        }

        if (Luma.database.isDunced(targetUser.getId())) {
            // Notify mod-actions
            TextChannel modActions = event.getServer().orElseThrow(AssertionError::new)
                    .getTextChannelById(MOD_NOTIFICATIONS_CHANNEL_ID).orElseThrow(AssertionError::new);
            modActions.sendMessage(new EmbedBuilder()
                    .setAuthor(event.getAuthor())
                    .setTitle("Undunced " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ")."));

            // Remove instant in database
            Luma.database.removeUndunceInstant(targetUser.getId());

            // Give back previous roles
            Luma.database.popDunceStoredRoles(targetUser, event.getServer().orElseThrow(AssertionError::new));

            // Remove dunce role
            event.getServer().orElseThrow(AssertionError::new)
                    .getRoleById(DUNCE_ROLE_ID).orElseThrow(AssertionError::new)
                    .removeUser(targetUser);

            // Send Response
            return new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .setTitle("Undunced " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ").");
        } else {
            return new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("User " + targetUser.getMentionTag() + " (" + targetUser.getDiscriminatedName() + ") is not dunced.");
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
            } else if (GenericValidator.isLong(userReference)) {
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
            } else if (GenericValidator.isLong(userReference)) {
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
