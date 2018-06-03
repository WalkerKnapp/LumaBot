package gq.luma.bot.services.node;

import gq.luma.bot.Luma;
import gq.luma.bot.WebsocketDecoder;

import java.io.IOException;
import java.nio.ByteBuffer;

public class GDriveDecoder extends WebsocketDecoder {
    public GDriveDecoder(String fileName) {
        super(fileName);
        //TODO: This class
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException {

    }

    @Override
    public String finish() throws IOException {
        return null;
    }
}
