package gq.luma.bot.render.fs.frame;

import com.aparapi.Kernel;
import com.aparapi.Range;
import gq.luma.bot.render.fs.weighters.DemoWeighter;
import jnr.ffi.Pointer;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class AparapiAccumulatorFrame implements Frame {
    private ProcessingKernel kernel;
    private Range processingRange;
    private ByteBuffer inBuffer;

    private int pixelCount;

    private class ProcessingKernel extends Kernel {

        private final byte[] exportBuffer;
        private final float[] weight;
        private final byte[] importBuffer;

        ProcessingKernel(byte[] exportBuffer, byte[] importBuffer){
            setExplicit(true);
            this.exportBuffer = exportBuffer;
            this.importBuffer = importBuffer;
            this.weight = new float[1];
        }

        @Override
        public void run() {
            int i = getGlobalId();
            float additionFactor = ((importBuffer[i] & 0xFF) * weight[0]);
            exportBuffer[i] += additionFactor;
        }
    }

    public AparapiAccumulatorFrame(ByteBuffer buffer, int pixelCount){
        this.inBuffer = buffer;
        this.pixelCount = pixelCount;
        this.kernel = new ProcessingKernel(new byte[pixelCount], new byte[pixelCount]);
        this.processingRange = Range.create(pixelCount);
    }

    @Override
    public void packet(byte[] data, int offset, int writeLength, DemoWeighter weighter, int position, int index) {
        int frameOffset = 0;
        int offsetShift = 18;
        if(offset == 0){
            kernel.weight[0] = weighter.weightFloat(position);
            kernel.put(kernel.weight);
            frameOffset = 18;
            offsetShift = 0;
        }
        System.arraycopy(data, frameOffset, kernel.importBuffer, offset - offsetShift, writeLength - frameOffset);
        if(offset + writeLength - 18 >= pixelCount){
            kernel.put(kernel.importBuffer);
            kernel.execute(processingRange);
        }
    }

    @Override
    public void packet(Pointer buf, long offset, long writeLength, DemoWeighter weighter, int position, int index) {
        int frameOffset = 0;
        int offsetShift = 18;
        if(offset == 0){
            kernel.weight[0] = weighter.weightFloat(position);
            kernel.put(kernel.weight);
            frameOffset = 18;
            offsetShift = 0;
        }
        buf.get(frameOffset, kernel.importBuffer, (int)(offset - offsetShift), (int)(writeLength - frameOffset));
        if(offset + writeLength - 18 >= pixelCount){
            kernel.put(kernel.importBuffer);
            kernel.execute(processingRange);
        }
    }

    @Override
    public void finishData() {
        kernel.get(kernel.exportBuffer);

        inBuffer.rewind();
        inBuffer.put(kernel.exportBuffer, 0, pixelCount);
    }

    @Override
    public void reset() {
        Arrays.fill(kernel.exportBuffer, (byte) 0);
        kernel.put(kernel.exportBuffer);
        inBuffer.rewind();
    }
}
