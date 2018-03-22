package gq.luma.bot.render.fs.frame;

import gq.luma.bot.utils.LumaException;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class AtomicFrame {
    private byte[] estimatedHeader;
    private AtomicIntegerArray totalData;
    private int frameSize;

    //private AtomicDouble totalBlend = new AtomicDouble(0);
    private AtomicInteger totalSize = new AtomicInteger(0);

    public AtomicFrame(int height, int width, int frameSize){
        this.totalData = new AtomicIntegerArray(height * width * 3);
        this.frameSize = frameSize;
    }

    /*public int recPacket(final byte[] data, int offset, final double weight, int index){
        int frameOffset = 0;
        if(index > latestIndex){
            if(estimatedHeader == null) {
                estimatedHeader = Arrays.copyOfRange(data, 0, 18);
                System.out.println("==Thread: " + Thread.currentThread().getName());
                System.out.println("-----------------------Setting header to: " + Hex.encodeHexString(estimatedHeader) + " due to " + index + ">" + latestIndex);
            }
            latestIndex = index;
            pixelOffset = 0;
            totalSize = 0;
            frameOffset = 18;
        }
        else if(index < latestIndex){
            System.err.println("YOU CANT GO BACKWARDS POR FAVOR. " + index + "<" + latestIndex);
        }

        totalSize += data.length;

        int maxValue = data.length;

        if(estimatedHeader[2] == 0x02 && estimatedHeader[16] == 0x20) {
            int i = 0;
            while (frameOffset < maxValue) {
                if(i % 4 == 3)
                    frameOffset++;
                else
                    totalData[pixelOffset++] += data[frameOffset++] * weight;
                i++;
            }
        }
        else if(estimatedHeader[2] == 0x02 && estimatedHeader[16] == 0x18){
            while(frameOffset < maxValue) {
                totalData[pixelOffset++] += data[frameOffset++] * weight;
            }
        }
        else {
            while (frameOffset < maxValue){
                int ident = data[frameOffset++];
                if((ident & 0x80) == 0){
                    for(int j = 0; j <= ident; j++){
                        totalData[pixelOffset++] += data[frameOffset++] * weight;
                        totalData[pixelOffset++] += data[frameOffset++] * weight;
                        totalData[pixelOffset++] += data[frameOffset++] * weight;
                    }
                }
                else{
                    ident &= 0x7f;
                    final int b = (int) (data[frameOffset++] * weight);
                    final int g = (int) (data[frameOffset++] * weight);
                    final int r = (int) (data[frameOffset++] * weight);
                    for(int j = 0; j <= ident; j++){
                        totalData[pixelOffset++] += b;
                        totalData[pixelOffset++] += g;
                        totalData[pixelOffset++] += r;
                    }
                }
            }
        }

        return pixelOffset + 18;
    }*/

    public int recPacketUnweighted(final byte[] data, int offset){
        int frameOffset = 0;
        if(offset == 0){
            if(estimatedHeader == null) {
                estimatedHeader = Arrays.copyOfRange(data, 0, 18);
            }
            frameOffset = 18;
        }

        totalSize.addAndGet(data.length);
        int maxValue = data.length;

        if(estimatedHeader[2] == 0x02 && estimatedHeader[16] == 0x20) {
            int i = 0;
            while (frameOffset < maxValue) {
                if(i % 4 == 3)
                    frameOffset++;
                else
                    totalData.set((offset + frameOffset) - 18, data[frameOffset++]);
                i++;
            }
        }
        else if(estimatedHeader[2] == 0x02 && estimatedHeader[16] == 0x18){
            while(frameOffset < maxValue) {
                totalData.set((offset + frameOffset) - 18, data[frameOffset++]);
            }
        }
        else {
            while (frameOffset < maxValue){
                int ident = data[frameOffset++];
                if((ident & 0x80) == 0){
                    for(int j = 0; j <= ident; j++){
                        totalData.set((offset + frameOffset) - 18, data[frameOffset++]);
                        totalData.set((offset + frameOffset) - 18, data[frameOffset++]);
                        totalData.set((offset + frameOffset) - 18, data[frameOffset++]);
                    }
                }
                else{
                    ident &= 0x7f;
                    final int b = (int) (data[frameOffset++]);
                    final int g = (int) (data[frameOffset++]);
                    final int r = (int) (data[frameOffset++]);
                    for(int j = 0; j <= ident; j++){
                        totalData.set((offset + frameOffset) - 18, b);
                        totalData.set((offset + frameOffset) - 18, g);
                        totalData.set((offset + frameOffset) - 18, r);
                    }
                }
            }
        }

        return frameOffset;
    }

    /*public byte[] export() throws LumaException {
        if(18 + totalData.length() != this.frameSize)
            throw new LumaException("Pixel mismatch to header length. (" + (18 + totalData.length()) + "!=" + this.frameSize + ")\nPlease contact the developer and describe what you were trying to do. ");

        if(estimatedHeader == null)
            throw new LumaException("Frame header is found null. This is an error that should be reported to the developer.");

        byte[] ret = new byte[this.frameSize];
        System.arraycopy(estimatedHeader, 0, ret, 0, 18);
        if(totalBlend.get() == 0) {
            for (int i = 0; i < totalData.length(); i++) {
                ret[i + 18] = (byte)totalData.get(i);
            }
        }
        else{
            for (int i = 0; i < totalData.length(); i++) {
                ret[i + 18] = (byte) (int) (totalData.get(i) / totalBlend.get());
            }
        }
        return ret;
    }*/

    public BufferedImage getImage(int width, int height) throws LumaException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] write = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int i = 0;
        int maxSize = totalData.length();
        while(i < maxSize){
            write[i/3] |= totalData.get(i++) & 0xff;
            write[i/3] |= (totalData.get(i++) & 0xff) << 8;
            write[i/3] |= (totalData.get(i++) & 0xff) << 16;
        }
        return convertTo(image, BufferedImage.TYPE_3BYTE_BGR);
    }

    private BufferedImage convertTo(BufferedImage image, int type){
        BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), type);
        newImage.createGraphics().drawImage(image, 0, 0, null);
        return newImage;
    }

    /*public void addBlend(double weight){
        totalBlend.addAndGet(weight);
    }*/

    public int getTotalSize(){
        return totalSize.get();
    }

}
