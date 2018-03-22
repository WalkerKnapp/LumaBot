package gq.luma.bot.render.audio;

import gq.luma.bot.utils.FileReference;
import io.humble.video.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Consumer;

public class RecordedAudioProcessor {

    //private byte[] totalData;
    //private Encoder audioEncoder;
    //private MediaAudioResampler resampler;

    private RandomAccessFile randomAccessFile;

    public RecordedAudioProcessor(Encoder audioEncoder){
        //this.totalData = new byte[0];
        //this.audioEncoder = audioEncoder;
        //this.resampler = MediaAudioResampler.make(audioEncoder.getChannelLayout(), audioEncoder.getSampleRate(), audioEncoder.getSampleFormat(), AudioChannel.Layout.CH_LAYOUT_STEREO, 44100, AudioFormat.Type.SAMPLE_FMT_S16);
        //this.resampler.open();
        try {
            File file = new File(FileReference.tempDir, "meme.wav");
            if(file.exists())
                file.delete();
            file.createNewFile();
            this.randomAccessFile = new RandomAccessFile(file, "rw");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void packet(byte[] data, int offset, int writeLength, Consumer<MediaAudio> audioConsumer) {
        try {
            if (offset == 0) {
                this.randomAccessFile.seek(this.randomAccessFile.length());
                this.randomAccessFile.write(data, 0, data.length);
            } else {
                this.randomAccessFile.seek(offset);
                this.randomAccessFile.write(data, 0, data.length);
                if(offset == 40)
                    this.randomAccessFile.close();
            }
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }

    public void waitForFinish() throws InterruptedException {

    }
}
