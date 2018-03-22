package gq.luma.bot.render.audio;

import io.humble.video.MediaAudio;
import jnr.ffi.Pointer;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public interface AudioProcessor {
    void packet(byte[] data, int offset, int writeLength, Consumer<MediaAudio> audioConsumer);

    void packet(Pointer buf, long offset, long writeLength, Consumer<MediaAudio> audioConsumer);

    void waitForFinish() throws InterruptedException, ExecutionException;
}
