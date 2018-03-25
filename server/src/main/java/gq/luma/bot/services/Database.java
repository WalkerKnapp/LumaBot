package gq.luma.bot.services;

import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.channels.ServerTextChannel;
import de.btobastian.javacord.entities.channels.TextChannel;
import gq.luma.bot.reference.DefaultReference;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Database implements Service {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private static Connection conn;

    private static PreparedStatement getChannel;
    private static PreparedStatement updateChannelPrefix;
    private static PreparedStatement updateChannelLocale;
    private static PreparedStatement updateChannelNotify;
    private static PreparedStatement insertChannel;

    private static PreparedStatement getServer;
    private static PreparedStatement updateServerPrefix;
    private static PreparedStatement updateServerLocale;
    private static PreparedStatement updateServerMembers;
    private static PreparedStatement updateServerRoles;
    private static PreparedStatement updateServerNotify;
    private static PreparedStatement updateClarifaiMonthlyCap;
    private static PreparedStatement updateClarifaiCount;
    private static PreparedStatement updateClarifaiResetDate;
    private static PreparedStatement insertServer;

    private static PreparedStatement getUser;
    private static PreparedStatement updateUserNotify;
    private static PreparedStatement updateUserPermissions;
    private static PreparedStatement insertUser;

    private static PreparedStatement getResult;
    private static PreparedStatement insertResult;

    private static PreparedStatement getRoleByName;
    private static PreparedStatement getAllRoles;

    private static PreparedStatement getNodeByToken;
    private static PreparedStatement getNodeBySession;
    private static PreparedStatement getAllNodes;
    private static PreparedStatement updateNodeSession;
    private static PreparedStatement updateNodeTask;
    private static PreparedStatement updateNode;

    private static PreparedStatement getFilterByServer;

    @Override
    public void startService() throws SQLException, ClassNotFoundException {
        open();
        Runtime.getRuntime().addShutdownHook(new Thread(Database::close));
    }

    public static void open() throws SQLException, ClassNotFoundException {

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
        updateServerMembers = conn.prepareStatement("UPDATE servers SET members = ? WHERE id = ?");
        updateServerRoles = conn.prepareStatement("UPDATE servers SET roles = ? WHERE id = ?");
        updateServerNotify = conn.prepareStatement("UPDATE servers SET notify = ? WHERE id = ?");
        updateClarifaiCount = conn.prepareStatement("UPDATE servers SET clarifai_count = ? WHERE id = ?");
        insertServer = conn.prepareStatement("INSERT INTO servers (prefix, locale, members, roles, notify, monthly_clarifai_cap, clarifai_count, clarifai_reset_date, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

        getUser = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
        updateUserPermissions = conn.prepareStatement("UPDATE users SET permissions = ? WHERE id = ?");
        updateUserNotify = conn.prepareStatement("UPDATE users SET notify = ? WHERE id = ?");
        insertUser = conn.prepareStatement("INSERT INTO users (permissions, notify, id) VALUES (?, ?, ?)");

        getResult = conn.prepareStatement("SELECT * FROM render_results WHERE id = ?");
        insertResult = conn.prepareStatement("INSERT INTO render_results (id, name, type, code, thumbnail, requester, width, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

        getRoleByName = conn.prepareStatement("SELECT * FROM donor_roles WHERE name = ?");
        getAllRoles = conn.prepareStatement("SELECT * FROM donor_roles");

        getNodeByToken = conn.prepareStatement("SELECT * FROM nodes WHERE token = ?");
        getNodeBySession = conn.prepareStatement("SELECT * FROM nodes WHERE session = ?");
        getAllNodes = conn.prepareStatement("SELECT * FROM nodes");
        updateNodeSession = conn.prepareStatement("UPDATE nodes SET session = ? WHERE token = ?");
        updateNodeTask = conn.prepareStatement("UPDATE nodes SET task = ? WHERE token = ?");
        updateNode = conn.prepareStatement("UPDATE nodes SET session = ?, last_known_host = ?, last_known_name = ? WHERE token = ?");

        getFilterByServer = conn.prepareStatement("SELECT * FROM filters WHERE server = ?");
    }

    //Results

    public static synchronized ResultSet getResult(int id) throws SQLException {
        getResult.setInt(1, id);
        return getResult.executeQuery();
    }

    public static synchronized void addResult(int id, String name, String type, String code, String thumbnail, long requester, int width, int height) throws SQLException {
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

    public static synchronized String getEffectivePrefix(TextChannel channel) throws SQLException {
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

    public static synchronized String getChannelPrefix(TextChannel channel) throws SQLException {
        return getChannelPrefix(channel.getId());
    }

    private static synchronized String getChannelPrefix(long id) throws SQLException {
        getChannel.setLong(1, id);
        ResultSet rs = getChannel.executeQuery();
        if(rs.next()) {
            return rs.getString("prefix");
        }
        return null;
    }

    //Servers

    public static boolean isServerPresent(Server server) {
        try {
            getServer.setLong(1, server.getId());
            return getServer.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void addServer(Server server, int clarifaiCap, Instant clarifaiResetDate) throws SQLException {
        insertServer.setString(1, null);
        insertServer.setString(2, null);
        insertServer.setString(3, null);
        insertServer.setString(4, null);
        insertServer.setInt(5, 0);
        insertServer.setInt(6, clarifaiCap);
        insertServer.setInt(7, 0);
        insertServer.setTimestamp(8, Timestamp.from(clarifaiResetDate));
        insertServer.setLong(9, server.getId());
        insertServer.execute();
    }

    public static long getServerClarifaiResetDate(Server server) throws SQLException {
        getServer.setLong(1, server.getId());
        ResultSet rs = getServer.executeQuery();
        if(rs.next()){
            return rs.getLong("clarifaiResetDate");
        }
        return 0;
    }

    public static void setServerClarifaiResetDate(Server server, long date) throws SQLException {
        updateClarifaiResetDate.setLong(1, date);
        updateClarifaiResetDate.setLong(2, server.getId());
        updateClarifaiResetDate.execute();
    }

    public static ResultSet getServerFilters(Server server) throws SQLException {
        getFilterByServer.setLong(1, server.getId());
        return getFilterByServer.executeQuery();
    }

    public static synchronized Optional<List<String>> getServerPermsForUser(Server server, User user) throws SQLException {
        getServer.setLong(1, server.getId());
        ResultSet rs = getServer.executeQuery();
        if(rs.next() && rs.getString("members") != null){
            return Stream.of(rs.getString("members")
                    .split(";"))
                    .map(s -> s.split(":"))
                    .filter(a -> a[0].equalsIgnoreCase(user.getIdAsString()))
                    .map(a -> a[1])
                    .map(s -> s.split(","))
                    .map(List::of)
                    .findAny();

        }
        return Optional.empty();
    }

    public static synchronized Optional<List<String>> getGlobalPermsForUser(User user) throws SQLException {
        getUser.setLong(1, user.getId());
        ResultSet rs = getUser.executeQuery();
        if(rs.next()){
            return Optional.of(List.of(rs.getString("permissions").split(",")));
        }
        return Optional.empty();
    }
    public static synchronized String getServerPrefix(Server server) throws SQLException {
        return getServerPrefix(server.getId());
    }

    private static synchronized String getServerPrefix(long id) throws SQLException {
        getServer.setLong(1, id);
        ResultSet rs = getServer.executeQuery();
        if(rs.next()) {
            return rs.getString("prefix");
        }
        return null;
    }

    public static synchronized String getEffectiveLocale(TextChannel channel) throws SQLException {
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

    public static synchronized String getChannelLocale(TextChannel channel) throws SQLException {
        return getChannelLocale(channel.getId());
    }

    private static synchronized String getChannelLocale(long id) throws SQLException {
        getChannel.setLong(1, id);
        ResultSet rs = getChannel.executeQuery();
        if(rs.next()){
            return rs.getString("locale");
        }
        return null;
    }

    public static synchronized String getServerLocale(Server server) throws SQLException {
        return getServerLocale(server.getId());
    }

    private static synchronized String getServerLocale(long id) throws SQLException {
        getServer.setLong(1, id);
        ResultSet rs = getServer.executeQuery();
        if(rs.next()){
            return rs.getString("locale");
        }
        return null;
    }

    public static synchronized void setServerPrefix(Server server, String prefix) throws SQLException {
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

    public static synchronized void setChannelPrefix(TextChannel channel, String prefix) throws SQLException {
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

    public static synchronized void setServerLocale(Server server, String locale) throws SQLException {
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

    public static synchronized void setChannelLocale(TextChannel channel, String locale) throws SQLException {
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
            insertChannel.setInt(4, 0);
            insertChannel.setLong(5, channel.getId());
            insertChannel.execute();
        }
    }

    public static synchronized Optional<Long> getRoleByName(String name) throws SQLException {
        getRoleByName.setString(1, name.substring(0, 1).toUpperCase() + name.substring(1));
        ResultSet rs = getRoleByName.executeQuery();

        if(rs.next()){
            return Optional.of(rs.getLong("id"));
        } else {
            return Optional.empty();
        }
    }

    public static synchronized List<String> getAvailibeRoles() throws SQLException {
        ResultSet rs = getAllRoles.executeQuery();
        List<String> ret = new ArrayList<>();
        while(rs.next()){
            ret.add(rs.getString("name"));
        }
        return ret;
    }

    //Nodes

    public static synchronized Optional<ResultSet> getNodeByToken(String token) throws SQLException {
        getNodeByToken.setString(1, token);
        ResultSet rs = getNodeByToken.executeQuery();
        if(rs.next()){
            return Optional.of(rs);
        } else {
            return Optional.empty();
        }
    }

    public static synchronized void updateNodeSession(String token, String session) throws SQLException {
        updateNodeSession.setString(1, session);
        updateNodeSession.setString(2, token);
        updateNodeSession.execute();
    }

    public static synchronized void updateNode(String token, String session, String lastKnownHost, String lastKnownName) throws SQLException {
        updateNode.setString(1, session);
        updateNode.setString(2, lastKnownHost);
        updateNode.setString(3, lastKnownName);
        updateNode.setString(4, token);
        updateNode.execute();
    }

    public static void close(){
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
