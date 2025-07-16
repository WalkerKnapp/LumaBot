package gq.luma.bot.services;

import com.fasterxml.jackson.core.JsonGenerator;
import gq.luma.bot.commands.subsystem.permissions.PermissionSet;
import gq.luma.bot.reference.DefaultReference;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.services.apis.SRcomApi;
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
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@SuppressWarnings("SynchronizeOnNonFinalField")
public class Database implements Service {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private Connection conn;

    private PreparedStatement getChannel;

    private PreparedStatement getServer;
    private PreparedStatement getServerPinsEmojiUnicode;
    private PreparedStatement getServerPinsEmojiCustom;
    private PreparedStatement getServerPinsChannel;
    private PreparedStatement getServerPinsThreshold;
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

    private PreparedStatement getEnabledPermission;
    private PreparedStatement getEnabledPermissionByTargetId;

    private PreparedStatement getUserRecordById;
    private PreparedStatement insertUserRecord;
    private PreparedStatement updateUserRecordVerified;

    private PreparedStatement getUserConnectionAttemptsByIP;
    private PreparedStatement insertUserConnectionAttempt;

    private PreparedStatement getVerifiedConnectionsByUser;
    private PreparedStatement getVerifiedConnectionsByUserAndTypeWithoutRemoved;
    private PreparedStatement getVerifiedConnectionsByType;
    private PreparedStatement getVerifiedConnectionsByTypeAndId;
    private PreparedStatement updateVerifiedConnectionRemovedByUserServerAndId;
    private PreparedStatement updateVerifiedConnectionNotifyByUserServerAndId;
    private PreparedStatement insertVerifiedConnection;

