package gq.luma.bot.render.renderer;

import gq.luma.bot.render.fs.frame.Frame;
import io.humble.video.MediaAudio;
import io.humble.video.MediaPicture;
import io.humble.video.PixelFormat;

public class NullFFRenderer implements FFRenderer {

    private int width;
    private int height;

    public NullFFRenderer(int width, int height){
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean checkFrame(int rawIndex) {
        return true;
    }

    @Override
    public void encodeFrame(Frame frame, long index) {

    }

    @Override
    public boolean checkSamples(MediaAudio samples) {
        return true;
    }

    @Override
    public void encodeSamples(MediaAudio samples) {

    }

    @Override
    public void finish() {

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
        return MediaPicture.make(width, height, PixelFormat.Type.PIX_FMT_YUV420P);
    }

    @Override
    public MediaPicture generateOriginalTemplate() {
        return MediaPicture.make(width, height, PixelFormat.Type.PIX_FMT_BGR24);
    }
}
