package gq.luma.bot.render.fs.frame;

import com.kenai.jffi.MemoryIO;
import gq.luma.bot.render.fs.weighters.DemoWeighter;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import ru.serce.jnrfuse.LibFuse;
import ru.serce.jnrfuse.flags.FuseBufFlags;
import ru.serce.jnrfuse.struct.FuseBufvec;

import java.nio.ByteBuffer;

public interface Frame {
    void packet(byte[] data, int offset, int writeLength, DemoWeighter weighter, int position, int index);

    void packet(Pointer buf, long offset, long writeLength, DemoWeighter weighter, int position, int index);

    void finishData();

    void reset();

    default void writeFuseToByteBuffer(FuseBufvec buf, LibFuse libFuse, ByteBuffer byteBuffer, long offset, long length) {
        byte[] result = new byte[(int) length];
        writeFuseToByteArray(buf, libFuse, result, offset, length);
        byteBuffer.put(result, 0, (int) length);
    }

    default void writeFuseToByteArray(FuseBufvec buf, LibFuse libFuse, byte[] result, long offset, long length){
        Pointer pointer;
        if (buf.count.get() == 1 && buf.buf.flags.get() == FuseBufFlags.FUSE_BUF_IS_FD) {
            pointer = buf.buf.mem.get();
        } else {
            FuseBufvec tmp = new FuseBufvec(Runtime.getSystemRuntime());
            long adr = MemoryIO.getInstance().allocateMemory(Struct.size(tmp), false);
            tmp.useMemory(Pointer.wrap(Runtime.getSystemRuntime(), adr));
            FuseBufvec.init(tmp, length);

            long mem = MemoryIO.getInstance().allocateMemory(length, false);
            if (mem == 0) {
                MemoryIO.getInstance().freeMemory(adr);
            }
            tmp.buf.mem.set(mem);
            int res = (int) libFuse.fuse_buf_copy(tmp, buf, 0);
            if (res <= 0) {
                MemoryIO.getInstance().freeMemory(adr);
                MemoryIO.getInstance().freeMemory(mem);
            }
            tmp.buf.size.set(res);
            pointer = tmp.buf.mem.get();
        }
        pointer.get(offset, result, 0, (int) length);
    }
}
