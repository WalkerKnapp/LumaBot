package gq.luma.bot.services;

import gq.luma.bot.commands.subsystem.permissions.PermissionSet;
import gq.luma.bot.reference.DefaultReference;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.systems.filtering.filters.ImageFilter;
import gq.luma.bot.systems.filtering.filters.LinkFilter;
import gq.luma.bot.systems.filtering.filters.SimpleFilter;
import gq.luma.bot.systems.filtering.filters.VirusFilter;
import gq.luma.bot.systems.filtering.filters.types.Filter;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class Database implements Service {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private Connection conn;

    private PreparedStatement getChannel;
    private PreparedStatement updateChannelPrefix;
    private PreparedStatement updateChannelLocale;
    private PreparedStatement updateChannelNotify;
    private PreparedStatement insertChannel;

    private PreparedStatement getServer;
    private PreparedStatement updateServerPrefix;
    private PreparedStatement updateServerLocale;
    private PreparedStatement updateServerNotify;
    private PreparedStatement updateClarifaiMonthlyCap;
    private PreparedStatement updateClarifaiCount;
    private PreparedStatement updateClarifaiResetDate;
    private PreparedStatement insertServer;

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

    private PreparedStatement getVerifiedUserById;
    private PreparedStatement insertVerifiedUser;

    private PreparedStatement getVerifiedIpsByUser;
    private PreparedStatement getVerifiedIp;
    private PreparedStatement insertVerifiedIp;

    private PreparedStatement getVerifiedConnectionsByUser;
    private PreparedStatement insertVerifiedConnection;


    @Override
    public void startService() throws SQLException, ClassNotFoundException {
        open();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    private void open() throws SQLException, ClassNotFoundException {

        //Class.forName("com.mysql.jdbc.Driver");

        conn = DriverManager.getConnection("jdbc:mysql://" + FileReference.mySQLLocation + "/Luma?user=" + KeyReference.sqlUser + "&password=" + KeyReference.sqlPass + "&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=EST");

        getChannel = conn.prepareStatement("SELECT * FROM channels WHERE id = ?");
        updateChannelPrefix = conn.prepareStatement("UPDATE channels SET prefix = ? WHERE id = ?");
        updateChannelLocale = conn.prepareStatement("UPDATE channels SET locale = ? WHERE id = ?");
        updateChannelNotify = conn.prepareStatement("UPDATE channels SET notify = ? WHERE id = ?");
        insertChannel = conn.prepareStatement("INSERT INTO channels (prefix, locale, notify, id) VALUES (?, ?, ?, ?)");

        getServer = conn.prepareStatement("SELECT * FROM servers WHERE id = ?");
        updateServerPrefix = conn.prepareStatement("UPDATE servers SET prefix = ? WHERE id = ?");
        updateServerLocale = conn.prepareStatement("UPDATE servers SET locale = ? WHERE id = ?");
        updateServerNotify = conn.prepareStatement("UPDATE servers SET notify = ? WHERE id = ?");
        updateClarifaiCount = conn.prepareStatement("UPDATE servers SET clarifai_count = ? WHERE id = ?");
        insertServer = conn.prepareStatement("INSERT INTO servers (prefix, locale, logging_channel, streams_channel, notify, monthly_clarifai_cap, clarifai_count, clarifai_reset_date, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

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

        getVerifiedUserById = conn.prepareStatement("SELECT * FROM verified_users WHERE id = ?");
        insertVerifiedUser = conn.prepareStatement("INSERT INTO verified_users (id, server_id, discord_token) VALUES (?,?,?)");

        getVerifiedIpsByUser = conn.prepareStatement("SELECT * FROM verified_ips WHERE user_id = ?");
        getVerifiedIp = conn.prepareStatement("SELECT * FROM verified_ips WHERE ip = ?");
        insertVerifiedIp = conn.prepareStatement("INSERT INTO verified_ips (user_id, server_id, ip) VALUES (?,?,?)");

        getVerifiedConnectionsByUser = conn.prepareStatement("SELECT * FROM verified_connections WHERE user_id = ?");
        insertVerifiedConnection = conn.prepareStatement("INSERT INTO verified_connections (user_id, server_id, id, connection_type, connection_name, token) VALUES (?,?,?,?,?,?)");
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

    public synchronized boolean isUserVerified(long id) {
        try {
            getVerifiedUserById.setLong(1, id);
            ResultSet rs = getVerifiedUserById.executeQuery();
            return rs.next();
        } catch (SQLException e){
            e.printStackTrace();
            return false;
        }
    }

    public synchronized void writeConnectionBoxes(long id, StringBuilder sb) {
        try {
            getVerifiedConnectionsByUser.setLong(1, id);
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

    public synchronized void writeIps(long id, StringBuilder sb) {
        try {
            getVerifiedIpsByUser.setLong(1, id);
            ResultSet rs = getVerifiedIpsByUser.executeQuery();
            while (rs.next()) {
                sb.append(rs.getString("ip"));
                sb.append("</br>");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void verifyUser(long userId, long serverId, String accessToken) {
        try {
            getVerifiedUserById.setLong(1, userId);
            ResultSet rs = getVerifiedUserById.executeQuery();
            if(!rs.next()) {
                insertVerifiedUser.setLong(1, userId);
                insertVerifiedUser.setLong(2, serverId);
                insertVerifiedUser.setString(3, accessToken);
                insertVerifiedUser.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addVerifiedIP(long userId, long serverId, String ip) {
        try {
            getVerifiedIp.setString(1, ip);
            ResultSet rs = getVerifiedIp.executeQuery();
            if(!rs.next()) {
                insertVerifiedIp.setLong(1, userId);
                insertVerifiedIp.setLong(2, serverId);
                insertVerifiedIp.setString(3, ip);
                insertVerifiedIp.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addVerifiedConnection(long userId, long serverId, String connectionId, String connectionType, String connectionName, String connectionToken) {
        try {
            getVerifiedConnectionsByUser.setLong(1, userId);
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
            insertVerifiedConnection.setString(6, connectionType);
            insertVerifiedConnection.execute();
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
