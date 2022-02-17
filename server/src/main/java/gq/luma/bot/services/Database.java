package gq.luma.bot.services;

import com.fasterxml.jackson.core.JsonGenerator;
import com.github.twitch4j.TwitchClient;
import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.permissions.PermissionSet;
import gq.luma.bot.reference.DefaultReference;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.services.apis.IVerbApi;
import gq.luma.bot.services.apis.SRcomApi;
import gq.luma.bot.systems.filtering.filters.ImageFilter;
import gq.luma.bot.systems.filtering.filters.LinkFilter;
import gq.luma.bot.systems.filtering.filters.SimpleFilter;
import gq.luma.bot.systems.filtering.filters.VirusFilter;
import gq.luma.bot.systems.filtering.filters.types.Filter;
import org.apache.commons.csv.CSVPrinter;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.emoji.CustomEmoji;
import org.javacord.api.entity.emoji.Emoji;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class Database implements Service {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private Connection conn;

    private PreparedStatement getChannel;
    private PreparedStatement updateChannelPrefix;
    private PreparedStatement updateChannelLocale;
    private PreparedStatement updateChannelNotify;
    private PreparedStatement insertChannel;

    private PreparedStatement getServer;
    private PreparedStatement getServerPinsEmojiUnicode;
    private PreparedStatement getServerPinsEmojiCustom;
    private PreparedStatement getServerPinsChannel;
    private PreparedStatement getServerPinsThreshold;
    private PreparedStatement updateServerPrefix;
    private PreparedStatement updateServerLocale;
    private PreparedStatement updateServerNotify;
    private PreparedStatement updateClarifaiMonthlyCap;
    private PreparedStatement updateClarifaiCount;
    private PreparedStatement updateClarifaiResetDate;
    private PreparedStatement updateServerPinsEmojiUnicode;
    private PreparedStatement updateServerPinsEmojiCustom;
    private PreparedStatement updateServerPinsChannel;
    private PreparedStatement updateServerPinsThreshold;
    private PreparedStatement getPinEditableByServer;
    private PreparedStatement updateServerPinsEditable;
    private PreparedStatement insertServer;

    private PreparedStatement getPinNotificationByPinnedMessage;
    private PreparedStatement insertPinNotification;

    private PreparedStatement getPinBlacklistByServer;
    private PreparedStatement getPinBlacklistByServerAndChannel;
    private PreparedStatement insertPinBlacklist;
    private PreparedStatement removePinBlacklist;

    private PreparedStatement getUser;
    private PreparedStatement updateUserNotify;
    private PreparedStatement insertUser;

    private PreparedStatement getResult;
    private PreparedStatement insertResult;

    private PreparedStatement getRoleByName;
    private PreparedStatement getAllRoles;

    private PreparedStatement getNodeByToken;
    private PreparedStatement getNodeBySession;
    private PreparedStatement getAllNodes;
    private PreparedStatement updateNodeSession;
    private PreparedStatement updateNodeTask;
    private PreparedStatement updateNode;

    private PreparedStatement getEnabledPermission;
    private PreparedStatement getEnabledPermissionByTargetId;

    private PreparedStatement getAllFilters;

    private PreparedStatement getCommunityTrackedStreams;
    private PreparedStatement getUserTrackedStreams;

    private PreparedStatement getUserRecordById;
    private PreparedStatement insertUserRecord;
    private PreparedStatement updateUserRecordVerified;

    private PreparedStatement getUserConnectionAttemptsByID;
    private PreparedStatement getUserConnectionAttemptsByIP;
    private PreparedStatement insertUserConnectionAttempt;

    private PreparedStatement getVerifiedConnectionsByUser;
    private PreparedStatement getVerifiedConnectionsByUserAndType;
    private PreparedStatement getVerifiedConnectionsByUserAndTypeWithoutRemoved;
    private PreparedStatement getVerifiedConnectionsByType;
    private PreparedStatement getVerifiedConnectionsByTypeAndId;
    private PreparedStatement updateVerifiedConnectionRemovedByUserServerAndId;
    private PreparedStatement updateVerifiedConnectionNotifyByUserServerAndId;
    private PreparedStatement insertVerifiedConnection;

    private PreparedStatement countVotingKeysByServerId;
    private PreparedStatement getVotingKeysByServerId;
    private PreparedStatement insertVotingKey;
    private PreparedStatement removeVotingKey;
    private PreparedStatement clearVotingKeys;

    private PreparedStatement getManualRoleAssignmentsByUser;
    private PreparedStatement insertManualRoleAssignment;
    private PreparedStatement deleteManualRoleAssignment;

    private PreparedStatement getPins;
    private PreparedStatement insertPins;
    private PreparedStatement deletePin;

    private PreparedStatement getPingLeaderboard;
    private PreparedStatement getPingLeaderboardByUser;
    private PreparedStatement updatePingLeaderboardByUser;
    private PreparedStatement incrementPingCountByUser;
    private PreparedStatement getPingCountByUser;
    private PreparedStatement getPingCountsDesc;

    private PreparedStatement getUndunceInstants;
    private PreparedStatement getUndunceInstantByUser;
    private PreparedStatement insertUndunceInstant;
    private PreparedStatement updateUndunceInstantByUser;
    private PreparedStatement removeUndunceInstantByUser;
    private PreparedStatement getDunceStoredRoleByUser;
    private PreparedStatement insertDunceStoredRole;
    private PreparedStatement removeDunceStoredRole;

    private PreparedStatement getWarningsByUser;
    private PreparedStatement getWarningsCountByUser;
    private PreparedStatement insertWarning;

    @Override
    public void startService() throws SQLException, ClassNotFoundException {
        open();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    private void open() throws SQLException, ClassNotFoundException {

        //Class.forName("com.mysql.jdbc.Driver");

        conn = DriverManager.getConnection("jdbc:mysql://" + FileReference.mySQLLocation + "/Luma?user=" + KeyReference.sqlUser + "&password=" + KeyReference.sqlPass + "&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=EST&autoReconnect=true");

        getChannel = conn.prepareStatement("SELECT * FROM channels WHERE id = ?");
        updateChannelPrefix = conn.prepareStatement("UPDATE channels SET prefix = ? WHERE id = ?");
        updateChannelLocale = conn.prepareStatement("UPDATE channels SET locale = ? WHERE id = ?");
        updateChannelNotify = conn.prepareStatement("UPDATE channels SET notify = ? WHERE id = ?");
        insertChannel = conn.prepareStatement("INSERT INTO channels (prefix, locale, notify, id) VALUES (?, ?, ?, ?)");

        getServer = conn.prepareStatement("SELECT * FROM servers WHERE id = ?");
        getServerPinsEmojiUnicode = conn.prepareStatement("SELECT pin_emoji_unicode FROM servers WHERE id = ?");
        getServerPinsEmojiCustom = conn.prepareStatement("SELECT pin_emoji_custom FROM servers WHERE id = ?");
        getServerPinsChannel = conn.prepareStatement("SELECT pin_channel FROM servers WHERE id = ?");
        getServerPinsThreshold = conn.prepareStatement("SELECT pin_threshold FROM servers WHERE id = ?");
        updateServerPrefix = conn.prepareStatement("UPDATE servers SET prefix = ? WHERE id = ?");
        updateServerLocale = conn.prepareStatement("UPDATE servers SET locale = ? WHERE id = ?");
        updateServerNotify = conn.prepareStatement("UPDATE servers SET notify = ? WHERE id = ?");
        updateClarifaiCount = conn.prepareStatement("UPDATE servers SET clarifai_count = ? WHERE id = ?");
        updateServerPinsEmojiUnicode = conn.prepareStatement("UPDATE servers SET pin_emoji_unicode = ? WHERE id = ?");
        updateServerPinsEmojiCustom = conn.prepareStatement("UPDATE servers SET pin_emoji_custom = ? WHERE id = ?");
        updateServerPinsChannel = conn.prepareStatement("UPDATE servers SET pin_channel = ? WHERE id = ?");
        updateServerPinsThreshold = conn.prepareStatement("UPDATE servers SET pin_threshold = ? WHERE id = ?");
        insertServer = conn.prepareStatement("INSERT INTO servers (prefix, locale, logging_channel, streams_channel, notify, monthly_clarifai_cap, clarifai_count, clarifai_reset_date, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

        getPinNotificationByPinnedMessage = conn.prepareStatement("SELECT * FROM pin_notifications WHERE pinned_message = ?");
        insertPinNotification = conn.prepareStatement("INSERT INTO pin_notifications (pinned_message, pin_notification) VALUES (?, ?)");

        getPinBlacklistByServer = conn.prepareStatement("SELECT * FROM pin_blacklist WHERE server = ?");
        getPinBlacklistByServerAndChannel = conn.prepareStatement("SELECT * FROM pin_blacklist WHERE server = ? AND channel = ?");
        insertPinBlacklist = conn.prepareStatement("INSERT INTO pin_blacklist (server, channel) VALUES (?, ?)");
        removePinBlacklist = conn.prepareStatement("DELETE FROM pin_blacklist WHERE server = ? AND channel = ?");

        getPinEditableByServer = conn.prepareStatement("SELECT pin_editable FROM servers WHERE id = ?");
        updateServerPinsEditable = conn.prepareStatement("UPDATE servers SET pin_editable = ? WHERE id = ?");

        getUser = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
        updateUserNotify = conn.prepareStatement("UPDATE users SET notify = ? WHERE id = ?");
        insertUser = conn.prepareStatement("INSERT INTO users (notify, id) VALUES (?, ?, ?)");

        getResult = conn.prepareStatement("SELECT * FROM render_results WHERE id = ?");
        insertResult = conn.prepareStatement("INSERT INTO render_results (id, name, type, code, thumbnail, requester, width, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

        getRoleByName = conn.prepareStatement("SELECT * FROM donor_roles WHERE name = ? AND server_id = ?");
        getAllRoles = conn.prepareStatement("SELECT * FROM donor_roles WHERE server_id = ?");

        getNodeByToken = conn.prepareStatement("SELECT * FROM nodes WHERE token = ?");
        getNodeBySession = conn.prepareStatement("SELECT * FROM nodes WHERE session = ?");
        getAllNodes = conn.prepareStatement("SELECT * FROM nodes");
        updateNodeSession = conn.prepareStatement("UPDATE nodes SET session = ? WHERE token = ?");
        updateNodeTask = conn.prepareStatement("UPDATE nodes SET task = ? WHERE token = ?");
        updateNode = conn.prepareStatement("UPDATE nodes SET session = ?, last_known_host = ?, last_known_name = ? WHERE token = ?");

        getEnabledPermission = conn.prepareStatement("SELECT * FROM permissions WHERE enabled = 1 AND server = ? AND target = ? AND target_id = ?");
        getEnabledPermissionByTargetId = conn.prepareStatement("SELECT * FROM permissions WHERE enabled = 1 AND target_id = ?");

        getAllFilters = conn.prepareStatement("SELECT * FROM filters");

        getCommunityTrackedStreams = conn.prepareStatement("SELECT * FROM tracked_streams WHERE tracking_type = ?");

        getUserRecordById = conn.prepareStatement("SELECT * FROM user_records WHERE id = ?");
        insertUserRecord = conn.prepareStatement("INSERT INTO user_records (id, server_id, discord_token) VALUES (?,?,?)");
        updateUserRecordVerified = conn.prepareStatement("UPDATE user_records SET verified = ? WHERE id = ? AND server_id = ?");

        getUserConnectionAttemptsByID = conn.prepareStatement("SELECT * FROM user_connection_attempts WHERE user_id = ?");
        getUserConnectionAttemptsByIP = conn.prepareStatement("SELECT * FROM user_connection_attempts WHERE ip = ?");
        insertUserConnectionAttempt = conn.prepareStatement("INSERT INTO user_connection_attempts (user_id, server_id, ip) VALUES (?,?,?)");

        getVerifiedConnectionsByUser = conn.prepareStatement("SELECT * FROM verified_connections WHERE user_id = ? AND server_id = ?");
        getVerifiedConnectionsByUserAndType = conn.prepareStatement("SELECT * FROM verified_connections WHERE user_id = ? AND server_id = ? AND connection_type = ?");
        getVerifiedConnectionsByUserAndTypeWithoutRemoved = conn.prepareStatement("SELECT * FROM verified_connections WHERE user_id = ? AND server_id = ? AND connection_type = ? AND removed = 0");
        getVerifiedConnectionsByType = conn.prepareStatement("SELECT * FROM verified_connections WHERE connection_type = ?");
        getVerifiedConnectionsByTypeAndId = conn.prepareStatement("SELECT * FROM verified_connections WHERE connection_type = ? AND id = ?");
        updateVerifiedConnectionRemovedByUserServerAndId = conn.prepareStatement("UPDATE verified_connections SET removed = ? WHERE user_id = ? AND server_id = ? AND id = ?");
        updateVerifiedConnectionNotifyByUserServerAndId = conn.prepareStatement("UPDATE verified_connections SET notify = ? WHERE user_id = ? AND server_id = ? AND id = ?");
        insertVerifiedConnection = conn.prepareStatement("INSERT INTO verified_connections (user_id, server_id, id, connection_type, connection_name, token) VALUES (?,?,?,?,?,?)");

        countVotingKeysByServerId = conn.prepareStatement("SELECT COUNT(*) FROM vote_keys WHERE `server_id` = ?");
        getVotingKeysByServerId = conn.prepareStatement("SELECT * FROM vote_keys WHERE `server_id` = ?");
        insertVotingKey = conn.prepareStatement("INSERT INTO vote_keys (`key`, server_id) VALUES (?,?)");
        removeVotingKey = conn.prepareStatement("DELETE FROM vote_keys WHERE `key` = ? AND `server_id` = ?");
        clearVotingKeys = conn.prepareStatement("DELETE FROM vote_keys WHERE `server_id` = ?");

        getManualRoleAssignmentsByUser = conn.prepareStatement("SELECT * FROM manual_role_assignments WHERE `user_id` = ? AND `server_id` = ?");
        insertManualRoleAssignment = conn.prepareStatement("INSERT INTO manual_role_assignments (`user_id`, `server_id`, `role_id`) VALUES (?, ?, ?)");
        deleteManualRoleAssignment = conn.prepareStatement("DELETE FROM manual_role_assignments WHERE `user_id` = ? AND `server_id` = ? AND `role_id` = ?");

        getPingLeaderboard = conn.prepareStatement("SELECT * FROM ping_leaderboard ORDER BY `ping`");
        getPingLeaderboardByUser = conn.prepareStatement("SELECT ping FROM ping_leaderboard WHERE `user_id` = ?");
        updatePingLeaderboardByUser = conn.prepareStatement("INSERT INTO ping_leaderboard (`user_id`, `ping`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `ping` = ?");

        incrementPingCountByUser = conn.prepareStatement("INSERT INTO ping_counts (user_id, ping_count) VALUES (?, 1) ON DUPLICATE KEY UPDATE ping_count = ping_count + 1");
        getPingCountByUser = conn.prepareStatement("SELECT ping_count FROM ping_counts WHERE user_id = ?");
        getPingCountsDesc = conn.prepareStatement("SELECT *" +
                "      FROM Luma.ping_counts" +
                "      ORDER BY ping_count DESC");

        getUndunceInstants = conn.prepareStatement("SELECT * FROM dunce_instants");
        getUndunceInstantByUser = conn.prepareStatement("SELECT undunce_instant FROM dunce_instants WHERE user_id = ?");
        insertUndunceInstant = conn.prepareStatement("INSERT INTO dunce_instants (user_id, undunce_instant) VALUES (?, ?)");
        updateUndunceInstantByUser = conn.prepareStatement("UPDATE dunce_instants SET undunce_instant = ? WHERE user_id = ?");
        removeUndunceInstantByUser = conn.prepareStatement("DELETE FROM dunce_instants WHERE user_id = ?");

        getDunceStoredRoleByUser = conn.prepareStatement("SELECT * FROM dunce_stored_roles WHERE user_id = ?");
        insertDunceStoredRole = conn.prepareStatement("INSERT INTO dunce_stored_roles (user_id, role_id) VALUES (?, ?)");
        removeDunceStoredRole = conn.prepareStatement("DELETE FROM dunce_stored_roles WHERE user_id = ?");

        getWarningsByUser = conn.prepareStatement("SELECT * FROM warnings WHERE user_id = ?");
        getWarningsCountByUser = conn.prepareStatement("SELECT COUNT(warning_instant) AS warning_count FROM warnings WHERE user_id = ?");
        insertWarning = conn.prepareStatement("INSERT INTO warnings (user_id, warning_instant, reason, message_link) VALUES (?, ?, ?, ?)");
    }

    //Results

    public synchronized ResultSet getResult(int id) throws SQLException {
        getResult.setInt(1, id);
        return getResult.executeQuery();
    }

    public synchronized void addResult(int id, String name, String type, String code, String thumbnail, long requester, int width, int height) throws SQLException {
        insertResult.setInt(1, id);
        insertResult.setString(2, name);
        insertResult.setString(3, type);
        insertResult.setString(4, code);
        insertResult.setString(5, thumbnail);
        insertResult.setLong(6, requester);
        insertResult.setInt(7, width);
        insertResult.setInt(8, height);
        insertResult.execute();
    }

    public synchronized String getEffectivePrefix(TextChannel channel) throws SQLException {
        String channelPrefix = getChannelPrefix(channel.getId());
        if(channelPrefix != null){
            return channelPrefix;
        }

        Optional<ServerTextChannel> scOptional = channel.asServerTextChannel();
        if(scOptional.isPresent()){
            String serverPrefix = getServerPrefix(scOptional.get().getServer().getId());
            if(serverPrefix != null){
                return serverPrefix;
            }
        }

        return DefaultReference.DEFAULT_PREFIX;
    }

    //Channels

    public synchronized String getChannelPrefix(TextChannel channel) throws SQLException {
        return getChannelPrefix(channel.getId());
    }

    private synchronized String getChannelPrefix(long id) throws SQLException {
        getChannel.setLong(1, id);
        ResultSet rs = getChannel.executeQuery();
        if(rs.next()) {
            return rs.getString("prefix");
        }
        return null;
    }

    //Servers

    public synchronized boolean isServerPresent(Server server) {
        try {
            getServer.setLong(1, server.getId());
            return getServer.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized void addServer(Server server, int clarifaiCap, Instant clarifaiResetDate) throws SQLException {
        insertServer.setString(1, null);
        insertServer.setString(2, null);
        insertServer.setNull(3, Types.BIGINT);
        insertServer.setString(4, null);
        insertServer.setInt(5, 0);
        insertServer.setInt(6, clarifaiCap);
        insertServer.setInt(7, 0);
        insertServer.setTimestamp(8, Timestamp.from(clarifaiResetDate));
        insertServer.setLong(9, server.getId());
        insertServer.execute();
    }

    public synchronized long getServerClarifaiResetDate(Server server) throws SQLException {
        getServer.setLong(1, server.getId());
        ResultSet rs = getServer.executeQuery();
        if(rs.next()){
            return rs.getLong("clarifaiResetDate");
        }
        return 0;
    }

    public synchronized void setServerClarifaiResetDate(Server server, long date) throws SQLException {
        updateClarifaiResetDate.setLong(1, date);
        updateClarifaiResetDate.setLong(2, server.getId());
        updateClarifaiResetDate.execute();
    }

    public synchronized String getServerPrefix(Server server) throws SQLException {
        return getServerPrefix(server.getId());
    }

    private synchronized String getServerPrefix(long id) throws SQLException {
        getServer.setLong(1, id);
        ResultSet rs = getServer.executeQuery();
        if(rs.next()) {
            return rs.getString("prefix");
        }
        return null;
    }

    public synchronized String getEffectiveLocale(TextChannel channel) throws SQLException {
        String channelLocale = getChannelLocale(channel.getId());
        if(channelLocale != null){
            return channelLocale;
        }

        Optional<ServerTextChannel> scOptional = channel.asServerTextChannel();
        if(scOptional.isPresent()){
            String serverPrefix = getServerLocale(scOptional.get().getServer().getId());
            if(serverPrefix != null){
                return serverPrefix;
            }
        }

        return DefaultReference.DEFAULT_LOCALE;
    }

    public synchronized String getChannelLocale(TextChannel channel) throws SQLException {
        return getChannelLocale(channel.getId());
    }

    private synchronized String getChannelLocale(long id) throws SQLException {
        getChannel.setLong(1, id);
        ResultSet rs = getChannel.executeQuery();
        if(rs.next()){
            return rs.getString("locale");
        }
        return null;
    }

    public synchronized String getServerLocale(Server server) throws SQLException {
        return getServerLocale(server.getId());
    }

    private synchronized String getServerLocale(long id) throws SQLException {
        getServer.setLong(1, id);
        ResultSet rs = getServer.executeQuery();
        if(rs.next()){
            return rs.getString("locale");
        }
        return null;
    }

    public synchronized Optional<Long> getServerLog(Server server) {
        try {
            getServer.setLong(1, server.getId());
            ResultSet rs = getServer.executeQuery();
            if (rs.next()) {
                long res = rs.getLong("logging_channel");
                if (res != 0) {
                    return Optional.of(res);
                }
            }
            return Optional.empty();
        } catch (SQLException e){
            logger.error("Encountered error: ", e);
            return Optional.empty();
        }
    }

    public synchronized Optional<Long> getServerStreamsChannel(long serverId){
        try {
            getServer.setLong(1, serverId);
            ResultSet rs = getServer.executeQuery();
            if(rs.next()){
                long streamsChannel = rs.getLong("streams_channel");
                if(!rs.wasNull()){
                    return Optional.of(streamsChannel);
                }
            }
            return Optional.empty();
        } catch (SQLException e){
            logger.error("Encountered error: ", e);
            return Optional.empty();
        }
    }

    public synchronized void setServerPrefix(Server server, String prefix) throws SQLException {
        getServer.setLong(1, server.getId());
        ResultSet rs = getServer.executeQuery();

        if(rs.next()){
            updateServerPrefix.setString(1, prefix);
            updateServerPrefix.setLong(2, server.getId());
            updateServerPrefix.execute();
        } else {
            logger.error("Couldn't find server: {}.", server.toString());
        }
    }

    public synchronized void setChannelPrefix(TextChannel channel, String prefix) throws SQLException {
        getChannel.setLong(1, channel.getId());
        ResultSet rs = getChannel.executeQuery();

        if(rs.next()){
            updateChannelPrefix.setString(1, prefix);
            updateChannelPrefix.setLong(2, channel.getId());
            updateChannelPrefix.execute();
        } else {
            insertChannel.setString(1, prefix);
            insertChannel.setString(2, null);
            insertChannel.setInt(3, 0);
            insertChannel.setLong(4, channel.getId());
            insertChannel.execute();
        }
    }

    public synchronized void setServerLocale(Server server, String locale) throws SQLException {
        getServer.setLong(1, server.getId());
        ResultSet rs = getServer.executeQuery();

        if(rs.next()){
            updateServerLocale.setString(1, locale);
            updateServerLocale.setLong(2, server.getId());
            updateServerLocale.execute();
        } else {
            logger.error("Couldn't find server: {}", server.toString());
        }
    }

    public synchronized void setChannelLocale(TextChannel channel, String locale) throws SQLException {
        getChannel.setLong(1, channel.getId());
        ResultSet rs = getChannel.executeQuery();

        if(rs.next()){
            updateChannelLocale.setString(1, locale);
            updateChannelLocale.setLong(2, channel.getId());
            updateChannelLocale.execute();
        } else {
            insertChannel.setString(1, null);
            insertChannel.setString(2, locale);
            insertChannel.setInt(3, 0);
            insertChannel.setLong(4, channel.getId());
            insertChannel.execute();
        }
    }

    public synchronized Optional<String> getServerPinEmojiMention(Server server) {
        try {
            // Check for unicode emojis

            getServerPinsEmojiUnicode.setLong(1, server.getId());

            ResultSet rs = getServerPinsEmojiUnicode.executeQuery();

            if (rs.next()) {
                String emoji = rs.getString("pin_emoji_unicode");

                if (emoji != null) {
                    return Optional.of(emoji);
                }
            }

            // Check for custom emojis

            getServerPinsEmojiCustom.setLong(1, server.getId());

            rs = getServerPinsEmojiCustom.executeQuery();

            if (rs.next()) {
                long id = rs.getLong("pin_emoji_custom");

                if (id != 0) {
                    return Bot.api.getCustomEmojiById(id).map(CustomEmoji::getMentionTag);
                }
            }

            return Optional.empty();

        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public synchronized boolean ifServerPinEmojiMatches(Server server, Emoji otherEmoji) {
        try {
            if (otherEmoji.isUnicodeEmoji()) {
                // Check for unicode emojis

                getServerPinsEmojiUnicode.setLong(1, server.getId());

                ResultSet rs = getServerPinsEmojiUnicode.executeQuery();

                if (rs.next()) {
                    String emoji = rs.getString("pin_emoji_unicode");

                    if (emoji != null) {
                        return otherEmoji.asUnicodeEmoji().get().equals(emoji);
                    }
                }
            } else if (otherEmoji.isCustomEmoji()) {

                // Check for custom emojis

                getServerPinsEmojiCustom.setLong(1, server.getId());

                ResultSet rs = getServerPinsEmojiCustom.executeQuery();

                if (rs.next()) {
                    long id = rs.getLong("pin_emoji_custom");

                    if (id != 0) {
                        return id == otherEmoji.asCustomEmoji().get().getId();
                    }
                }
            }

            return false;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized void setServerPinEmojiCustom(long serverId, long emojiId) {
        try {
            updateServerPinsEmojiCustom.setLong(1, emojiId);
            updateServerPinsEmojiCustom.setLong(2, serverId);
            updateServerPinsEmojiCustom.execute();

            updateServerPinsEmojiUnicode.setNull(1, Types.VARCHAR);
            updateServerPinsEmojiUnicode.setLong(2, serverId);
            updateServerPinsEmojiUnicode.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void setServerPinEmojiUnicode(long serverId, String emoji) {
        try {
            updateServerPinsEmojiUnicode.setString(1, emoji);
            updateServerPinsEmojiUnicode.setLong(2, serverId);
            updateServerPinsEmojiUnicode.execute();

            updateServerPinsEmojiCustom.setNull(1, Types.BIGINT);
            updateServerPinsEmojiCustom.setLong(2, serverId);
            updateServerPinsEmojiCustom.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized OptionalInt getServerPinThreshold(Server server) {
        try {
            getServerPinsThreshold.setLong(1, server.getId());

            ResultSet rs = getServerPinsThreshold.executeQuery();

            if (rs.next()) {
                int threshold = rs.getInt("pin_threshold");

                if (threshold != 0) {
                    return OptionalInt.of(threshold);
                }
            }

            return OptionalInt.empty();
        } catch (SQLException e) {
            e.printStackTrace();
            return OptionalInt.empty();
        }
    }

    public synchronized void setServerPinThreshold(long serverId, int threshold) {
        try {
            updateServerPinsThreshold.setInt(1, threshold);
            updateServerPinsThreshold.setLong(2, serverId);

            updateServerPinsThreshold.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized Optional<ServerTextChannel> getServerPinChannel(Server server) {
        try {
            getServerPinsChannel.setLong(1, server.getId());

            ResultSet rs = getServerPinsChannel.executeQuery();

            if (rs.next()) {
                long channelId = rs.getLong("pin_channel");

                if (channelId != 0) {
                    return Bot.api.getServerTextChannelById(channelId);
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public synchronized void setServerPinChannel(long serverId, long channelId) {
        try {
            updateServerPinsChannel.setLong(1, channelId);
            updateServerPinsChannel.setLong(2, serverId);

            updateServerPinsChannel.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean isPinnedMessageNotified(long pinnedMessageId) {
        try {
            getPinNotificationByPinnedMessage.setLong(1, pinnedMessageId);

            ResultSet rs = getPinNotificationByPinnedMessage.executeQuery();

            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized Message getPinNotificationByPinnedMessage(long pinnedMessageId, long notificationChannelId) {
        try {
            getPinNotificationByPinnedMessage.setLong(1, pinnedMessageId);

            ResultSet rs = getPinNotificationByPinnedMessage.executeQuery();

            if (rs.next()) {
                long id = rs.getLong("pin_notification");

                return Bot.api.getMessageById(id, Bot.api.getTextChannelById(notificationChannelId)
                        .orElseThrow(AssertionError::new)).join();
            }

            throw new AssertionError("Tried to get pin notification for illegal message.");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AssertionError("Tried to get pin notification and had SQL failure.");
        }
    }

    public synchronized void createPinNotification(long pinnedMessageId, long pinNotificationId) {
        try {
            insertPinNotification.setLong(1, pinnedMessageId);
            insertPinNotification.setLong(2, pinNotificationId);

            insertPinNotification.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean isChannelPinBlacklisted(long serverId, long channelId) {
        try {
            getPinBlacklistByServerAndChannel.setLong(1, serverId);
            getPinBlacklistByServerAndChannel.setLong(2, channelId);

            ResultSet rs = getPinBlacklistByServerAndChannel.executeQuery();

            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized Stream<ServerTextChannel> getPinBlacklist(long serverId) {
        try {
            getPinBlacklistByServer.setLong(1, serverId);

            ResultSet rs = getPinBlacklistByServer.executeQuery();

            ArrayList<ServerTextChannel> channels = new ArrayList<>();

            while (rs.next()) {
                Bot.api.getServerTextChannelById(rs.getLong("channel")).ifPresent(channels::add);
            }

            return channels.stream();
        } catch (SQLException e) {
            e.printStackTrace();
            return Stream.empty();
        }
    }

    public synchronized void addPinBlacklist(long serverId, long channelId) {
        try {
            insertPinBlacklist.setLong(1, serverId);
            insertPinBlacklist.setLong(2, channelId);

            insertPinBlacklist.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void deletePinBlacklist(long serverId, long channelId) {
        try {
            removePinBlacklist.setLong(1, serverId);
            removePinBlacklist.setLong(2, channelId);

            removePinBlacklist.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized Optional<Long> getRoleByName(long serverId, String name) throws SQLException {
        getRoleByName.setString(1, name.substring(0, 1).toUpperCase() + name.substring(1));
        getRoleByName.setLong(2, serverId);
        ResultSet rs = getRoleByName.executeQuery();

        if(rs.next()){
            return Optional.of(rs.getLong("id"));
        } else {
            return Optional.empty();
        }
    }

    public synchronized List<String> getAvailibeRoles(long serverId) throws SQLException {
        getAllRoles.setLong(1, serverId);
        ResultSet rs = getAllRoles.executeQuery();
        List<String> ret = new ArrayList<>();
        while(rs.next()){
            ret.add(rs.getString("name"));
        }
        return ret;
    }

    public synchronized boolean isServerPinEditable(long serverId) throws SQLException {
        getPinEditableByServer.setLong(1, serverId);
        ResultSet rs = getPinEditableByServer.executeQuery();

        if (rs.next()) {
            return rs.getBoolean("pin_editable");
        } else {
            return false;
        }
    }

    public synchronized void setServerPinEditable(long serverId, boolean editable) throws SQLException {
        updateServerPinsEditable.setBoolean(1, editable);
        updateServerPinsEditable.setLong(2, serverId);
        updateServerPinsEditable.execute();
    }

    //Nodes

    public synchronized Optional<ResultSet> getNodeByToken(String token) throws SQLException {
        getNodeByToken.setString(1, token);
        ResultSet rs = getNodeByToken.executeQuery();
        if(rs.next()){
            return Optional.of(rs);
        } else {
            return Optional.empty();
        }
    }

    public synchronized void updateNodeSession(String token, String session) throws SQLException {
        updateNodeSession.setString(1, session);
        updateNodeSession.setString(2, token);
        updateNodeSession.execute();
    }

    public synchronized void updateNode(String token, String session, String lastKnownHost, String lastKnownName) throws SQLException {
        updateNode.setString(1, session);
        updateNode.setString(2, lastKnownHost);
        updateNode.setString(3, lastKnownName);
        updateNode.setString(4, token);
        updateNode.execute();
    }

    public synchronized ArrayList<PermissionSet> getPermission(Server server, PermissionSet.PermissionTarget target, long targetId) throws SQLException {
        getEnabledPermission.setLong(1, server.getId());
        getEnabledPermission.setString(2, target.name());
        getEnabledPermission.setLong(3, targetId);
        ArrayList<PermissionSet> ret = new ArrayList<>();
        ResultSet rs = getEnabledPermission.executeQuery();
        while(rs.next()){
            ret.add(new PermissionSet(rs));
        }
        return ret;
    }

    public synchronized ArrayList<PermissionSet> getPermission(long serverId, PermissionSet.PermissionTarget target, long targetId) throws SQLException {
        getEnabledPermission.setLong(1, serverId);
        getEnabledPermission.setString(2, target.name());
        getEnabledPermission.setLong(3, targetId);
        ArrayList<PermissionSet> ret = new ArrayList<>();
        ResultSet rs = getEnabledPermission.executeQuery();
        while(rs.next()){
            ret.add(new PermissionSet(rs));
        }
        return ret;
    }

    public synchronized ArrayList<PermissionSet> getPermissionByTargetId(long targetId) throws SQLException {
        getEnabledPermissionByTargetId.setLong(1, targetId);
        ArrayList<PermissionSet> ret = new ArrayList<>();
        ResultSet rs = getEnabledPermissionByTargetId.executeQuery();
        while(rs.next()){
            ret.add(new PermissionSet(rs));
        }
        return ret;
    }

    public synchronized Map<Long, Collection<Filter>> getAllFilters() throws SQLException {
        ResultSet rs = getAllFilters.executeQuery();
        Map<Long, Collection<Filter>> ret = new HashMap<>();
        while (rs.next()){
            switch(rs.getInt("type")){
                case 0:
                    ret.computeIfAbsent(rs.getLong("server"), (id) -> new ArrayList<>()).add(new ImageFilter(rs));
                    break;
                case 1:
                    //Video Filter
                    break;
                case 2:
                    ret.computeIfAbsent(rs.getLong("server"), (id) -> new ArrayList<>()).add(new VirusFilter(rs));
                    break;
                case 3:
                    ret.computeIfAbsent(rs.getLong("server"), (id) -> new ArrayList<>()).add(new LinkFilter(rs));
                    break;
                case 4:
                    ret.computeIfAbsent(rs.getLong("server"), (id) -> new ArrayList<>()).add(new SimpleFilter(rs));
                    break;
            }
        }
        return ret;
    }

    public synchronized Map<String, List<Long>> getCommunityStreams() throws SQLException {
        getCommunityTrackedStreams.setString(1, "community");
        ResultSet rs = getCommunityTrackedStreams.executeQuery();
        Map<String, List<Long>> map = new HashMap<>();
        while (rs.next()){
            map.computeIfAbsent(rs.getString("tracked_id"), (s) -> new ArrayList<>()).add(rs.getLong("server_id"));
        }
        return map;
    }

    public synchronized int getUserVerified(long id) {
        try {
            getUserRecordById.setLong(1, id);
            ResultSet rs = getUserRecordById.executeQuery();
            if(rs.next()) {
                return rs.getInt("verified");
            } else {
                return -1;
            }
        } catch (SQLException e){
            e.printStackTrace();
            return -1;
        }
    }

    public synchronized void writeConnectionBoxes(long id, long serverId, StringBuilder sb) {
        try {
            getVerifiedConnectionsByUser.setLong(1, id);
            getVerifiedConnectionsByUser.setLong(2, serverId);
            ResultSet rs = getVerifiedConnectionsByUser.executeQuery();
            while (rs.next()) {
                sb.append("<div class=\"connectionbox\">");
                sb.append("<img class=\"connection\" src=\"https://cdn.luma.gq/").append(rs.getString("connection_type"));
                sb.append(".png\" alt=\"").append(rs.getString("connection_type")).append("\"></img>");
                sb.append(rs.getString("connection_name"));
                sb.append("</div>");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void writeConnectionsJson(long discordId, long serverId, JsonGenerator jsonGenerator) {
        try {
            getVerifiedConnectionsByUserAndTypeWithoutRemoved.setLong(1, discordId);
            getVerifiedConnectionsByUserAndTypeWithoutRemoved.setLong(2, serverId);
            getVerifiedConnectionsByUserAndTypeWithoutRemoved.setString(3, "steam");
            ResultSet rs = getVerifiedConnectionsByUserAndTypeWithoutRemoved.executeQuery();
            jsonGenerator.writeArrayFieldStart("steamAccounts");
            while (rs.next()) {
                if(rs.getInt("removed") == 0) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("name", rs.getString("connection_name"));
                    String steamId = rs.getString("id");
                    jsonGenerator.writeStringField("id", steamId);
                    jsonGenerator.writeStringField("steamLink", "https://steamcommunity.com/profiles/" + steamId);
                    String iverbLink = "https://board.portal2.sr/profile/" + steamId;
                    jsonGenerator.writeStringField("iverbLink", iverbLink);
                    int rank = Luma.skillRoleService.calculateRoundedTotalPoints(Long.parseLong(steamId));
                    if (rank != -1) {
                        jsonGenerator.writeNumberField("iverbRank", rank);
                    }
                    jsonGenerator.writeEndObject();
                }
            }
            jsonGenerator.writeEndArray();
            getVerifiedConnectionsByUserAndTypeWithoutRemoved.setString(3, "srcom");
            rs = getVerifiedConnectionsByUserAndTypeWithoutRemoved.executeQuery();
            jsonGenerator.writeArrayFieldStart("srcomAccounts");
            while (rs.next()) {
                if(rs.getInt("removed") == 0) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("name", rs.getString("connection_name"));
                    String srcomId = rs.getString("id");
                    jsonGenerator.writeStringField("id", srcomId);
                    SRcomApi.writeConnectionFieldsJson(srcomId, jsonGenerator);
                    jsonGenerator.writeEndObject();
                }
            }
            jsonGenerator.writeEndArray();
            getVerifiedConnectionsByUserAndTypeWithoutRemoved.setString(3, "twitch");
            rs = getVerifiedConnectionsByUserAndTypeWithoutRemoved.executeQuery();
            jsonGenerator.writeArrayFieldStart("twitchAccounts");
            while (rs.next()) {
                if(rs.getInt("removed") == 0) {
                    jsonGenerator.writeStartObject();
                    String twitchName = rs.getString("connection_name");
                    jsonGenerator.writeStringField("name", twitchName);
                    String twitchId = rs.getString("id");
                    jsonGenerator.writeStringField("id", twitchId);
                    jsonGenerator.writeNumberField("notify", rs.getInt("notify"));
                    jsonGenerator.writeStringField("link", "https://twitch.tv/" + twitchName);
                    jsonGenerator.writeEndObject();
                }
            }
            jsonGenerator.writeEndArray();
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    public class StreamRead {
        int notify;
        long discordId;
        User user;
        long twitchId;
        com.github.twitch4j.helix.domain.User twitchUser;
        boolean isLive;

        public StreamRead(int notify, long discordId, User user, long twitchId, com.github.twitch4j.helix.domain.User twitchUser, boolean isLive) {
            this.notify = notify;
            this.discordId = discordId;
            this.user = user;
            this.twitchId = twitchId;
            this.twitchUser = twitchUser;
            this.isLive = isLive;
        }
    }

    public synchronized void writeStreamsJson(long serverId, JsonGenerator generator) {
        try {
            getVerifiedConnectionsByType.setString(1, "twitch");
            ResultSet rs = getVerifiedConnectionsByType.executeQuery();
            generator.writeStartArray();
            ArrayList<Future<StreamRead>> futures = new ArrayList<>();
            while (rs.next()) {
                long discordId = rs.getLong("user_id");
                long twitchId = rs.getLong("id");
                int notify = rs.getInt("notify");

                futures.add(Luma.executorService.submit(() -> {
                    try {
                        User user = Bot.api.getUserById(discordId).join();
                        com.github.twitch4j.helix.domain.User twitchUser = Luma.twitchApi.client.getHelix().getUsers(Luma.twitchApi.appAccessToken, List.of(String.valueOf(twitchId)), null).execute().getUsers().get(0);
                        if(twitchUser != null) {
                            //Channel twitchChannel = Luma.twitchApi.client.getHelix()(twitchId);
                            //boolean isLive = Luma.twitchApi.client.getStreamEndpoint().isLive(twitchChannel);
                            boolean isLive = false;
                            return new StreamRead(notify, discordId, user, twitchId, twitchUser, isLive);
                        } else {
                            System.err.println("Twitch user deleted, id=" + twitchId + " for user " + user.getDiscriminatedName());
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    return null;
                }));
            }
            for (Future<StreamRead> future : futures) {
                writeStreamField(generator, future.get());
            }
            generator.writeEndArray();
        } catch (SQLException | IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public synchronized List<String> getVerifiedConnectionsByType(String type) {
        try {
            getVerifiedConnectionsByType.setString(1, type);

            ResultSet rs = getVerifiedConnectionsByType.executeQuery();

            ArrayList<String> ret = new ArrayList<>();
            while(rs.next()) {
                ret.add(rs.getString("id"));
            }

            return ret;

        } catch (SQLException t) {
            t.printStackTrace();
        }

        return List.of();
    }

    public synchronized List<User> getVerifiedConnectionsById(String id, String type) {
        try {
            getVerifiedConnectionsByTypeAndId.setString(1, type);
            getVerifiedConnectionsByTypeAndId.setString(2, id);

            ResultSet rs = getVerifiedConnectionsByTypeAndId.executeQuery();

            ArrayList<User> ret = new ArrayList<>();
            while (rs.next()) {
                ret.add(Bot.api.getUserById(rs.getString("user_id")).join());
            }

            return ret;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    public synchronized Map<String, List<String>> getVerifiedConnectionsByUser(long userId, long serverId) {
        HashMap<String, List<String>> ret = new HashMap<>();

        try {
            getVerifiedConnectionsByUser.setLong(1, userId);
            getVerifiedConnectionsByUser.setLong(2, serverId);
            ResultSet rs = getVerifiedConnectionsByUser.executeQuery();

            while (rs.next()) {
                ret.computeIfAbsent(rs.getString("connection_type"), str -> new ArrayList<>())
                        .add(rs.getString("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return ret;
    }

    private void writeStreamField(JsonGenerator generator, StreamRead read) throws IOException {
        if(read == null) return;
        generator.writeStartObject();
        generator.writeNumberField("notify", read.notify);
        generator.writeNumberField("discordId", read.discordId);
        generator.writeStringField("discordName", read.user.getDiscriminatedName());
        generator.writeStringField("discordAvatarUrl", read.user.getAvatar().getUrl().toString());
        generator.writeNumberField("twitchId", read.twitchId);
        generator.writeStringField("twitchName", read.twitchUser.getDisplayName());
        generator.writeStringField("twitchAvatarUrl", read.twitchUser.getProfileImageUrl());
        generator.writeBooleanField("live", read.isLive);
        generator.writeEndObject();
    }

    public synchronized void setRemovedConnection(long discordId, long serverId, String id, int removed) {
        try {
            updateVerifiedConnectionRemovedByUserServerAndId.setInt(1, removed);
            updateVerifiedConnectionRemovedByUserServerAndId.setLong(2, discordId);
            updateVerifiedConnectionRemovedByUserServerAndId.setLong(3, serverId);
            updateVerifiedConnectionRemovedByUserServerAndId.setString(4, id);
            updateVerifiedConnectionRemovedByUserServerAndId.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void setNotifyConnection(long discordId, long serverId, String id, int notify) {
        try {
            System.out.println("Setting notifyconnection for user " + discordId + " conid" + id + " to " + notify);
            updateVerifiedConnectionNotifyByUserServerAndId.setInt(1, notify);
            updateVerifiedConnectionNotifyByUserServerAndId.setLong(2, discordId);
            updateVerifiedConnectionNotifyByUserServerAndId.setLong(3, serverId);
            updateVerifiedConnectionNotifyByUserServerAndId.setString(4, id);
            System.out.println("Updated " + updateVerifiedConnectionNotifyByUserServerAndId.executeUpdate() + " rows.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void writeIps(long id, StringBuilder sb) {
        try {
            getUserConnectionAttemptsByID.setLong(1, id);
            ResultSet rs = getUserConnectionAttemptsByID.executeQuery();
            while (rs.next()) {
                sb.append(rs.getString("ip"));
                sb.append("</br>");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addUserRecord(long userId, long serverId, String accessToken) {
        try {
            getUserRecordById.setLong(1, userId);
            ResultSet rs = getUserRecordById.executeQuery();
            if(!rs.next()) {
                insertUserRecord.setLong(1, userId);
                insertUserRecord.setLong(2, serverId);
                insertUserRecord.setString(3, accessToken);
                insertUserRecord.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void updateUserRecordVerified(long userId, long serverId, int verified) {
        try {
            updateUserRecordVerified.setInt(1, verified);
            updateUserRecordVerified.setLong(2, userId);
            updateUserRecordVerified.setLong(3, serverId);
            updateUserRecordVerified.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addUserConnectionAttempt(long userId, long serverId, String ip) {
        try {
            getUserConnectionAttemptsByIP.setString(1, ip);
            ResultSet rs = getUserConnectionAttemptsByIP.executeQuery();
            if(!rs.next()) {
                insertUserConnectionAttempt.setLong(1, userId);
                insertUserConnectionAttempt.setLong(2, serverId);
                insertUserConnectionAttempt.setString(3, ip);
                insertUserConnectionAttempt.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized List<Long> getUserConnectionAttemptsByIP(String ip) {
        ArrayList<Long> ret = new ArrayList<>();
        try {
            getUserConnectionAttemptsByIP.setString(1, ip);
            ResultSet rs = getUserConnectionAttemptsByIP.executeQuery();
            while (rs.next()) {
                ret.add(rs.getLong("user_id"));
            }
            return ret;
        } catch (SQLException e) {
            e.printStackTrace();
            return ret;
        }
    }

    public synchronized void addVerifiedConnection(long userId, long serverId, String connectionId, String connectionType, String connectionName, String connectionToken) {
        try {
            getVerifiedConnectionsByUser.setLong(1, userId);
            getVerifiedConnectionsByUser.setLong(2, serverId);
            ResultSet rs = getVerifiedConnectionsByUser.executeQuery();
            while (rs.next()) {
                if(rs.getString("id").equalsIgnoreCase(connectionId) && rs.getString("connection_name").equalsIgnoreCase(connectionName)) {
                    return;
                }
            }
            insertVerifiedConnection.setLong(1, userId);
            insertVerifiedConnection.setLong(2, serverId);
            insertVerifiedConnection.setString(3, connectionId);
            insertVerifiedConnection.setString(4, connectionType);
            insertVerifiedConnection.setString(5, connectionName);
            insertVerifiedConnection.setString(6, connectionToken);
            insertVerifiedConnection.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized int countVotingKeysByServer(long serverId) {
        try {
            countVotingKeysByServerId.setLong(1, serverId);
            ResultSet rs = countVotingKeysByServerId.executeQuery();
            rs.next();
            return rs.getInt("Count(*)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public synchronized List<String> getVotingKeysByServer(long serverId) {
        try {
            getVotingKeysByServerId.setLong(1, serverId);
            ResultSet rs = getVotingKeysByServerId.executeQuery();
            List<String> list = new ArrayList<>();
            while(rs.next()) {
                list.add(rs.getString("key"));
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return List.of();
    }

    public synchronized void insertVotingKey(String key, long serverId) {
        try {
            insertVotingKey.setString(1, key);
            insertVotingKey.setLong(2, serverId);
            insertVotingKey.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void removeVotingKey(String key, long serverId) {
        try {
            removeVotingKey.setString(1, key);
            removeVotingKey.setLong(2, serverId);
            removeVotingKey.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void clearVotingKeys(long serverId) {
        try {
            clearVotingKeys.setLong(1, serverId);
            clearVotingKeys.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void assignRole(long roleId, long serverId, long userId) {
        try {
            getManualRoleAssignmentsByUser.setLong(1, userId);
            getManualRoleAssignmentsByUser.setLong(2, serverId);
            ResultSet rs = getManualRoleAssignmentsByUser.executeQuery();
            while (rs.next()) {
                if (rs.getLong("role_id") == roleId) {
                    return;
                }
            }

            insertManualRoleAssignment.setLong(1, userId);
            insertManualRoleAssignment.setLong(2, serverId);
            insertManualRoleAssignment.setLong(3, roleId);
            insertManualRoleAssignment.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void unassignRole(long roleId, long serverId, long userId) {
        try {
            deleteManualRoleAssignment.setLong(1, userId);
            deleteManualRoleAssignment.setLong(2, serverId);
            deleteManualRoleAssignment.setLong(3, roleId);
            deleteManualRoleAssignment.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized ArrayList<Long> getAssignedRoles(long userId, long serverId) {
        ArrayList<Long> ret = new ArrayList<>();
        try {
            getManualRoleAssignmentsByUser.setLong(1, userId);
            getManualRoleAssignmentsByUser.setLong(2, serverId);
            ResultSet rs = getManualRoleAssignmentsByUser.executeQuery();

            while(rs.next()) {
                ret.add(rs.getLong("role_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ret;
    }

    public synchronized String getPingLeaderboard() {
        try {
            ResultSet rs = getPingLeaderboard.executeQuery();

            StringBuilder sb = new StringBuilder();

            int i = 0;

            int lastPing = Integer.MIN_VALUE;

            do {
                if (rs.next()) {
                    long userId = rs.getLong("user_id");
                    int ping = rs.getInt("ping");

                    if (ping != lastPing) {
                        if (i != 0) {
                            sb.append(" - ").append(lastPing).append(" ms\n");
                        }

                        lastPing = ping;

                        i++;
                        sb.append(i).append(": ");
                    } else {
                        sb.append(" & ");
                    }

                    sb.append("<@").append(userId).append(">");
                } else {
                    break;
                }
            } while (i < 5);

            if (i != 0) {
                sb.append(" - ").append(lastPing).append(" ms");
            }

            return sb.toString();
        } catch (SQLException e) {
            e.printStackTrace();
            return "";
        }
    }

    public synchronized int getFastestPing(long userId) {
        try {
            getPingLeaderboardByUser.setLong(1, userId);
            ResultSet rs = getPingLeaderboardByUser.executeQuery();

            if (rs.next()) {
                return rs.getInt("ping");
            } else {
                return Integer.MAX_VALUE;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Integer.MAX_VALUE;
        }
    }

    public synchronized  void setFastestPing(long userId, int ping) {
        try {
            updatePingLeaderboardByUser.setLong(1, userId);
            updatePingLeaderboardByUser.setInt(2, ping);
            updatePingLeaderboardByUser.setInt(3, ping);
            updatePingLeaderboardByUser.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized int incrementPingCount(long userId) {
        try {
            incrementPingCountByUser.setLong(1, userId);
            incrementPingCountByUser.execute();

            getPingCountByUser.setLong(1, userId);
            ResultSet rs = getPingCountByUser.executeQuery();

            if (rs.next()) {
                return rs.getInt("ping_count");
            } else {
                return 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public synchronized void getCurrentDunces(BiConsumer<Long, Instant> dunceConsumer) {
        try {
            ResultSet rs = getUndunceInstants.executeQuery();

            while (rs.next()) {
                dunceConsumer.accept(rs.getLong("user_id"), rs.getTimestamp("undunce_instant").toInstant());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean isDunced(long userId) {
        try {
            getUndunceInstantByUser.setLong(1, userId);
            ResultSet rs = getUndunceInstantByUser.executeQuery();

            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized void insertUndunceInstant(long userId, Instant undunceInstant) {
        try {
            insertUndunceInstant.setLong(1, userId);
            insertUndunceInstant.setTimestamp(2, Timestamp.from(undunceInstant));
            insertUndunceInstant.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void updateUndunceInstant(long userId, Instant undunceInstant) {
        try {
            updateUndunceInstantByUser.setTimestamp(1, Timestamp.from(undunceInstant));
            updateUndunceInstantByUser.setLong(2, userId);
            updateUndunceInstantByUser.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void removeUndunceInstant(long userId) {
        try {
            removeUndunceInstantByUser.setLong(1, userId);
            removeUndunceInstantByUser.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void writePingCounts(CSVPrinter printer) {
        try {
            ResultSet rs = getPingCountsDesc.executeQuery();

            while (rs.next()) {
                String username = Bot.api.getUserById(rs.getLong("user_id")).thenApply(u -> u.getDiscriminatedName()).exceptionally(t -> {
                    try {
                        return String.valueOf(rs.getLong("user_id"));
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return "";
                    }
                }).join();
                printer.printRecord(username, rs.getInt("ping_count"));
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addDunceStoredRoles(User user, Server server) {
        try {
            for (Role r : server.getRoles(user)) {
                if (r.getId() != 312324674275115008L && !r.isEveryoneRole()) {
                    insertDunceStoredRole.setLong(1, user.getId());
                    insertDunceStoredRole.setLong(2, r.getId());
                    insertDunceStoredRole.execute();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void popDunceStoredRoles(User user, Server server) {
        try {
            getDunceStoredRoleByUser.setLong(1, user.getId());
            ResultSet rs = getDunceStoredRoleByUser.executeQuery();

            while (rs.next()) {
                server.getRoleById(rs.getLong("role_id")).ifPresent(user::addRole);
            }

            removeDunceStoredRole.setLong(1, user.getId());
            removeDunceStoredRole.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void warnUser(long userId, Instant timestamp, String messageLink, String reason) {
        try {
            insertWarning.setLong(1, userId);
            insertWarning.setTimestamp(2, Timestamp.from(timestamp));
            insertWarning.setString(3, reason);
            insertWarning.setString(4, messageLink);

            insertWarning.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized int countUserWarnings(long userId) {
        try {
            getWarningsCountByUser.setLong(1, userId);

            ResultSet rs = getWarningsCountByUser.executeQuery();

            if (rs.next()) {
                return rs.getInt("warning_count");
            } else {
                return 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public synchronized void enumerateWarnings(long userId, EmbedBuilder response) {
        try {
            getWarningsByUser.setLong(1, userId);

            ResultSet rs = getWarningsByUser.executeQuery();

            while (rs.next()) {
                String warningDate = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                        .withLocale(Locale.US)
                        .withZone(ZoneId.systemDefault())
                        .format(rs.getTimestamp("warning_instant").toInstant());
                String messageLink = rs.getString("message_link");
                String reason = rs.getString("reason");
                response.addField(warningDate, "[" + (reason.isEmpty() ? "*No reason given*" : reason) + "](" + messageLink + ")");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void close(){
        if(conn == null) {
            return;
        }
        try{
            if(conn.isClosed())
                return;
            conn.close();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }
}
