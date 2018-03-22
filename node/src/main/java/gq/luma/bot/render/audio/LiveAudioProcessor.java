package gq.luma.bot.render.audio;

import gq.luma.bot.utils.FileReference;
import io.humble.video.*;
import jnr.ffi.Pointer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class LiveAudioProcessor implements AudioProcessor {

    private final int sampleRate = 44100;
    private final int bytesPerSample = 2;
    private final int channels = 2;
    private final AudioChannel.Layout layout = AudioChannel.Layout.CH_LAYOUT_STEREO;
    private final AudioFormat.Type format = AudioFormat.Type.SAMPLE_FMT_S16;

    private int totalSampleCount;

    private CompletableFuture<Void> finished;
    private boolean headered = false;

    private Encoder audioEncoder;
    private MediaAudio rawAudio;

    private RandomAccessFile randomAccessFile;

    public LiveAudioProcessor(Encoder audioEncoder){
        this.audioEncoder = audioEncoder;
        this.rawAudio = MediaAudio.make(788 * 2, sampleRate, channels, layout, format);
        this.rawAudio.setTimeBase(Rational.make(1, sampleRate));
        this.finished = new CompletableFuture<>();

        try {
            File file = new File(FileReference.tempDir, "export.wav");
            if(file.exists())
                file.delete();
            file.createNewFile();
            this.randomAccessFile = new RandomAccessFile(file, "rw");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void packet(byte[] data, int offset, int writeLength, Consumer<MediaAudio> audioConsumer){
        if(!headered){
            try {
                this.randomAccessFile.seek(offset);
                this.randomAccessFile.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            headered = true;
        }
        else if(offset != 0){
            try {
                this.randomAccessFile.seek(offset);
                this.randomAccessFile.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(offset == 40){
                finished.complete(null);
            }
        }
        else{
            try {
                this.randomAccessFile.seek(this.randomAccessFile.length());
                this.randomAccessFile.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            int sampleCount = writeLength / (bytesPerSample * channels);

            System.out.println("It thinks the bytes per sample are: " + rawAudio.getBytesPerSample());
            System.out.println("Expected Size: " + rawAudio.getData(0).getBufferSize() + " NumSamples: " + rawAudio.getNumSamples() + " BytesPerSample: " + rawAudio.getBytesPerSample());
            System.out.println("Delivered Size: " + writeLength);
            System.out.println("Number of Planes: " + rawAudio.getNumDataPlanes());

            System.out.println("NumSamples: " + sampleCount);

            rawAudio.getData(0).put(data, 0, 0, writeLength);
            rawAudio.setNumSamples(sampleCount);
            rawAudio.setTimeStamp(totalSampleCount);

            System.out.println("Encoding packet for audio data: " + totalSampleCount + "-" + (totalSampleCount + sampleCount) + " " + (totalSampleCount/sampleRate) + " sec.");
            totalSampleCount += sampleCount;

            rawAudio.setComplete(true);

            System.out.println("Previous encoded count: " + audioEncoder.getFrameCount());

            audioConsumer.accept(rawAudio);

            System.out.println("After encoded count: " + audioEncoder.getFrameCount());
        }
    }

    @Override
    public void packet(Pointer buf, long offset, long writeLength, Consumer<MediaAudio> audioConsumer) {

    }

    @Override
    public void waitForFinish() throws InterruptedException, ExecutionException {
        try {
            this.randomAccessFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finished.get();
    }
}
