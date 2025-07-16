package gq.luma.bot.commands.subsystem.permissions;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PermissionSet {
    public enum PermissionTarget {
        USER,
        ROLE,
        PERMISSION,
        OWNER
    }

    public enum Permission {
        ALL(0),
        MANAGE_PERMISSIONS(1),
        MANAGE_ALL_COMMAND_SETTINGS(2),
        MANAGE_FILTERS(3),
        MANAGE_VERIFY(38),
        ACCESS_ALL_MOD_COMMANDS(32),
        CHANGE_PREFIX_OR_LANGUAGE(33),
        SET_SLOW_MODE(34),
        SET_PANIC_MODE(35),
        CLEANUP(36),
        VOTES(37),
        PINS(38),
        DEVELOPER(64);

        final int index;

        Permission(int index){
            this.index = index;
        }
    }

    private final long bitmap;

    public PermissionSet(ResultSet rs) throws SQLException {
        this.bitmap = rs.getLong("permissions");
    }

    public boolean hasPermission(Permission permission){
        return ((bitmap >> permission.index) & 0x1) == 1;
    }

    public boolean effectivelyHasPermission(Permission permission){
        if(permission.index < 64 && hasPermission(Permission.ALL)){
            return true;
        }
        if(permission.index > 2 && permission.index < 32 && hasPermission(Permission.MANAGE_ALL_COMMAND_SETTINGS)){
            return true;
        }
        if(permission.index > 32 && permission.index < 64 && hasPermission(Permission.ACCESS_ALL_MOD_COMMANDS)){
            return true;
        }
        return hasPermission(permission);
    }
}
