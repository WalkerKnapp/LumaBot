package gq.luma.bot.render.audio;

import io.humble.ferry.Buffer;
import io.humble.video.*;
import jnr.ffi.Pointer;

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
    private byte[] backingBuffer;
    private long latestCalc;

    private boolean headered;
    private CompletableFuture<Void> finished;

    public BufferedAudioProcessor(){
        this.backingBuffer = new byte[bufferSize];
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
                data.get(inputIndex, backingBuffer, bufferPointer, writeLength - inputIndex);
                //rawAudio.getData(0).put(data, inputIndex, bufferPointer, writeLength - inputIndex);
                bufferPointer += writeLength - inputIndex;
                break;
            }
            else{
                data.get(inputIndex, backingBuffer, inputIndex, bufferSize - bufferPointer);
                //rawAudio.getData(0).put(data, inputIndex, bufferPointer, bufferSize - bufferPointer);
                inputIndex += bufferSize - bufferPointer;
                bufferPointer += bufferSize - bufferPointer;
                flush(audioConsumer);
                bufferPointer = 0;
            }
        }
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
