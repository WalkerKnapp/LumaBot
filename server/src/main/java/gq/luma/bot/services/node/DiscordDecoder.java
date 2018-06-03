package gq.luma.bot.services.node;

import de.btobastian.javacord.entities.channels.TextChannel;
import gq.luma.bot.ByteBufferBackedInputStream;
import gq.luma.bot.WebsocketDecoder;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DiscordDecoder extends WebsocketDecoder {
    private TextChannel channel;
    private ByteBuffer buffer;

    public DiscordDecoder(String fileName, TextChannel channel) {
        super(fileName);
        this.channel = channel;
        this.buffer = ByteBuffer.allocate(8 * 1024 * 1024);
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException {
        this.buffer.put(buffer);
    }

    @Override
    public String finish() throws IOException {
        channel.sendMessage(new ByteBufferBackedInputStream(buffer), getFileName());
        return null;
    }
}
