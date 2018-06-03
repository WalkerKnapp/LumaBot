package gq.luma.bot.render.fs.frame;

import gq.luma.bot.render.fs.weighters.DemoWeighter;
import io.humble.ferry.Buffer;
import io.humble.video.MediaPicture;
import io.humble.video.MediaPictureResampler;
import jnr.ffi.Pointer;
import ru.serce.jnrfuse.LibFuse;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionException;

/*
* The frame optimized for the most possible speed.
* */
public class UnsafeFrame implements Frame {
    private MediaPicture in;
    private MediaPicture out;

    private ByteBuffer resampleBuffer;
    private long resampleBufferBasePointer;

    private Unsafe unsafe;

    public UnsafeFrame(MediaPicture referenceIn, MediaPicture referenceOut) {
        this.in = referenceIn.copyReference();
        this.out = referenceOut.copyReference();

        Buffer buffer = in.getData(0);
        int size = in.getDataPlaneSize(0);
        this.resampleBuffer = buffer.getByteBuffer(0, size);
        buffer.delete();

        try {
            Field bAddr = java.nio.Buffer.class.getDeclaredField("address");
            bAddr.setAccessible(true);
            this.resampleBufferBasePointer = (long) bAddr.get(resampleBuffer);

            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            this.unsafe = (Unsafe) f.get(null);
        } catch (Exception e){
            throw new CompletionException(e);
        }
    }

    @Override
    public void packet(byte[] data, int offset, int writeLength, DemoWeighter weighter, int position, int index) {
        //Deprecated
    }

    @Override
    public void packet(Pointer buf, LibFuse lib, long offset, long writeLength, DemoWeighter weighter, int position, int index) {
        int frameOffset = 0;
        long destOffset = 0;
        if(offset == 0){
            frameOffset = 18;
        } else {
            destOffset = offset - 18;
        }

        unsafe.copyMemory(buf.address() + frameOffset, resampleBufferBasePointer + destOffset, writeLength - frameOffset);
    }

    @Override
    public MediaPicture writeMedia(MediaPictureResampler resampler, long timestamp) {
        in.setTimeStamp(timestamp);
        in.setComplete(true);

        resampler.resample(out, in);

        return out;
    }

    @Override
    public MediaPicture getUnprocessed(long timestamp){
        in.setTimeStamp(timestamp);
        in.setComplete(true);

        return in;
    }

    @Override
    public void reset() {
        this.resampleBuffer.rewind();
    }
}
