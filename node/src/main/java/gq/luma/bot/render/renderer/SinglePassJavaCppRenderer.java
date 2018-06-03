package gq.luma.bot.render.renderer;

import gq.luma.bot.render.fs.frame.Frame;
import io.humble.video.MediaAudio;
import io.humble.video.MediaPicture;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swscale.*;

import java.io.IOException;

public class SinglePassJavaCppRenderer implements FFRenderer {

    public SinglePassJavaCppRenderer(){
        //AVCodec codec =
    }

    @Override
    public boolean checkFrame(int rawIndex) {
        return false;
    }

    @Override
    public void encodeFrame(Frame frame, long index) {

    }

    @Override
    public boolean checkSamples(MediaAudio samples) {
        return false;
    }

    @Override
    public void encodeSamples(MediaAudio samples) {

    }

    @Override
    public void finish() throws IOException, InterruptedException {

    }

    @Override
    public void forcefullyClose() {

    }

    @Override
    public void setIgnoreTime(double ignoreTime) {

    }

    @Override
    public void setFrameOffset(long offset) {

    }

    @Override
    public long getLatestFrame() {
        return 0;
    }

    @Override
    public MediaPicture generateResampledTemplate() {
        return null;
    }

    @Override
    public MediaPicture generateOriginalTemplate() {
        return null;
    }

    @Override
    public void resample(MediaPicture out, MediaPicture in) {

    }
}
