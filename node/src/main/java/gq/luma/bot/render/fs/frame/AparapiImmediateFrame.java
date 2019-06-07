package gq.luma.bot.render.fs.frame;

import com.aparapi.Kernel;
import com.aparapi.Range;
import gq.luma.bot.render.fs.weighters.DemoWeighter;
import jnr.ffi.Pointer;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class AparapiImmediateFrame implements Frame {
    private ProcessingKernel kernel;

    private ByteBuffer byteBuffer;

    private int pixelCount;

    private class ProcessingKernel extends Kernel {

        private final float[] weight;
        private final byte[][] importBuffer;
        private final byte[] exportBuffer;
        private final int[] exportOffset;
        private final int[] importOffset;

        ProcessingKernel(byte[] exportBuffer){
            this.weight = new float[1];
            this.importBuffer = new byte[1][];
            this.exportBuffer = exportBuffer;
            this.exportOffset = new int[1];
            this.importOffset = new int[1];
        }

        @Override
        public void run() {
            int id = getGlobalId();
            int i = id + this.exportOffset[0];
            int j = id + this.importOffset[0];
            this.exportBuffer[i] += ((this.importBuffer[0][j] & 0xFF) * this.weight[0]);
        }
    }

    public AparapiImmediateFrame(ByteBuffer buffer, int pixelCount){
        this.byteBuffer = buffer;
        this.pixelCount = pixelCount;

        this.kernel = new ProcessingKernel(new byte[pixelCount]);
        kernel.setExecutionModeWithoutFallback(Kernel.EXECUTION_MODE.GPU);
        kernel.setExplicit(true);
    }

    private void processData(byte[] data, int offset, int writeLength, DemoWeighter weighter, int position) {
        Range processingRange;
        if(offset == 0){
            processingRange = Range.create(writeLength - 18);
            kernel.weight[0] = weighter.weightFloat(position);
            kernel.importOffset[0] = 18;
            kernel.exportOffset[0] = 0;
            kernel.put(kernel.weight).put(kernel.importOffset).put(kernel.exportOffset);
        } else {
            processingRange = Range.create(writeLength);
            kernel.importOffset[0] = 0;
            kernel.exportOffset[0] = offset - 18;
            kernel.put(kernel.importOffset).put(kernel.exportOffset);
        }
        kernel.importBuffer[0] = data;
        kernel.put(kernel.importBuffer);
        kernel.execute(processingRange);
    }

    @Override
    public void packet(byte[] data, int offset, int writeLength, DemoWeighter weighter, int position, int index) {
        processData(data, offset, writeLength, weighter, position);
    }

    @Override
    public void packet(Pointer buf, long offset, long writeLength, DemoWeighter weighter, int position, int index) {
        byte[] byteBuf = new byte[(int) writeLength];
        buf.get(0, byteBuf, 0, (int) writeLength);
        processData(byteBuf, (int)offset, (int)writeLength, weighter, position);
    }

    @Override
    public void finishData() {
        kernel.get(kernel.exportBuffer);
        byteBuffer.rewind();
        byteBuffer.put(kernel.exportBuffer);
    }

    @Override
    public void reset() {
        Arrays.fill(kernel.exportBuffer, (byte) 0);
        kernel.put(kernel.exportBuffer);
    }
}
