package gq.luma.bot.render.fs.frame;

import io.humble.ferry.Buffer;
import io.humble.ferry.JNIReference;
import io.humble.video.MediaPicture;
import io.humble.video.MediaPictureResampler;
import io.humble.video.PixelFormat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicReference;

public class ReferenceFrame {
    private byte[] estimatedHeader;
    private boolean blend;

    private volatile byte[] totalData;
    private volatile int[] totalDataBlended;
    private volatile double totalBlend = 0d;
    private volatile int totalSize = 0;

    private volatile ByteBuffer totalBuffer;

    public ReferenceFrame(int pixelCount, boolean blend){
        this.blend = blend;
        this.estimatedHeader = new byte[18];
        if(blend) this.totalDataBlended = new int[pixelCount];
        else this.totalData = new byte[pixelCount];

        totalBuffer = ByteBuffer.allocate(pixelCount);
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
            //if(estimatedHeader == null) {
            //    //estimatedHeader = Arrays.copyOfRange(data, 0, 18);
            //}
            System.arraycopy(data, 0, estimatedHeader, 0, 18);
            frameOffset = 18;
        }

        totalSize += data.length;
        int maxValue = data.length;

        if(estimatedHeader[2] == 0x02 && estimatedHeader[16] == 0x20) {
            int i = 0;
            while (frameOffset < maxValue) {
                if(i % 4 == 3)
                    frameOffset++;
                else
                    totalData[(offset + frameOffset) - 18] = data[frameOffset++];
                i++;
            }
        }
        else if(estimatedHeader[2] == 0x02 && estimatedHeader[16] == 0x18){
            //System.arraycopy(data, frameOffset, totalData, (offset + frameOffset) - 18, maxValue - frameOffset);
            //while(frameOffset < maxValue) {
            //    totalData[(offset + frameOffset) - 18] = data[frameOffset++];
            //}
            totalBuffer.put(data, frameOffset, maxValue - frameOffset);
        }
        else {
            while (frameOffset < maxValue){
                int ident = data[frameOffset++];
                if((ident & 0x80) == 0){
                    for(int j = 0; j <= ident; j++){
                        totalData[(offset + frameOffset) - 18] = data[frameOffset++];
                        totalData[(offset + frameOffset) - 18] = data[frameOffset++];
                        totalData[(offset + frameOffset) - 18] = data[frameOffset++];
                    }
                }
                else{
                    ident &= 0x7f;
                    final byte b = data[frameOffset++];
                    final byte g = data[frameOffset++];
                    final byte r = data[frameOffset++];
                    for(int j = 0; j <= ident; j++){
                        totalData[(offset + frameOffset) - 18] = b;
                        totalData[(offset + frameOffset) - 18] = g;
                        totalData[(offset + frameOffset) - 18] = r;
                    }
                }
            }
        }

