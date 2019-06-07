package gq.luma.bot.render.fs.frame;

import gq.luma.bot.render.fs.weighters.DemoWeighter;
import jnr.ffi.Pointer;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.CompletionException;

/*
* The frame optimized for the most possible speed.
* */
public class UnsafeFrame implements Frame {

    private java.nio.Buffer resampleBuffer;
    private long resampleBufferBasePointer;

    private Unsafe unsafe;

    public UnsafeFrame(java.nio.Buffer javaBuffer) {
        this.resampleBuffer = javaBuffer;

        try {
            Field bAddr = java.nio.Buffer.class.getDeclaredField("address");
            bAddr.setAccessible(true);
            this.resampleBufferBasePointer = (long) bAddr.get(javaBuffer);

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
    public void packet(Pointer buf, long offset, long writeLength, DemoWeighter weighter, int position, int index) {
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
    public void finishData() {

    }

    @Override
    public void reset() {
        this.resampleBuffer.rewind();
    }
}
