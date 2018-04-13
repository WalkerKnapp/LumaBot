package gq.luma.bot.commands.params.io.output;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.Luma;
import gq.luma.bot.services.Database;
import gq.luma.bot.utils.FileUtilities;
import gq.luma.bot.utils.WordEncoder;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.ByteBuffer;
import java.sql.SQLException;

public class WebserverOutputter  implements FileOutputter {

    private static final String URL_STUB = "https://luma.gq/renders/";

    @Override
    public String uploadFile(JsonObject data, long workingDir, String name, long requester, int width, int height) throws SQLException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put((byte) 0x0);
        buffer.put(DigestUtils.sha(FileUtilities.longToBytes(workingDir)), 0, 3);
        buffer.rewind();
        int id = buffer.getInt();

        Luma.database.addResult(id,
                name,
                data.getString("upload-type", "none"),
                data.getString("code", "none"),
                data.getString("thumbnail", "none"),
                requester,
                width,
                height);

        return URL_STUB + WordEncoder.encode(id);
    }
}
