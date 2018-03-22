package gq.luma.bot.commands.params.io.output;

import com.eclipsesource.json.JsonObject;

import java.sql.SQLException;

public interface FileOutputter {
    String uploadFile(JsonObject data, long workingDir, String name, long requester, int width, int height) throws SQLException;
}
