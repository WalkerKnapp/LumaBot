package gq.luma.bot.render.fs.frame;

import gq.luma.bot.render.fs.weighters.DemoWeighter;
import io.humble.ferry.Buffer;
import io.humble.video.MediaPicture;
import io.humble.video.MediaPictureResampler;
import jnr.ffi.Pointer;
import ru.serce.jnrfuse.LibFuse;

import java.nio.ByteBuffer;

public class WeightedFrame implements Frame {

    private byte[] totalData;
    private int bufferPointer;

    private MediaPicture in;
    private MediaPicture out;
    private ByteBuffer buffer;

    private double latestWeight;

    private boolean flag1 = false;
    private boolean flag2 = false;
    private boolean flag3 = false;

    public WeightedFrame(MediaPicture inFrame, MediaPicture outFrame, int pixelCount){
        this.in = inFrame.copyReference();
        this.out = outFrame.copyReference();

        this.totalData = new byte[pixelCount];

        Buffer buffer = in.getData(0);
        int size = in.getDataPlaneSize(0);
        this.buffer = buffer.getByteBuffer(0, size);
        buffer.delete();
    }

    @Override
    public void packet(byte[] data, int offset, int writeLength, DemoWeighter weighter, int position, int index) {
        int frameOffset = 0;
        if(offset == 0){
            flag1 = data[2] == 0x02;
            flag2 = data[16] == 0x20;
            flag3 = data[16] == 0x18;

            frameOffset = 18;
            bufferPointer = 0;

            latestWeight = weighter.weight(position);
        }

        if(position == 0){
            if(flag1 && flag2){
                int i = 0;
                while (frameOffset < writeLength) {
                    if(i % 4 == 3)
                        frameOffset++;
                    else
                        totalData[bufferPointer++] = (byte)(data[frameOffset++] * latestWeight);
                    i++;
                }
            }
            else if(flag1 && flag3){
                while(frameOffset < writeLength) {
                    totalData[bufferPointer++] = (byte) ((data[frameOffset++] & 0xFF) * latestWeight);
                }
            }
            else {
                while (frameOffset < writeLength) {
                    int ident = data[frameOffset++];
                    if ((ident & 0x80) == 0) {
                        for (int j = 0; j <= ident; j++) {
                            totalData[bufferPointer++] = (byte)(data[frameOffset++] * latestWeight);
                            totalData[bufferPointer++] = (byte)(data[frameOffset++] * latestWeight);
                            totalData[bufferPointer++] = (byte)(data[frameOffset++] * latestWeight);
                        }
                    } else {
                        ident &= 0x7f;
                        final int b = (int) (data[frameOffset++] * latestWeight);
                        final int g = (int) (data[frameOffset++] * latestWeight);
                        final int r = (int) (data[frameOffset++] * latestWeight);
                        for (int j = 0; j <= ident; j++) {
                            totalData[bufferPointer++] = (byte)b;
                            totalData[bufferPointer++] = (byte)g;
                            totalData[bufferPointer++] = (byte)r;
                        }
                    }
                }
            }
        }
        else {
            if(flag1 && flag2){
                int i = 0;
                while (frameOffset < writeLength) {
                    if(i % 4 == 3)
                        frameOffset++;
                    else
                        totalData[bufferPointer++] += data[frameOffset++] * latestWeight;
                    i++;
                }
            }
            else if(flag1 && flag3){
                while(frameOffset < writeLength) {
                    totalData[bufferPointer++] += (int) ((data[frameOffset++] & 0xFF) * latestWeight);
                }
            }
            else {
                while (frameOffset < writeLength) {
                    int ident = data[frameOffset++];
                    if ((ident & 0x80) == 0) {
                        for (int j = 0; j <= ident; j++) {
                            totalData[bufferPointer++] += data[frameOffset++] * latestWeight;
                            totalData[bufferPointer++] += data[frameOffset++] * latestWeight;
                            totalData[bufferPointer++] += data[frameOffset++] * latestWeight;
                        }
                    } else {
                        ident &= 0x7f;
                        final int b = (int) (data[frameOffset++] * latestWeight);
                        final int g = (int) (data[frameOffset++] * latestWeight);
                        final int r = (int) (data[frameOffset++] * latestWeight);
                        for (int j = 0; j <= ident; j++) {
                            totalData[bufferPointer++] += b;
                            totalData[bufferPointer++] += g;
                            totalData[bufferPointer++] += r;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void packet(Pointer buf, LibFuse libFuse, long offset, long writeLength, DemoWeighter weighter, int position, int index) {
        int frameOffset = 0;
        if(offset == 0){
            frameOffset = 18;
            bufferPointer = 0;
            latestWeight = weighter.weight(position);
        }
        byte[] data = new byte[(int) writeLength];
        buf.get(0, data, 0, (int) (writeLength));
        if(position == 0){
            while(frameOffset < writeLength) {
                totalData[bufferPointer++] = (byte) ((data[frameOffset++] & 0xFF) * latestWeight);
            }
        } else {
            while(frameOffset < writeLength) {
                totalData[bufferPointer++] += (int) ((data[frameOffset++] & 0xFF) * latestWeight);
            }
        }
    }

    @Override
    public MediaPicture writeMedia(MediaPictureResampler resampler, long timestamp) {

        buffer.put(totalData);

        in.setTimeStamp(timestamp);
        in.setComplete(true);

        resampler.resample(out, in);

        return out;
    }

    @Override
    public MediaPicture getUnprocessed(long timestamp){
        buffer.put(totalData);

        in.setTimeStamp(timestamp);
        in.setComplete(true);

        return in;
    }

    @Override
    public void reset() {
        buffer.rewind();
    }
}
