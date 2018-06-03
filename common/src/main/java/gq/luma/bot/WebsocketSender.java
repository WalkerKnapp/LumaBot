package gq.luma.bot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

public class WebsocketSender {
    private static final int PADDING_SIZE = 255 + 4 + 4 + 4;
    private static final int MAX_PACKET_SIZE = 1024 * 1024 * 10;

    public interface OutgoingWebsocket {
        void send(ByteBuffer buffer);
    }

    private InputStream stream;
    private byte[] fileName;
    private OutgoingWebsocket sender;
    private WebsocketDestination destination;

    public WebsocketSender(OutgoingWebsocket sender, String fileName, InputStream stream, WebsocketDestination destination){
        this.fileName = new byte[255];
        System.arraycopy(fileName.getBytes(),0, this.fileName, 0, fileName.getBytes().length);
        this.stream = stream;
        this.sender = sender;
        this.destination = destination;
    }

    public CompletableFuture<Void> sendCompletelyAsync(ExecutorService executor){
        return CompletableFuture.runAsync(() -> {
            try {
                sendCompletely();
            } catch (IOException | LumaException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public void sendCompletely() throws IOException, LumaException {
        int availible;
        int offset = 0;
        while ((availible = stream.available()) > 0){
            if (availible > MAX_PACKET_SIZE) {
                sender.send(paddedRead(MAX_PACKET_SIZE, offset));
                offset += MAX_PACKET_SIZE;
            } else {
                sender.send(paddedRead(availible, offset));
                offset += MAX_PACKET_SIZE;
            }
        }
        sender.send(readFinal(offset));
    }

    private ByteBuffer paddedRead(int length, int offset) throws IOException, LumaException {
        ByteBuffer buffer = ByteBuffer.allocate(length + PADDING_SIZE);
        buffer.put(fileName);
        buffer.putInt(offset);
        buffer.putInt(0);
        buffer.putInt(destination.getId());
        readStream(buffer, length);
        return buffer;
    }

    private ByteBuffer readFinal(int offset){
        ByteBuffer buffer = ByteBuffer.allocate(PADDING_SIZE);
        buffer.put(fileName);
        buffer.putInt(offset);
        buffer.putInt(1);
        return buffer;
    }

    private void readStream(ByteBuffer buffer, int length) throws IOException, LumaException {
        int read = 0;
        for(int i = 0; i < length; i++){
            if((read = stream.read()) != -1){
                buffer.put((byte) read);
            } else {
                break;
            }
        }
        if(read == -1){
            throw new LumaException("Could not read available bytes from InputStream");
        }
    }
}
