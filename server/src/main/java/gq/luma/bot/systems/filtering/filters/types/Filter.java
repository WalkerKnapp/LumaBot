package gq.luma.bot.systems.filtering.filters.types;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Filter {
    private long id;
    private String name;
    private boolean enabled;
    private long server;
    private long creator;

    protected JsonObject typeSettings;

    private int serverScope;
    private List<Long> serverScopeParams;
    private int userScope;
    private List<Long> userScopeParams;

    private JsonObject effect;

    Filter(ResultSet rs) throws SQLException {
        this.id = rs.getLong("id");
        this.name = rs.getString("name");
        this.enabled = rs.getBoolean("enabled");
        this.server = rs.getLong("server");
        this.creator = rs.getLong("creator");

        this.typeSettings = Json.parse(rs.getString("type_settings")).asObject();

        this.serverScope = rs.getInt("server_scope");
        this.serverScopeParams = Json.parse(rs.getString("server_scope_settings"))
                .asArray().values().stream().map(JsonValue::asLong).collect(Collectors.toList());
        this.userScope = rs.getInt("user_scope");
        this.userScopeParams = Json.parse(rs.getString("user_scope_settings"))
                .asArray().values().stream().map(JsonValue::asLong).collect(Collectors.toList());

        this.effect = Json.parse(rs.getString("effect")).asObject();
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getServer() {
        return server;
    }

    public int getServerScope() {
        return serverScope;
    }

    public List<Long> getServerScopeParams() {
        return serverScopeParams;
    }

    public int getUserScope() {
        return userScope;
    }

    public List<Long> getUserScopeParams() {
        return userScopeParams;
    }

    public JsonObject getEffect() {
        return effect;
    }
}
