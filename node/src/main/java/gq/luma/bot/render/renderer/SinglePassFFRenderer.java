package gq.luma.bot.render.renderer;

import gq.luma.bot.render.fs.frame.Frame;
import io.humble.video.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class SinglePassFFRenderer implements FFRenderer {
    private static final Logger logger = LoggerFactory.getLogger(SinglePassFFRenderer.class);

    private Muxer muxer;

    private Encoder videoEncoder;
    private MediaPictureResampler videoResampler;
    private MediaPacket videoPacket;

    private Encoder audioEncoder;
    private MediaAudioResampler audioResampler;
    private MediaPacket audioPacket;

    private long latestFrame;
    private long frameOffset;
    private long ignoreFrames;

    private long latestSample;
    private long sampleOffset;
    private long ignoreSamples;

    public SinglePassFFRenderer(Muxer muxer, Encoder videoEncoder, Encoder audioEncoder){
        int pixelCount = videoEncoder.getWidth() * videoEncoder.getHeight() * 3;
        int frameSize = pixelCount + 18;

        this.muxer = muxer;

        this.videoEncoder = videoEncoder;
        this.videoPacket = MediaPacket.make(frameSize);
        this.videoResampler = MediaPictureResampler.make(
                videoEncoder.getWidth(), videoEncoder.getHeight(), videoEncoder.getPixelFormat(),
                videoEncoder.getWidth(), videoEncoder.getHeight(), PixelFormat.Type.PIX_FMT_BGR24, 0);
        this.videoResampler.open();

        this.audioEncoder = audioEncoder;
        this.audioPacket = MediaPacket.make();
        this.audioResampler = MediaAudioResampler.make(audioEncoder.getChannelLayout(), audioEncoder.getSampleRate(), audioEncoder.getSampleFormat(), AudioChannel.Layout.CH_LAYOUT_STEREO, 44100, AudioFormat.Type.SAMPLE_FMT_S16);
        this.audioResampler.open();
        this.sampleOffset = (long) (audioEncoder.getSampleRate() * 0.1);


        //this.screenshotQuerier = screenshotQuerier;
    }

    @Override
    public void encodeFrame(Frame frame, long index){
        this.latestFrame = (index + frameOffset);

        if(this.latestFrame - ignoreFrames >= 0) {

            logger.trace("Writing video with index: {}", this.latestFrame);

            //screenshotQuerier.accept(frame.getUnprocessed(this.latestFrame - ignoreFrames));

            MediaPicture finalPacket = frame.writeMedia(videoResampler, this.latestFrame - ignoreFrames);

            do {
                videoEncoder.encode(videoPacket, finalPacket);
                if (videoPacket.isComplete()) {
                    muxer.write(videoPacket, false);
                }
            } while (videoPacket.isComplete());

        } else {
            logger.debug("Ignoring frame {} to remove broken frames.", this.latestFrame);
        }
    }

    @Override
    public void encodeSamples(MediaAudio samples){
        this.latestSample = samples.getTimeStamp() + this.sampleOffset;

        if(this.latestSample - this.ignoreSamples > 0) {

            samples.setTimeStamp(this.latestSample - this.ignoreSamples);

            MediaAudio usedAudio = samples;

            if (samples.getSampleRate() != audioEncoder.getSampleRate() ||
                    samples.getFormat() != audioEncoder.getSampleFormat() ||
                    samples.getChannelLayout() != audioEncoder.getChannelLayout()) {

                usedAudio = MediaAudio.make(
                        samples.getNumSamples(),
                        audioEncoder.getSampleRate(),
                        audioEncoder.getChannels(),
                        audioEncoder.getChannelLayout(),
                        audioEncoder.getSampleFormat());
                int originalCount = samples.getNumSamples();
                int sampleCount = audioResampler.resample(usedAudio, samples);

                logger.trace("Needed resample. Original count: {} Sample Count: {}", originalCount, sampleCount);
            }

            do {
                audioEncoder.encodeAudio(audioPacket, usedAudio);
                if (audioPacket.isComplete()) {
                    muxer.write(audioPacket, false);
                }
            } while (audioPacket.isComplete());

        }
    }

    @Override
    public void finish(){
        logger.debug("Waiting for video stream to finish...");
        do
        {
            videoEncoder.encode(videoPacket, null);
            if (videoPacket.isComplete())
            {
                muxer.write(videoPacket, false);
            }
        } while (videoPacket.isComplete());

        logger.debug("Waiting for audio stream to finish...");
        do
        {
            audioEncoder.encode(audioPacket, null);
            if (audioPacket.isComplete())
            {
                muxer.write(audioPacket, false);
            }
        } while (audioPacket.isComplete());
        logger.info("All streams finished!");
    }

    @Override
    public void forcefullyClose() {
        if(muxer.getState() == Muxer.State.STATE_OPENED) muxer.close();
    }

    @Override
    public void setIgnoreTime(double ignoreTime){
        this.ignoreFrames = (long) (videoEncoder.getTimeBase().getDenominator() * ignoreTime);
        this.ignoreSamples = (long) (audioEncoder.getSampleRate() * ignoreTime);
    }

    @Override
    public long getLatestFrame(){
        return this.latestFrame;
    }

    @Override
    public MediaPicture generateResampledTemplate() {
        return MediaPicture.make(videoEncoder.getWidth(), videoEncoder.getHeight(), videoEncoder.getPixelFormat());
    }

    @Override
    public MediaPicture generateOriginalTemplate() {
        return MediaPicture.make(videoEncoder.getWidth(), videoEncoder.getHeight(), PixelFormat.Type.PIX_FMT_BGR24);
    }

    @Override
    public void setFrameOffset(long offset){
        this.frameOffset = offset;
    }

    public long getLatestSample(){
        return this.latestSample;
    }

    public void setSampleOffset(long offset){
        this.sampleOffset = offset;
    }
}
