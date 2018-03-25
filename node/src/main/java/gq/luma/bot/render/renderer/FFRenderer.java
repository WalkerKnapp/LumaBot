package gq.luma.bot.render.renderer;

import gq.luma.bot.render.fs.frame.Frame;
import io.humble.video.MediaAudio;
import io.humble.video.MediaPicture;

public interface FFRenderer {

    boolean checkFrame(int rawIndex);

    void encodeFrame(Frame frame, long index);

    boolean checkSamples(MediaAudio samples);

    void encodeSamples(MediaAudio samples);

    void finish();

    void forcefullyClose();

    void setIgnoreTime(double ignoreTime);

    void setFrameOffset(long offset);

    long getLatestFrame();

    MediaPicture generateResampledTemplate();

    MediaPicture generateOriginalTemplate();
}