        return maxValue;
    }

    /*public byte[] export() throws MelissaException {
        if(18 + totalData.length != this.frameSize)
            throw new MelissaException("Pixel mismatch to header length. (" + (18 + totalData.length) + "!=" + this.frameSize + ")\nPlease contact the developer and describe what you were trying to do. ");

        if(estimatedHeader == null)
            throw new MelissaException("Frame header is found null. This is an error that should be reported to the developer.");

        byte[] ret = new byte[this.frameSize];
        System.arraycopy(estimatedHeader, 0, ret, 0, 18);
        if(totalBlend == 0) {
            for (int i = 0; i < totalData.length; i++) {
                ret[i + 18] = (byte)totalData[i];
            }
        }
        else{
            for (int i = 0; i < totalData.length; i++) {
                ret[i + 18] = (byte) (int) (totalData[i] / totalBlend);
            }
        }
        return ret;
    }*/

    /*public BufferedImage getImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] write = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int i = 0;

        if(!blend) {
            int maxSize = totalData.length;
            while (i < maxSize) {
                write[i / 3] |= totalData[i++] & 0xff;
                write[i / 3] |= (totalData[i++] & 0xff) << 8;
                write[i / 3] |= (totalData[i++] & 0xff) << 16;
            }
        }
        else {
            int maxSize = totalDataBlended.length;
            while (i < maxSize) {
                write[i / 3] |= totalDataBlended[i++] & 0xff;
                write[i / 3] |= (totalDataBlended[i++] & 0xff) << 8;
                write[i / 3] |= (totalDataBlended[i++] & 0xff) << 16;
            }
        }
        return convertTo(image, BufferedImage.TYPE_3BYTE_BGR);
    }*/

    public BufferedImage getImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] write = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
        int i = 0;
        int j = 0;

        if(!blend) {
            int maxSize = totalData.length;
            while (i < maxSize) {
                byte r = totalData[i++];
                byte g = totalData[i++];
                byte b = totalData[i++];
                write[j++] = r;
                write[j++] = g;
                write[j++] = b;
            }
        }
        else {
            int maxSize = totalDataBlended.length;
            while (i < maxSize) {
                byte r = (byte)totalDataBlended[i++];
                byte g = (byte)totalDataBlended[i++];
                byte b = (byte)totalDataBlended[i++];
                write[j++] = r;
                write[j++] = g;
                write[j++] = b;
            }
        }
        return image;
    }

    private BufferedImage convertTo(BufferedImage image, int type){
        BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), type);
        newImage.createGraphics().drawImage(image, 0, 0, null);
        return newImage;
    }

    public MediaPicture writeMedia(MediaPicture output, ByteBuffer pictureByteBuffer, MediaPictureResampler resampler, MediaPicture resampledFrame, long timestamp) {

        /*byte[] imageBytes = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();

        System.out.println("Comparing ImagifiedArray (len: " + imageBytes.length + ") to Buffer (len: " + totalBuffer.capacity() + ")");*/

        if(!blend) {
            //System.out.println("Inserting data (length: " + totalData.length + ") into buffer (size: " + pictureByteBuffer.capacity() + ")");
            //System.arraycopy(totalData, 0, pictureByteBuffer, 0, totalData.length);
            pictureByteBuffer.put(totalData);
        }
        else {
            int maxSize = totalDataBlended.length;
            int i = 0;
            while (i < maxSize) {
                pictureByteBuffer.put(i, (byte)(totalDataBlended[i++] / totalBlend));
                //pictureByteBuffer[i] = (byte)(totalDataBlended[i++] / totalBlend);
            }
        }

        resampledFrame.setTimeStamp(timestamp);
        resampledFrame.setComplete(true);

        resampler.resample(output, resampledFrame);

        pictureByteBuffer.rewind();

        return output;
    }

    public MediaPicture writeMediaReal(MediaPicture picture, long timestamp) {

        final AtomicReference<JNIReference> ref = new AtomicReference<>(null);

        Buffer buffer = picture.getData(0);
        int size = picture.getDataPlaneSize(0);
        ByteBuffer pictureByteBuffer = buffer.getByteBuffer(0, size, ref);
        buffer.delete();
        buffer = null;

        if(!blend) {
            System.out.println("Inserting data (length: " + totalBuffer.capacity() + ") into buffer (size: " + pictureByteBuffer.capacity() + ")");
            pictureByteBuffer.put(totalBuffer);
        }
        else {
            int maxSize = totalDataBlended.length;
            int i = 0;
            while (i < maxSize) {
                pictureByteBuffer.put(i, (byte)(totalDataBlended[i++] / totalBlend));
            }
        }

        pictureByteBuffer = null;
        picture.setTimeStamp(timestamp);
        picture.setComplete(true);

        if(ref.get() != null)
            ref.get().delete();

        return picture;
    }

    public MediaPicture exactCopy(MediaPicture output, BufferedImage input, long timestamp){

        MediaPictureResampler mToImageResampler = MediaPictureResampler.make(
                output.getWidth(), output.getHeight(), output.getFormat(),
                output.getWidth(), output.getHeight(), PixelFormat.Type.PIX_FMT_BGR24, 0);
        MediaPicture mResampleMediaPicture = MediaPicture.make(output.getWidth(), output.getHeight(), PixelFormat.Type.PIX_FMT_BGR24);
        mToImageResampler.open();

        //validateImage(input);

        // get the image byte buffer buffer

        DataBuffer imageBuffer = input.getRaster().getDataBuffer();
        byte[] imageBytes = null;
        int[] imageInts = null;

        // handle byte buffer case

        if (imageBuffer instanceof DataBufferByte) {
            imageBytes = ((DataBufferByte) imageBuffer).getData();
        }

        // handle integer buffer case

        else if (imageBuffer instanceof DataBufferInt) {
            imageInts = ((DataBufferInt) imageBuffer).getData();
        }

        // if it's some other type, throw

        else {
            throw new IllegalArgumentException(
                    "Unsupported BufferedImage data buffer type: "
                            + imageBuffer.getDataType());
        }

        // create the video picture and get it's underlying buffer

        final AtomicReference<JNIReference> ref = new AtomicReference<JNIReference>(
                null);
        final MediaPicture picture = mResampleMediaPicture;
        try {
            Buffer buffer = picture.getData(0);
            int size = picture.getDataPlaneSize(0);
            ByteBuffer pictureByteBuffer = buffer.getByteBuffer(0,
                    size, ref);
            buffer.delete();
            buffer = null;

            if (imageInts != null) {
                pictureByteBuffer.order(ByteOrder.BIG_ENDIAN);
                IntBuffer pictureIntBuffer = pictureByteBuffer.asIntBuffer();
                pictureIntBuffer.put(imageInts);
            } else {
                pictureByteBuffer.put(imageBytes);
            }
            pictureByteBuffer = null;
            picture.setTimeStamp(timestamp);
            picture.setComplete(true);

            mToImageResampler.resample(output, picture);

            return output;
        } finally {
            if (ref.get() != null)
                ref.get().delete();
        }
    }

    public void addBlend(double weight){
        totalBlend += weight;
    }

    public int getTotalSize(){
        return totalSize;
    }

}
