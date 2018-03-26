package gq.luma.bot.render.renderer;

import gq.luma.bot.render.fs.frame.Frame;
import io.humble.video.MediaAudio;
import io.humble.video.MediaPicture;
import io.humble.video.Muxer;

public class TwoPassFFRenderer implements FFRenderer {

    public TwoPassFFRenderer(){
        Muxer firstPassCollecter;
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
        return null;
    }

    @Override
    public MediaPicture generateOriginalTemplate() {
        return null;
    }
}
