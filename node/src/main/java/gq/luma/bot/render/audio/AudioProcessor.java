package gq.luma.bot.render.audio;

import jnr.ffi.Pointer;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface AudioProcessor {
    void packet(byte[] data, int offset, int writeLength, Consumer<Long> audioConsumer);

    void packet(Pointer buf, long offset, long writeLength, Consumer<Long> audioConsumer);

    void waitForFinish() throws InterruptedException, ExecutionException;
}
