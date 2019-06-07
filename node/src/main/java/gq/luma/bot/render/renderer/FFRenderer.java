package gq.luma.bot.render.renderer;

import gq.luma.bot.LumaException;
import gq.luma.bot.render.fs.frame.Frame;
import io.humble.video.MediaAudio;
import io.humble.video.MediaPicture;
import jnr.ffi.Pointer;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface FFRenderer {

    boolean checkFrame(int rawIndex);

    void handleVideoData(int index, Pointer buf, long offset, long writeLength) throws LumaException;

    void handleAudioData(Pointer buf, long offset, long size);

    void encodeFrame(Frame frame, long index) throws LumaException;

    boolean checkSamples(MediaAudio samples);

    void encodeSamples(Long index);

    void finish() throws IOException, InterruptedException, LumaException;

    void forcefullyClose() throws LumaException;

    void setIgnoreTime(double ignoreTime);

    void setFrameOffset(long offset);

    long getLatestFrame();

    void setSampleOffset(long offset);

    long getLatestSample();
}
