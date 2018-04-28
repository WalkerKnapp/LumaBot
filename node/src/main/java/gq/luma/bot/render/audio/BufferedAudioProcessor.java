package gq.luma.bot.render.audio;

import io.humble.ferry.Buffer;
import io.humble.video.*;
import jnr.ffi.Pointer;
import org.apache.commons.codec.binary.Hex;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class BufferedAudioProcessor implements AudioProcessor {

    private static final int bytesPerSample = 2;
    private static final long sampleRate = 44100;
    private static final int samplesPerFrame = 1024;
    private static final int channels = 2;
    private static final int bufferSize = bytesPerSample * samplesPerFrame * channels;

    private int bufferPointer;
    private MediaAudio rawAudio;
    private long latestCalc;

    private boolean headered;
    private CompletableFuture<Void> finished;

    public BufferedAudioProcessor(){
        byte[] backingBuffer = new byte[bufferSize];
        this.finished = new CompletableFuture<>();
        Buffer directBuffer = Buffer.make(null, backingBuffer, 0, bufferSize);
        rawAudio = MediaAudio.make(
                directBuffer,
                samplesPerFrame,
                (int) sampleRate,
                channels,
                AudioChannel.Layout.CH_LAYOUT_STEREO,
                AudioFormat.Type.SAMPLE_FMT_S16);
        rawAudio.setTimeBase(Rational.make(1, (int) sampleRate));
        System.out.println("Theroretical buffer size: " + bufferSize + " and real buffer size: " + rawAudio.getDataPlaneSize(0));
    }

    @Override
    public void packet(byte[] data, int offset, int writeLength, Consumer<MediaAudio> audioConsumer) {
        if(offset == 0 && headered){
            addBufferAndFlush(data, writeLength, audioConsumer);
        }
        else{
            this.headered = true;
            if(offset == 40) {
                if(bufferPointer != 0){

                } //flush(audioConsumer);
                finished.complete(null);
            }
            bufferPointer = 0;
        }
    }

    @Override
    public void packet(Pointer buf, long offset, long writeLength, Consumer<MediaAudio> audioConsumer) {
        //System.out.println("Got audio write of length: " + writeLength + " with offset: " + offset);
        if(offset == 0 && headered){
            addBufferAndFlush(buf, (int)writeLength, audioConsumer);
        }
        else{
            this.headered = true;
            if(offset == 40) {
                if(bufferPointer != 0){

                } //flush(audioConsumer);
                finished.complete(null);
            }
            bufferPointer = 0;
        }
    }

    @Override
    public void waitForFinish() throws InterruptedException, ExecutionException {
        finished.get();
    }

    private void addBufferAndFlush(byte[] data, int writeLength, Consumer<MediaAudio> audioConsumer){
        int inputIndex = 0;
        while (inputIndex < writeLength){
            if((writeLength - inputIndex) < (bufferSize - bufferPointer)){
                rawAudio.getData(0).put(data, inputIndex, bufferPointer, writeLength - inputIndex);
                bufferPointer += writeLength - inputIndex;
                break;
            }
            else{
                rawAudio.getData(0).put(data, inputIndex, bufferPointer, bufferSize - bufferPointer);
                inputIndex += bufferSize - bufferPointer;
                bufferPointer += bufferSize - bufferPointer;
                flush(audioConsumer);
                bufferPointer = 0;
            }
        }
    }

    private void addBufferAndFlush(Pointer data, int writeLength, Consumer<MediaAudio> audioConsumer){
        int inputIndex = 0;
        while (inputIndex < writeLength){
            if((writeLength - inputIndex) < (bufferSize - bufferPointer)){
                write(data, rawAudio.getData(0), inputIndex, bufferPointer, writeLength - inputIndex);
                bufferPointer += writeLength - inputIndex;
                break;
            }
            else{
                write(data, rawAudio.getData(0), inputIndex, bufferPointer, bufferSize - bufferPointer);
                //System.out.println("Finishing up a buffer. The pointer is: " + bufferPointer + " and we are writing: " + (bufferSize - bufferPointer));
                //System.out.println("Read:         " + new String(Hex.encodeHex(buf)));
                //System.out.println("Final buffer: " + new String(Hex.encodeHex(rawAudio.getData(0).getByteArray(0, rawAudio.getDataPlaneSize(0)))));
                inputIndex += bufferSize - bufferPointer;
                bufferPointer += bufferSize - bufferPointer;
                flush(audioConsumer);
                bufferPointer = 0;
            }
        }
    }

    private void write(Pointer from, Buffer to, int inOffset, int outIndex, int length){
        byte[] buf = new byte[length];
        from.get(inOffset, buf, 0, length);
        to.put(buf, 0, outIndex, length);
        //return buf;
    }

    private void flush(Consumer<MediaAudio> audioConsumer){

        int sampleCount = bufferPointer / (bytesPerSample * channels);

        //System.out.println("Data Recieved: " + Hex.encodeHexString(buffer));
        //System.out.println("Sample Count: " + sampleCount);
        //System.out.println("Buffer Pointer: " + bufferPointer);
        //System.out.println("Bytes per sample: " + bytesPerSample);

        rawAudio.setNumSamples(sampleCount);
        rawAudio.setTimeStamp(latestCalc);
        latestCalc += sampleCount;

        //System.out.println("Encoding audio at " + (latestCalc/sampleRate) + " seconds.");

        rawAudio.setComplete(true);

        audioConsumer.accept(rawAudio);
    }
}
