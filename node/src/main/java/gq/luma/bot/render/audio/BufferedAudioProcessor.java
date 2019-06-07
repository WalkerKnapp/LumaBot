package gq.luma.bot.render.audio;

import io.humble.ferry.Buffer;
import io.humble.video.*;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import org.apache.commons.codec.binary.Hex;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BufferedAudioProcessor implements AudioProcessor {

    private static final int bytesPerSample = 2;
    private final long sampleRate = 44100;
    private final int samplesPerFrame = 1024;
    private static final int channels = 2;
    private int bufferSize = bytesPerSample * samplesPerFrame * channels;
    //private int bufferSize;

    private int bufferPointer;
    private ByteBuffer audioBuffer;
    private Pointer wrappedBuffer;
    private long address;
    private long latestCalc;

    private boolean headered;
    private CompletableFuture<Void> finished;

    public BufferedAudioProcessor(ByteBuffer audioBuffer){
        this.finished = new CompletableFuture<>();
        //this.bufferSize = audioBuffer.capacity();
        this.audioBuffer = audioBuffer;
        this.wrappedBuffer = Pointer.wrap(Runtime.getSystemRuntime(), audioBuffer);
        this.address = wrappedBuffer.address();
        System.out.println("Theroretical buffer size: " + bufferSize + " and real buffer size: " + audioBuffer.capacity());
    }

    public BufferedAudioProcessor(ByteBuffer audioBuffer, int capacity){
        this.finished = new CompletableFuture<>();
        //this.bufferSize = capacity;
        this.audioBuffer = audioBuffer;
        this.wrappedBuffer = Pointer.wrap(Runtime.getSystemRuntime(), audioBuffer);
        System.out.println("Theroretical buffer size: " + bufferSize + " and real buffer size: " + capacity);
    }

    @Override
    public void packet(byte[] data, int offset, int writeLength, Consumer<Long> audioConsumer) {
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
    public void packet(Pointer buf, long offset, long writeLength, Consumer<Long> audioConsumer) {
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

    private void addBufferAndFlush(byte[] data, int writeLength, Consumer<Long> audioConsumer){
        int inputIndex = 0;
        while (inputIndex < writeLength){
            if((writeLength - inputIndex) < (bufferSize - bufferPointer)){
                audioBuffer.position(bufferPointer);
                audioBuffer.put(data, inputIndex, writeLength - inputIndex);
                bufferPointer += writeLength - inputIndex;
                break;
            }
            else{
                audioBuffer.position(bufferPointer);
                audioBuffer.put(data, inputIndex, bufferSize - bufferPointer);
                inputIndex += bufferSize - bufferPointer;
                bufferPointer += bufferSize - bufferPointer;
                flush(audioConsumer);
                bufferPointer = 0;
            }
        }
    }

    private void addBufferAndFlush(Pointer data, int writeLength, Consumer<Long> audioConsumer){
        int inputIndex = 0;
        while (inputIndex < writeLength){
            if((writeLength - inputIndex) < (bufferSize - bufferPointer)){
                data.transferTo(inputIndex, wrappedBuffer, bufferPointer, writeLength - inputIndex);
                bufferPointer += writeLength - inputIndex;
                break;
            }
            else{
                data.transferTo(inputIndex, wrappedBuffer, bufferPointer, bufferSize - bufferPointer);
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

    private void flush(Consumer<Long> audioConsumer){

        int sampleCount = bufferPointer / (bytesPerSample * channels);

        //System.out.println("Data Recieved: " + Hex.encodeHexString(buffer));
        //System.out.println("Sample Count: " + sampleCount);
        //System.out.println("Buffer Pointer: " + bufferPointer);
        //System.out.println("Bytes per sample: " + bytesPerSample);

        //rawAudio.setNumSamples(sampleCount);
        //rawAudio.setTimeStamp(latestCalc);
        latestCalc += sampleCount;

        //System.out.println("Encoding audio at " + (latestCalc/sampleRate) + " seconds.");

        //rawAudio.setComplete(true);

        audioConsumer.accept(latestCalc);
    }
}
