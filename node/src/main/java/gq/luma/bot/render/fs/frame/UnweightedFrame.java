package gq.luma.bot.render.fs.frame;

import gq.luma.bot.render.fs.weighters.DemoWeighter;
import jnr.ffi.Pointer;

import java.nio.ByteBuffer;

public class UnweightedFrame implements Frame {

    private ByteBuffer resampleBuffer;

    private boolean flag1 = false;
    private boolean flag2 = false;
    private boolean flag3 = false;

    public UnweightedFrame(ByteBuffer buffer){
        this.resampleBuffer = buffer;
    }

    @Override
    public void packet(byte[] data, int offset, int writeLength, DemoWeighter weighter, int position, int index) {
        int frameOffset = 0;
        if(offset == 0){
            flag1 = data[2] == 0x02;
            flag2 = data[16] == 0x20;
            flag3 = data[16] == 0x18;
            frameOffset = 18;
        }

        if(flag1 && flag2) {
            int i = 0;
            while (frameOffset < writeLength) {
                if(i % 4 == 3)
                    frameOffset++;
                else
                    resampleBuffer.put((offset + frameOffset) - 18, data[frameOffset++]);
                i++;
            }
        }
        else if(flag1 && flag3){
            resampleBuffer.put(data, frameOffset, writeLength - frameOffset);
        }
        else {
            while (frameOffset < writeLength){
                int ident = data[frameOffset++];
                if((ident & 0x80) == 0){
                    for(int j = 0; j <= ident; j++){
                        resampleBuffer.put((offset + frameOffset) - 18, data[frameOffset++]);
                        resampleBuffer.put((offset + frameOffset) - 18, data[frameOffset++]);
                        resampleBuffer.put((offset + frameOffset) - 18, data[frameOffset++]);
                    }
                }
                else{
                    ident &= 0x7f;
                    final byte b = data[frameOffset++];
                    final byte g = data[frameOffset++];
                    final byte r = data[frameOffset++];
                    for(int j = 0; j <= ident; j++){
                        resampleBuffer.put((offset + frameOffset) - 18, b);
                        resampleBuffer.put((offset + frameOffset) - 18, g);
                        resampleBuffer.put((offset + frameOffset) - 18, r);
                    }
                }
            }
        }
    }

    @Override
    public void packet(Pointer buf, long offset, long writeLength, DemoWeighter weighter, int position, int index) {
        int frameOffset = 0;
        if(offset == 0){
            frameOffset = 18;
        }
        byte[] tmp = new byte[(int) writeLength - frameOffset];
        buf.get(frameOffset, tmp, 0, (int) writeLength - frameOffset);
        resampleBuffer.put(tmp);
    }

    @Override
    public void finishData() {

    }

    @Override
    public void reset() {
        this.resampleBuffer.rewind();
    }
}
