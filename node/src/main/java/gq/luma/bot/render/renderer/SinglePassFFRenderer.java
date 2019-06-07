package gq.luma.bot.render.renderer;

import gq.luma.bot.RenderSettings;
import gq.luma.bot.RenderWeighterType;
import gq.luma.bot.render.audio.AudioProcessor;
import gq.luma.bot.render.audio.BufferedAudioProcessor;
import gq.luma.bot.render.fs.frame.AparapiAccumulatorFrame;
import gq.luma.bot.render.fs.frame.Frame;
import gq.luma.bot.render.fs.frame.UnsafeFrame;
import gq.luma.bot.render.fs.frame.WeightedFrame;
import gq.luma.bot.render.fs.weighters.DemoWeighter;
import gq.luma.bot.render.fs.weighters.LinearDemoWeighter;
import gq.luma.bot.render.fs.weighters.QueuedGaussianDemoWeighter;
import io.humble.ferry.Buffer;
import io.humble.video.*;
import jnr.ffi.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SinglePassFFRenderer implements FFRenderer {
    private static final Logger logger = LoggerFactory.getLogger(SinglePassFFRenderer.class);

    public static final boolean FORCE_INTERLEAVE = true;

    private static final int bytesPerSample = 2;
    private static final long sampleRate = 44100;
    private static final int samplesPerFrame = 1024;
    private static final int channels = 2;
    private static final int bufferSize = bytesPerSample * samplesPerFrame * channels;

    private Muxer muxer;

    private Encoder videoEncoder;
    private MediaPictureResampler videoResampler;
    private MediaPacket videoPacket;

    private Encoder audioEncoder;
    private MediaAudioResampler audioResampler;
    private MediaPacket audioPacket;
    private MediaAudio audioFrame;
    private AudioProcessor audioProcessor;

    private Frame frame;
    private DemoWeighter weighter;
    private RenderSettings settings;
    private MediaPicture originalFrame;
    private MediaPicture resampledFrame;
    private long frameSize;
    private int topFrame;

    private long latestFrame;
    private long frameOffset;
    private long ignoreFrames;

    private long latestSample;
    private long sampleOffset;
    private long ignoreSamples;

    public SinglePassFFRenderer(Muxer muxer, Encoder videoEncoder, Encoder audioEncoder, RenderSettings settings){
        int pixelCount = videoEncoder.getWidth() * videoEncoder.getHeight() * 3;
        this.frameSize = pixelCount + 18;
        this.topFrame = settings.getFrameblendIndex() - 1;

        this.muxer = muxer;

        this.videoEncoder = videoEncoder;
        this.videoPacket = MediaPacket.make((int) frameSize);
        this.videoResampler = MediaPictureResampler.make(
                videoEncoder.getWidth(), videoEncoder.getHeight(), videoEncoder.getPixelFormat(),
                videoEncoder.getWidth(), videoEncoder.getHeight(), PixelFormat.Type.PIX_FMT_BGR24, 0);
        this.videoResampler.open();

        this.audioEncoder = audioEncoder;
        this.audioPacket = MediaPacket.make();
        this.audioResampler = MediaAudioResampler.make(audioEncoder.getChannelLayout(), audioEncoder.getSampleRate(), audioEncoder.getSampleFormat(), AudioChannel.Layout.CH_LAYOUT_STEREO, 44100, AudioFormat.Type.SAMPLE_FMT_S16);
        this.audioResampler.open();
        this.sampleOffset = (long) (audioEncoder.getSampleRate() * 0.1);
        this.audioFrame = MediaAudio.make(
                samplesPerFrame,
                (int) sampleRate,
                channels,
                AudioChannel.Layout.CH_LAYOUT_STEREO,
                AudioFormat.Type.SAMPLE_FMT_S16);
        audioFrame.setTimeBase(Rational.make(1, (int) sampleRate));

        Buffer audioBuffer = audioFrame.getData(0);
        this.audioProcessor = new BufferedAudioProcessor(audioBuffer.getByteBuffer(0, audioFrame.getDataPlaneSize(0)));
        audioBuffer.delete();

        this.resampledFrame = generateResampledTemplate();
        this.originalFrame = generateOriginalTemplate();

        this.settings = settings;


        Buffer buffer = originalFrame.getData(0);
        int size = originalFrame.getDataPlaneSize(0);
        ByteBuffer originalBuf = buffer.getByteBuffer(0, size);
        buffer.delete();

        if (settings.getFrameblendIndex() == 1) {
            this.frame = new UnsafeFrame(originalBuf);
        } else {
            //System.out.println("Pixel count: " + pixelCount);
            //this.frame = new WeightedFrame(originalBuf, pixelCount);
            this.frame = new AparapiAccumulatorFrame(originalBuf, pixelCount);
            if (settings.getWeighterType() == RenderWeighterType.LINEAR) {
                //System.out.println("Setting weighter to linear");
                this.weighter = new LinearDemoWeighter(settings.getFrameblendIndex());
            } else if (settings.getWeighterType() == RenderWeighterType.GAUSSIAN) {
                //System.out.println("Setting weighter to gaussian");
                this.weighter = new QueuedGaussianDemoWeighter(settings.getFrameblendIndex(), 0, 5d);
            }
        }
    }

    @Override
    public boolean checkFrame(int rawIndex) {
        if(ignoreFrames > 0){
            logger.debug("Ignoring raw index {} to remove broken frames.", rawIndex);
            ignoreFrames--;
            return false;
        }
        return true;
    }

    @Override
    public void handleVideoData(int index, Pointer buf, long offset, long writeLength) {
        int position = index % settings.getFrameblendIndex();

        frame.packet(buf, offset, writeLength, weighter, position, index);

        if(offset == this.frameSize - writeLength && position == topFrame){
            System.out.println("Writing media on frame: " + index + " and position " + position);
            encodeFrame(frame, index / settings.getFrameblendIndex());
            frame.reset();
        }
    }

    @Override
    public void handleAudioData(Pointer buf, long offset, long size){
        audioProcessor.packet(buf, offset, size, this::encodeSamples);
    }

    @Override
    public void encodeFrame(Frame frame, long index){
        this.latestFrame = (index + frameOffset);

        logger.trace("Writing video with index: {}", this.latestFrame);

        originalFrame.setTimeStamp(this.latestFrame - ignoreFrames);
        originalFrame.setComplete(true);

        frame.finishData();
        videoResampler.resample(resampledFrame, originalFrame);
        frame.reset();

        do {
            videoEncoder.encode(videoPacket, resampledFrame);
            if (videoPacket.isComplete()) {
                muxer.write(videoPacket, FORCE_INTERLEAVE);
            }
        } while (videoPacket.isComplete());
    }

    @Override
    public boolean checkSamples(MediaAudio samples) {
        if(this.ignoreSamples >= samples.getNumSamples()){
            logger.debug("Ignoring samples at timestamp {} to remove broken frames.", samples.getTimeStamp());
            this.ignoreSamples -= samples.getNumSamples();
            return false;
        }
        return true;
    }

    @Override
    public void encodeSamples(Long index) {
        System.out.println("Encoding samples");
        this.latestSample = index + this.sampleOffset;

        audioFrame.setTimeStamp(this.latestSample);
        audioFrame.setComplete(true);

        MediaAudio usedAudio = audioFrame;

        if (audioFrame.getSampleRate() != audioEncoder.getSampleRate() ||
                audioFrame.getFormat() != audioEncoder.getSampleFormat() ||
                audioFrame.getChannelLayout() != audioEncoder.getChannelLayout()) {

            usedAudio = MediaAudio.make(
                    audioFrame.getNumSamples(),
                    audioEncoder.getSampleRate(),
                    audioEncoder.getChannels(),
                    audioEncoder.getChannelLayout(),
                    audioEncoder.getSampleFormat());
            int originalCount = audioFrame.getNumSamples();
            int sampleCount = audioResampler.resample(usedAudio, audioFrame);

            logger.trace("Audio needed resample. Original count: {} Sample Count: {}", originalCount, sampleCount);
        }

        //logger.debug("Encoding samples: {} of data: {}", usedAudio.getData(0).toString(), new String(Hex.encodeHex(usedAudio.getData(0).getByteArray(0, usedAudio.getDataPlaneSize(0)))));

        do {
            audioEncoder.encodeAudio(audioPacket, usedAudio);
            if (audioPacket.isComplete()) {
                muxer.write(audioPacket, FORCE_INTERLEAVE);
            }
        } while (audioPacket.isComplete());
    }

    @Override
    public void finish() throws IOException, InterruptedException {
        logger.debug("Waiting for video stream to finish...");
        do
        {
            videoEncoder.encode(videoPacket, null);
            if (videoPacket.isComplete())
            {
                muxer.write(videoPacket, FORCE_INTERLEAVE);
            }
        } while (videoPacket.isComplete());

        logger.debug("Waiting for audio stream to finish...");
        do
        {
            audioEncoder.encode(audioPacket, null);
            if (audioPacket.isComplete())
            {
                muxer.write(audioPacket, FORCE_INTERLEAVE);
            }
        } while (audioPacket.isComplete());
        logger.info("All streams finished!");
        muxer.close();
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

    public MediaPicture generateResampledTemplate() {
        return MediaPicture.make(videoEncoder.getWidth(), videoEncoder.getHeight(), videoEncoder.getPixelFormat());
    }

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
