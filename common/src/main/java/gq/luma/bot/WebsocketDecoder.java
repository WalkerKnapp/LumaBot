package gq.luma.bot;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class WebsocketDecoder {
    private String fileName;

    public WebsocketDecoder(String fileName){
        this.fileName = fileName;
    }

    public abstract void write(ByteBuffer buffer) throws IOException;

    public abstract String finish() throws IOException;

    public String getFileName() {
        return fileName;
    }
}