    private PreparedStatement getPingLeaderboard;
    private PreparedStatement getPingLeaderboardByUser;
    private PreparedStatement updatePingLeaderboardByUser;
    private PreparedStatement incrementPingCountByUser;
    private PreparedStatement getPingCountByUser;

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
    public void startService() throws SQLException {
        open();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    private void open() throws SQLException {

        conn = DriverManager.getConnection("jdbc:mysql://" + FileReference.mySQLLocation + "/luma?user=" + KeyReference.sqlUser + "&password=" + KeyReference.sqlPass + "&autoReconnect=true");

        getChannel = conn.prepareStatement("SELECT * FROM channels WHERE id = ?");

        getServer = conn.prepareStatement("SELECT * FROM servers WHERE id = ?");
        getServerPinsEmojiUnicode = conn.prepareStatement("SELECT pin_emoji_unicode FROM servers WHERE id = ?");
        getServerPinsEmojiCustom = conn.prepareStatement("SELECT pin_emoji_custom FROM servers WHERE id = ?");
        getServerPinsChannel = conn.prepareStatement("SELECT pin_channel FROM servers WHERE id = ?");
        getServerPinsThreshold = conn.prepareStatement("SELECT pin_threshold FROM servers WHERE id = ?");
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

        getEnabledPermission = conn.prepareStatement("SELECT * FROM permissions WHERE enabled = 1 AND server = ? AND target = ? AND target_id = ?");
        getEnabledPermissionByTargetId = conn.prepareStatement("SELECT * FROM permissions WHERE enabled = 1 AND target_id = ?");

        getUserRecordById = conn.prepareStatement("SELECT * FROM user_records WHERE id = ?");
        insertUserRecord = conn.prepareStatement("INSERT INTO user_records (id, server_id, discord_token) VALUES (?,?,?)");
        updateUserRecordVerified = conn.prepareStatement("UPDATE user_records SET verified = ? WHERE id = ? AND server_id = ?");

        getUserConnectionAttemptsByIP = conn.prepareStatement("SELECT * FROM user_connection_attempts WHERE ip = ?");
        insertUserConnectionAttempt = conn.prepareStatement("INSERT INTO user_connection_attempts (user_id, server_id, ip) VALUES (?,?,?)");

        getVerifiedConnectionsByUser = conn.prepareStatement("SELECT * FROM verified_connections WHERE user_id = ? AND server_id = ?");
        getVerifiedConnectionsByUserAndTypeWithoutRemoved = conn.prepareStatement("SELECT * FROM verified_connections WHERE user_id = ? AND server_id = ? AND connection_type = ? AND removed = 0");
        getVerifiedConnectionsByType = conn.prepareStatement("SELECT * FROM verified_connections WHERE connection_type = ?");
        getVerifiedConnectionsByTypeAndId = conn.prepareStatement("SELECT * FROM verified_connections WHERE connection_type = ? AND id = ?");
        updateVerifiedConnectionRemovedByUserServerAndId = conn.prepareStatement("UPDATE verified_connections SET removed = ? WHERE user_id = ? AND server_id = ? AND id = ?");
        updateVerifiedConnectionNotifyByUserServerAndId = conn.prepareStatement("UPDATE verified_connections SET notify = ? WHERE user_id = ? AND server_id = ? AND id = ?");
        insertVerifiedConnection = conn.prepareStatement("INSERT INTO verified_connections (user_id, server_id, id, connection_type, connection_name, token) VALUES (?,?,?,?,?,?)");

        getPingLeaderboard = conn.prepareStatement("SELECT * FROM ping_leaderboard ORDER BY `ping`");
        getPingLeaderboardByUser = conn.prepareStatement("SELECT ping FROM ping_leaderboard WHERE `user_id` = ?");
        updatePingLeaderboardByUser = conn.prepareStatement("INSERT INTO ping_leaderboard (`user_id`, `ping`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `ping` = ?");

        incrementPingCountByUser = conn.prepareStatement("INSERT INTO ping_counts (user_id, ping_count) VALUES (?, 1) ON DUPLICATE KEY UPDATE ping_count = ping_count + 1");
        getPingCountByUser = conn.prepareStatement("SELECT ping_count FROM ping_counts WHERE user_id = ?");

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

    public String getEffectivePrefix(TextChannel channel) throws SQLException {
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

    private String getChannelPrefix(long id) throws SQLException {
        synchronized (getChannel) {
            getChannel.setLong(1, id);
            ResultSet rs = getChannel.executeQuery();
            if (rs.next()) {
                return rs.getString("prefix");
            }
            return null;
        }
    }

    //Servers

    public boolean isServerPresent(Server server) {
        try {
            synchronized (getServer) {
                getServer.setLong(1, server.getId());
                return getServer.executeQuery().next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void addServer(Server server, int clarifaiCap, Instant clarifaiResetDate) throws SQLException {
        synchronized (insertServer) {
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
    }

    private String getServerPrefix(long id) throws SQLException {
        synchronized (getServer) {
            getServer.setLong(1, id);
            ResultSet rs = getServer.executeQuery();
            if (rs.next()) {
                return rs.getString("prefix");
            }
            return null;
        }
    }

    public String getEffectiveLocale(TextChannel channel) throws SQLException {
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

    private String getChannelLocale(long id) throws SQLException {
        synchronized (getChannel) {
            getChannel.setLong(1, id);
            ResultSet rs = getChannel.executeQuery();
            if (rs.next()) {
                return rs.getString("locale");
            }
            return null;
        }
    }

    private String getServerLocale(long id) throws SQLException {
        synchronized (getServer) {
            getServer.setLong(1, id);
            ResultSet rs = getServer.executeQuery();
            if (rs.next()) {
                return rs.getString("locale");
            }
            return null;
        }
    }

    public Optional<Long> getServerLog(Server server) {
        try {
            synchronized (getServer) {
                getServer.setLong(1, server.getId());
                ResultSet rs = getServer.executeQuery();
                if (rs.next()) {
                    long res = rs.getLong("logging_channel");
                    if (res != 0) {
                        return Optional.of(res);
                    }
                }
                return Optional.empty();
            }
        } catch (SQLException e){
            logger.error("Encountered error: ", e);
            return Optional.empty();
        }
    }


    public Optional<String> getServerPinEmojiMention(Server server) {
        try {
            // Check for unicode emojis

            synchronized (getServerPinsEmojiUnicode) {
                getServerPinsEmojiUnicode.setLong(1, server.getId());

                ResultSet rs = getServerPinsEmojiUnicode.executeQuery();

                if (rs.next()) {
                    String emoji = rs.getString("pin_emoji_unicode");

                    if (emoji != null) {
                        return Optional.of(emoji);
                    }
                }
            }

            // Check for custom emojis

            synchronized (getServerPinsEmojiCustom) {
                getServerPinsEmojiCustom.setLong(1, server.getId());

                ResultSet rs = getServerPinsEmojiCustom.executeQuery();

                if (rs.next()) {
                    long id = rs.getLong("pin_emoji_custom");

                    if (id != 0) {
                        return Bot.api.getCustomEmojiById(id).map(CustomEmoji::getMentionTag);
                    }
                }
            }

            return Optional.empty();

        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public boolean ifServerPinEmojiMatches(Server server, Emoji otherEmoji) {
        try {
            if (otherEmoji.isUnicodeEmoji()) {
                // Check for unicode emojis
                synchronized (getServerPinsEmojiUnicode) {
                    getServerPinsEmojiUnicode.setLong(1, server.getId());

                    ResultSet rs = getServerPinsEmojiUnicode.executeQuery();

                    if (rs.next()) {
                        String emoji = rs.getString("pin_emoji_unicode");

                        if (emoji != null) {
                            return otherEmoji.asUnicodeEmoji().get().equals(emoji);
                        }
                    }
                }
            } else if (otherEmoji.isCustomEmoji()) {
                // Check for custom emojis
                synchronized (getServerPinsEmojiCustom) {
                    getServerPinsEmojiCustom.setLong(1, server.getId());

                    ResultSet rs = getServerPinsEmojiCustom.executeQuery();

                    if (rs.next()) {
                        long id = rs.getLong("pin_emoji_custom");

                        if (id != 0) {
                            return id == otherEmoji.asCustomEmoji().get().getId();
                        }
                    }
                }
            }

            return false;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setServerPinEmojiCustom(long serverId, long emojiId) {
        try {
            synchronized (updateServerPinsEmojiCustom) {
                updateServerPinsEmojiCustom.setLong(1, emojiId);
                updateServerPinsEmojiCustom.setLong(2, serverId);
                updateServerPinsEmojiCustom.execute();
            }

            synchronized (updateServerPinsEmojiUnicode) {
                updateServerPinsEmojiUnicode.setNull(1, Types.VARCHAR);
                updateServerPinsEmojiUnicode.setLong(2, serverId);
                updateServerPinsEmojiUnicode.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setServerPinEmojiUnicode(long serverId, String emoji) {
        try {
            synchronized (updateServerPinsEmojiUnicode) {
                updateServerPinsEmojiUnicode.setString(1, emoji);
                updateServerPinsEmojiUnicode.setLong(2, serverId);
                updateServerPinsEmojiUnicode.execute();
            }

            synchronized (updateServerPinsEmojiCustom) {
                updateServerPinsEmojiCustom.setNull(1, Types.BIGINT);
                updateServerPinsEmojiCustom.setLong(2, serverId);
                updateServerPinsEmojiCustom.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public OptionalInt getServerPinThreshold(Server server) {
        try {
            synchronized (getServerPinsThreshold) {
                getServerPinsThreshold.setLong(1, server.getId());

                ResultSet rs = getServerPinsThreshold.executeQuery();

                if (rs.next()) {
                    int threshold = rs.getInt("pin_threshold");

                    if (threshold != 0) {
                        return OptionalInt.of(threshold);
                    }
                }
            }

            return OptionalInt.empty();
        } catch (SQLException e) {
            e.printStackTrace();
            return OptionalInt.empty();
        }
    }

    public void setServerPinThreshold(long serverId, int threshold) {
        try {
            synchronized (updateServerPinsThreshold) {
                updateServerPinsThreshold.setInt(1, threshold);
                updateServerPinsThreshold.setLong(2, serverId);

                updateServerPinsThreshold.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Optional<ServerTextChannel> getServerPinChannel(Server server) {
        try {
            synchronized (getServerPinsChannel) {
                getServerPinsChannel.setLong(1, server.getId());

                ResultSet rs = getServerPinsChannel.executeQuery();

                if (rs.next()) {
                    long channelId = rs.getLong("pin_channel");

                    if (channelId != 0) {
                        return Bot.api.getServerTextChannelById(channelId);
                    }
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public void setServerPinChannel(long serverId, long channelId) {
        try {
            synchronized (updateServerPinsChannel) {
                updateServerPinsChannel.setLong(1, channelId);
                updateServerPinsChannel.setLong(2, serverId);

                updateServerPinsChannel.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isPinnedMessageNotified(long pinnedMessageId) {
        try {
            synchronized (getPinNotificationByPinnedMessage) {
                getPinNotificationByPinnedMessage.setLong(1, pinnedMessageId);

                ResultSet rs = getPinNotificationByPinnedMessage.executeQuery();

                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Message getPinNotificationByPinnedMessage(long pinnedMessageId, long notificationChannelId) {
        try {
            synchronized (getPinNotificationByPinnedMessage) {
                getPinNotificationByPinnedMessage.setLong(1, pinnedMessageId);

                ResultSet rs = getPinNotificationByPinnedMessage.executeQuery();

                if (rs.next()) {
                    long id = rs.getLong("pin_notification");

                    return Bot.api.getMessageById(id, Bot.api.getTextChannelById(notificationChannelId)
                            .orElseThrow(AssertionError::new)).join();
                }
            }

            throw new AssertionError("Tried to get pin notification for illegal message.");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AssertionError("Tried to get pin notification and had SQL failure.");
        }
    }

    public void createPinNotification(long pinnedMessageId, long pinNotificationId) {
        try {
            synchronized (insertPinNotification) {
                insertPinNotification.setLong(1, pinnedMessageId);
                insertPinNotification.setLong(2, pinNotificationId);

                insertPinNotification.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isChannelPinBlacklisted(long serverId, long channelId) {
        try {
            synchronized (getPinBlacklistByServerAndChannel) {
                getPinBlacklistByServerAndChannel.setLong(1, serverId);
                getPinBlacklistByServerAndChannel.setLong(2, channelId);

                ResultSet rs = getPinBlacklistByServerAndChannel.executeQuery();

                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Stream<ServerTextChannel> getPinBlacklist(long serverId) {
        try {
            ArrayList<ServerTextChannel> channels = new ArrayList<>();

            synchronized (getPinBlacklistByServer) {
                getPinBlacklistByServer.setLong(1, serverId);

                ResultSet rs = getPinBlacklistByServer.executeQuery();

                while (rs.next()) {
                    Bot.api.getServerTextChannelById(rs.getLong("channel")).ifPresent(channels::add);
                }
            }

            return channels.stream();
        } catch (SQLException e) {
            e.printStackTrace();
            return Stream.empty();
        }
    }

    public void addPinBlacklist(long serverId, long channelId) {
        try {
            synchronized (insertPinBlacklist) {
                insertPinBlacklist.setLong(1, serverId);
                insertPinBlacklist.setLong(2, channelId);

                insertPinBlacklist.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deletePinBlacklist(long serverId, long channelId) {
        try {
            synchronized (removePinBlacklist) {
                removePinBlacklist.setLong(1, serverId);
                removePinBlacklist.setLong(2, channelId);

                removePinBlacklist.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isServerPinEditable(long serverId) throws SQLException {
        synchronized (getPinEditableByServer) {
            getPinEditableByServer.setLong(1, serverId);
            ResultSet rs = getPinEditableByServer.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("pin_editable");
            } else {
                return false;
            }
        }
    }

    public void setServerPinEditable(long serverId, boolean editable) throws SQLException {
        synchronized (updateServerPinsEditable) {
            updateServerPinsEditable.setBoolean(1, editable);
            updateServerPinsEditable.setLong(2, serverId);
            updateServerPinsEditable.execute();
        }
    }

    public ArrayList<PermissionSet> getPermission(Server server, PermissionSet.PermissionTarget target, long targetId) throws SQLException {
        synchronized (getEnabledPermission) {
            getEnabledPermission.setLong(1, server.getId());
            getEnabledPermission.setString(2, target.name());
            getEnabledPermission.setLong(3, targetId);
            ArrayList<PermissionSet> ret = new ArrayList<>();
            ResultSet rs = getEnabledPermission.executeQuery();
            while (rs.next()) {
                ret.add(new PermissionSet(rs));
            }
            return ret;
        }
    }


    public ArrayList<PermissionSet> getPermissionByTargetId(long targetId) throws SQLException {
        synchronized (getEnabledPermissionByTargetId) {
            getEnabledPermissionByTargetId.setLong(1, targetId);
            ArrayList<PermissionSet> ret = new ArrayList<>();
            ResultSet rs = getEnabledPermissionByTargetId.executeQuery();
            while (rs.next()) {
                ret.add(new PermissionSet(rs));
            }
            return ret;
        }
    }


    public int getUserVerified(long id) {
        try {
            synchronized (getUserRecordById) {
                getUserRecordById.setLong(1, id);
                ResultSet rs = getUserRecordById.executeQuery();
                if (rs.next()) {
                    return rs.getInt("verified");
                } else {
                    return -1;
                }
            }
        } catch (SQLException e){
            e.printStackTrace();
            return -1;
        }
    }

    public void writeConnectionsJson(long discordId, long serverId, JsonGenerator jsonGenerator) {
        try {
            synchronized (getVerifiedConnectionsByUserAndTypeWithoutRemoved) {
                getVerifiedConnectionsByUserAndTypeWithoutRemoved.setLong(1, discordId);
                getVerifiedConnectionsByUserAndTypeWithoutRemoved.setLong(2, serverId);
                getVerifiedConnectionsByUserAndTypeWithoutRemoved.setString(3, "steam");
                ResultSet rs = getVerifiedConnectionsByUserAndTypeWithoutRemoved.executeQuery();
                jsonGenerator.writeArrayFieldStart("steamAccounts");
                while (rs.next()) {
                    if (rs.getInt("removed") == 0) {
                        jsonGenerator.writeStartObject();
                        jsonGenerator.writeStringField("name", rs.getString("connection_name"));
                        String steamId = rs.getString("id");
                        jsonGenerator.writeStringField("id", steamId);
                        jsonGenerator.writeStringField("steamLink", "https://steamcommunity.com/profiles/" + steamId);
                        String iverbLink = "https://board.portal2.sr/profile/" + steamId;
                        jsonGenerator.writeStringField("iverbLink", iverbLink);
                        //int rank = Luma.skillRoleService.calculateRoundedTotalPoints(Long.parseLong(steamId));
                        //if (rank != -1) {
                        //    jsonGenerator.writeNumberField("iverbRank", rank);
                        //}
                        jsonGenerator.writeEndObject();
                    }
                }
                jsonGenerator.writeEndArray();
                getVerifiedConnectionsByUserAndTypeWithoutRemoved.setString(3, "srcom");
                rs = getVerifiedConnectionsByUserAndTypeWithoutRemoved.executeQuery();
                jsonGenerator.writeArrayFieldStart("srcomAccounts");
                while (rs.next()) {
                    if (rs.getInt("removed") == 0) {
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
                    if (rs.getInt("removed") == 0) {
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
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getVerifiedConnectionsByType(String type) {
        try {
            synchronized (getVerifiedConnectionsByType) {
                getVerifiedConnectionsByType.setString(1, type);

                ResultSet rs = getVerifiedConnectionsByType.executeQuery();

                ArrayList<String> ret = new ArrayList<>();
                while (rs.next()) {
                    ret.add(rs.getString("id"));
                }

                return ret;
            }
        } catch (SQLException t) {
            t.printStackTrace();
        }

        return List.of();
    }

    public List<User> getVerifiedConnectionsById(String id, String type) {
        try {
            synchronized (getVerifiedConnectionsByTypeAndId) {
                getVerifiedConnectionsByTypeAndId.setString(1, type);
                getVerifiedConnectionsByTypeAndId.setString(2, id);

                ResultSet rs = getVerifiedConnectionsByTypeAndId.executeQuery();

                ArrayList<User> ret = new ArrayList<>();
                while (rs.next()) {
                    ret.add(Bot.api.getUserById(rs.getString("user_id")).join());
                }

                return ret;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    public Map<String, List<String>> getVerifiedConnectionsByUser(long userId, long serverId) {
        HashMap<String, List<String>> ret = new HashMap<>();

        try {
            synchronized (getVerifiedConnectionsByUser) {
                getVerifiedConnectionsByUser.setLong(1, userId);
                getVerifiedConnectionsByUser.setLong(2, serverId);
                ResultSet rs = getVerifiedConnectionsByUser.executeQuery();

                while (rs.next()) {
                    ret.computeIfAbsent(rs.getString("connection_type"), str -> new ArrayList<>())
                            .add(rs.getString("id"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return ret;
    }

    public void setRemovedConnection(long discordId, long serverId, String id, int removed) {
        try {
            synchronized (updateVerifiedConnectionRemovedByUserServerAndId) {
                updateVerifiedConnectionRemovedByUserServerAndId.setInt(1, removed);
                updateVerifiedConnectionRemovedByUserServerAndId.setLong(2, discordId);
                updateVerifiedConnectionRemovedByUserServerAndId.setLong(3, serverId);
                updateVerifiedConnectionRemovedByUserServerAndId.setString(4, id);
                updateVerifiedConnectionRemovedByUserServerAndId.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setNotifyConnection(long discordId, long serverId, String id, int notify) {
        try {
            synchronized (updateVerifiedConnectionNotifyByUserServerAndId) {
                System.out.println("Setting notifyconnection for user " + discordId + " conid" + id + " to " + notify);
                updateVerifiedConnectionNotifyByUserServerAndId.setInt(1, notify);
                updateVerifiedConnectionNotifyByUserServerAndId.setLong(2, discordId);
                updateVerifiedConnectionNotifyByUserServerAndId.setLong(3, serverId);
                updateVerifiedConnectionNotifyByUserServerAndId.setString(4, id);
                System.out.println("Updated " + updateVerifiedConnectionNotifyByUserServerAndId.executeUpdate() + " rows.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void addUserRecord(long userId, long serverId, String accessToken) {
        try {
            synchronized (getUserRecordById) {
                getUserRecordById.setLong(1, userId);
                ResultSet rs = getUserRecordById.executeQuery();
                if (!rs.next()) {
                    insertUserRecord.setLong(1, userId);
                    insertUserRecord.setLong(2, serverId);
                    insertUserRecord.setString(3, accessToken);
                    insertUserRecord.execute();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateUserRecordVerified(long userId, long serverId, int verified) {
        try {
            synchronized (updateUserRecordVerified) {
                updateUserRecordVerified.setInt(1, verified);
                updateUserRecordVerified.setLong(2, userId);
                updateUserRecordVerified.setLong(3, serverId);
                updateUserRecordVerified.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addUserConnectionAttempt(long userId, long serverId, String ip) {
        try {
            synchronized (getUserConnectionAttemptsByIP) {
                getUserConnectionAttemptsByIP.setString(1, ip);
                ResultSet rs = getUserConnectionAttemptsByIP.executeQuery();
                if (!rs.next()) {
                    synchronized (insertUserConnectionAttempt) {
                        insertUserConnectionAttempt.setLong(1, userId);
                        insertUserConnectionAttempt.setLong(2, serverId);
                        insertUserConnectionAttempt.setString(3, ip);
                        insertUserConnectionAttempt.execute();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Long> getUserConnectionAttemptsByIP(String ip) {
        ArrayList<Long> ret = new ArrayList<>();
        try {
            synchronized (getUserConnectionAttemptsByIP) {
                getUserConnectionAttemptsByIP.setString(1, ip);
                ResultSet rs = getUserConnectionAttemptsByIP.executeQuery();
                while (rs.next()) {
                    ret.add(rs.getLong("user_id"));
                }
            }
            return ret;
        } catch (SQLException e) {
            e.printStackTrace();
            return ret;
        }
    }

    public void addVerifiedConnection(long userId, long serverId, String connectionId, String connectionType, String connectionName, String connectionToken) {
        try {
            synchronized (getVerifiedConnectionsByUser) {
                getVerifiedConnectionsByUser.setLong(1, userId);
                getVerifiedConnectionsByUser.setLong(2, serverId);
                ResultSet rs = getVerifiedConnectionsByUser.executeQuery();
                while (rs.next()) {
                    if (rs.getString("id").equalsIgnoreCase(connectionId) && rs.getString("connection_name").equalsIgnoreCase(connectionName)) {
                        return;
                    }
                }
            }
            synchronized (insertVerifiedConnection) {
                insertVerifiedConnection.setLong(1, userId);
                insertVerifiedConnection.setLong(2, serverId);
                insertVerifiedConnection.setString(3, connectionId);
                insertVerifiedConnection.setString(4, connectionType);
                insertVerifiedConnection.setString(5, connectionName);
                insertVerifiedConnection.setString(6, connectionToken);
                insertVerifiedConnection.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPingLeaderboard() {
        try {
            synchronized (getPingLeaderboard) {
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
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "";
        }
    }

    public int getFastestPing(long userId) {
        try {
            synchronized (getPingLeaderboardByUser) {
                getPingLeaderboardByUser.setLong(1, userId);
                ResultSet rs = getPingLeaderboardByUser.executeQuery();

                if (rs.next()) {
                    return rs.getInt("ping");
                } else {
                    return Integer.MAX_VALUE;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Integer.MAX_VALUE;
        }
    }

    public void setFastestPing(long userId, int ping) {
        try {
            synchronized (updatePingLeaderboardByUser) {
                updatePingLeaderboardByUser.setLong(1, userId);
                updatePingLeaderboardByUser.setInt(2, ping);
                updatePingLeaderboardByUser.setInt(3, ping);
                updatePingLeaderboardByUser.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int incrementPingCount(long userId) {
        try {
            synchronized (incrementPingCountByUser) {
                incrementPingCountByUser.setLong(1, userId);
                incrementPingCountByUser.execute();
            }

            synchronized (getPingCountByUser) {
                getPingCountByUser.setLong(1, userId);
                ResultSet rs = getPingCountByUser.executeQuery();

                if (rs.next()) {
                    return rs.getInt("ping_count");
                } else {
                    return 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void getCurrentDunces(BiConsumer<Long, Instant> dunceConsumer) {
        try {
            synchronized (getUndunceInstants) {
                ResultSet rs = getUndunceInstants.executeQuery();

                while (rs.next()) {
                    dunceConsumer.accept(rs.getLong("user_id"), rs.getTimestamp("undunce_instant").toInstant());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isDunced(long userId) {
        try {
            synchronized (getUndunceInstantByUser) {
                getUndunceInstantByUser.setLong(1, userId);
                ResultSet rs = getUndunceInstantByUser.executeQuery();

                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void insertUndunceInstant(long userId, Instant undunceInstant) {
        try {
            synchronized (insertUndunceInstant) {
                insertUndunceInstant.setLong(1, userId);
                insertUndunceInstant.setTimestamp(2, Timestamp.from(undunceInstant));
                insertUndunceInstant.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateUndunceInstant(long userId, Instant undunceInstant) {
        try {
            synchronized (updateUndunceInstantByUser) {
                updateUndunceInstantByUser.setTimestamp(1, Timestamp.from(undunceInstant));
                updateUndunceInstantByUser.setLong(2, userId);
                updateUndunceInstantByUser.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeUndunceInstant(long userId) {
        try {
            synchronized (removeUndunceInstantByUser) {
                removeUndunceInstantByUser.setLong(1, userId);
                removeUndunceInstantByUser.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void addDunceStoredRoles(User user, Server server) {
        try {
            for (Role r : server.getRoles(user)) {
                if (r.getId() != 312324674275115008L && !r.isEveryoneRole()) {
                    synchronized (insertDunceStoredRole) {
                        insertDunceStoredRole.setLong(1, user.getId());
                        insertDunceStoredRole.setLong(2, r.getId());
                        insertDunceStoredRole.execute();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void popDunceStoredRoles(User user, Server server) {
        try {
            synchronized (getDunceStoredRoleByUser) {
                getDunceStoredRoleByUser.setLong(1, user.getId());
                ResultSet rs = getDunceStoredRoleByUser.executeQuery();

                while (rs.next()) {
                    server.getRoleById(rs.getLong("role_id")).ifPresent(user::addRole);
                }
            }

            synchronized (removeDunceStoredRole) {
                removeDunceStoredRole.setLong(1, user.getId());
                removeDunceStoredRole.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void warnUser(long userId, Instant timestamp, String messageLink, String reason) {
        try {
            synchronized (insertWarning) {
                insertWarning.setLong(1, userId);
                insertWarning.setTimestamp(2, Timestamp.from(timestamp));
                insertWarning.setString(3, reason);
                insertWarning.setString(4, messageLink);

                insertWarning.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int countUserWarnings(long userId) {
        try {
            synchronized (getWarningsCountByUser) {
                getWarningsCountByUser.setLong(1, userId);

                ResultSet rs = getWarningsCountByUser.executeQuery();

                if (rs.next()) {
                    return rs.getInt("warning_count");
                } else {
                    return 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void enumerateWarnings(long userId, EmbedBuilder response) {
        try {
            synchronized (getWarningsByUser) {
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
