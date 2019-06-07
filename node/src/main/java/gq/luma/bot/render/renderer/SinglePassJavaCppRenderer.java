package gq.luma.bot.render.renderer;

import gq.luma.bot.LumaException;
import gq.luma.bot.RenderSettings;
import gq.luma.bot.RenderWeighterType;
import gq.luma.bot.VideoOutputFormat;
import gq.luma.bot.render.audio.AudioProcessor;
import gq.luma.bot.render.audio.BufferedAudioProcessor;
import gq.luma.bot.render.audio.UnsafeAudioProcessor;
import gq.luma.bot.render.fs.frame.AparapiAccumulatorFrame;
import gq.luma.bot.render.fs.frame.Frame;
import gq.luma.bot.render.fs.frame.UnsafeFrame;
import gq.luma.bot.render.fs.weighters.DemoWeighter;
import gq.luma.bot.render.fs.weighters.LinearDemoWeighter;
import gq.luma.bot.render.fs.weighters.QueuedGaussianDemoWeighter;
import io.humble.video.MediaAudio;
import jnr.ffi.Pointer;
import org.bytedeco.javacpp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swscale.*;
import static org.bytedeco.javacpp.swresample.*;
import static org.bytedeco.javacpp.avformat.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;

public class SinglePassJavaCppRenderer implements FFRenderer {
    private static final Logger logger = LoggerFactory.getLogger(SinglePassJavaCppRenderer.class);

    private AVStream videoStream;
    private AVCodecContext videoContext;
    private SwsContext swsContext;
    private AVFrame preResampleVideoFrame, postResampleVideoFrame;
    private AVPacket videoPacket;
    private boolean shouldEncodeVideo = false;

    private AVStream audioStream;
    private AVCodecContext audioContext;
    private SwrContext swrContext;
    private AVFrame preResampleAudioFrame, postResampleAudioFrame;
    private AVPacket audioPacket;
    private boolean shouldResampleAudio = true;
    private boolean shouldEncodeAudio = false;

    private AVFormatContext formatContext;

    private Frame frame;
    private RenderSettings settings;
    private DemoWeighter weighter;
    private AudioProcessor audioProcessor;

    private long frameSize;
    private int topFrame;

    private long latestFrame;
    private long frameOffset;
    private long ignoreFrames;

    private long latestSample;
    private long sampleOffset;
    private long ignoreSamples;

    private boolean closed = false;

    public SinglePassJavaCppRenderer(File f, RenderSettings settings, double predictedLength) throws LumaException, IOException {
        this.settings = settings;
        AVOutputFormat fmt;
        AVCodec videoCodec, audioCodec;

        //AVCodec oformat = av_codec_next(null);
        //while(oformat != null)
        //{
        //    System.out.println(oformat.id() + " - " + oformat.name().getString() + " - " + oformat.long_name().getString());
        //    oformat = av_codec_next(oformat);
        //}

        fmt = av_guess_format(settings.getFormat().getFormat(), null, null);
        if(fmt == null){
            throw new LumaException("Unable to find a suitable output muxer: " + settings.getFormat().getFormat());
        }
        formatContext = avformat_alloc_context();
        formatContext.oformat(fmt);

        if(fmt.video_codec() != AV_CODEC_ID_NONE){
            shouldEncodeVideo = true;
            videoCodec = avcodec_find_encoder(settings.getFormat().getVideoCodec());
            if(videoCodec == null){
                throw new LumaException("Unable to initialize video codec.");
            }
            videoPacket = av_packet_alloc();
            if(videoPacket == null){
                throw new LumaException("Unable to initialize video packet.");
            }
            videoStream = avformat_new_stream(formatContext, null);
            if(videoStream == null){
                throw new LumaException("Unable to initialize video stream.");
            }
            videoContext = avcodec_alloc_context3(videoCodec);
            if(videoContext == null){
                throw new LumaException("Unable to initialize video context.");
            }
            videoContext.width(settings.getWidth());
            videoContext.height(settings.getHeight());
            videoStream.time_base(av_make_q(1, settings.getFps()));
            videoContext.time_base(videoStream.time_base());
            videoContext.pix_fmt(settings.getFormat().getPixelFormat());
            if(settings.getFormat() == VideoOutputFormat.GIF){
                //Set target bitrate to be under 8 MB
                if(predictedLength > 0) {
                    videoContext.bit_rate((long) ((7.5 * 1000 * 1000) / predictedLength));
                }
            }
            if((fmt.flags() & AVFMT_GLOBALHEADER) == AVFMT_GLOBALHEADER){
                videoContext.flags(videoContext.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
            }

            swsContext = sws_getContext(videoContext.width(),
                    videoContext.height(),
                    AV_PIX_FMT_BGR24,
                    videoContext.width(),
                    videoContext.height(),
                    videoContext.pix_fmt(),
                    SWS_BICUBIC, null, null, (DoublePointer) null);
            if(swsContext == null){
                throw new LumaException("Unable to initialize video resample context.");
            }

            if(avcodec_open2(videoContext, null, (AVDictionary) null) < 0){
                throw new LumaException("Failed to open video encoder.");
            }
            preResampleVideoFrame = allocVideoFrame(AV_PIX_FMT_BGR24, videoContext.width(), videoContext.height());
            postResampleVideoFrame = allocVideoFrame(videoContext.pix_fmt(), videoContext.width(), videoContext.height());

            int ret = avcodec_parameters_from_context(videoStream.codecpar(), videoContext);
            if(ret < 0){
                throw new LumaException("Failed to copy the video stream parameters");
            }

            ByteBuffer originalBuf = preResampleVideoFrame.data(0).asBuffer();
            int pixelCount = videoContext.width() * videoContext.height() * 3;
            this.frameSize = pixelCount + 18;
            this.topFrame = settings.getFrameblendIndex() - 1;

            if (settings.getFrameblendIndex() == 1) {
                this.frame = new UnsafeFrame(originalBuf);
            } else {
                this.frame = new AparapiAccumulatorFrame(originalBuf, pixelCount);
                if (settings.getWeighterType() == RenderWeighterType.LINEAR) {
                    this.weighter = new LinearDemoWeighter(settings.getFrameblendIndex());
                } else if (settings.getWeighterType() == RenderWeighterType.GAUSSIAN) {
                    this.weighter = new QueuedGaussianDemoWeighter(settings.getFrameblendIndex(), 0, 5d);
                }
            }
            formatContext.video_codec(videoCodec);
        }
        if(fmt.audio_codec() != AV_CODEC_ID_NONE){
            shouldEncodeAudio = true;
            audioCodec = avcodec_find_encoder(settings.getFormat().getAudioCodec());
            if(audioCodec == null){
                throw new LumaException("Unable to initialize audio codec.");
            }
            audioPacket = av_packet_alloc();
            if(audioPacket == null){
                throw new LumaException("Unable to initialize audio packet.");
            }
            audioStream = avformat_new_stream(formatContext, audioCodec);
            if(audioStream == null){
                throw new LumaException("Unable to initialize audio stream.");
            }
            audioContext = avcodec_alloc_context3(audioCodec);
            if(audioContext == null){
                throw new LumaException("Unable to initialize audio context.");
            }
            /* put ideal params, or otherwise first supported */
            audioContext.sample_fmt(pointerContainsOrElseFirst(audioCodec.sample_fmts(), AV_SAMPLE_FMT_S16));
            audioContext.sample_rate(pointerContainsOrElseFirst(audioCodec.supported_samplerates(), 44100));
            audioContext.channel_layout(pointerContainsOrElseFirst(audioCodec.channel_layouts(), AV_CH_LAYOUT_STEREO));
            audioContext.channels(av_get_channel_layout_nb_channels(audioContext.channel_layout()));
            audioStream.time_base(av_make_q(1, audioContext.sample_rate()));
            audioContext.time_base(av_make_q(1, audioContext.sample_rate()));
            if((fmt.flags() & AVFMT_GLOBALHEADER) == AVFMT_GLOBALHEADER){
                audioContext.flags(audioContext.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
            }

            /* setup audio resampler */
            if(AV_SAMPLE_FMT_S16 == audioContext.sample_fmt() && 44100 == audioContext.sample_rate() && AV_CH_LAYOUT_STEREO == audioContext.channel_layout()){
                shouldResampleAudio = false;
            } else {
                if(AV_SAMPLE_FMT_S16 != audioContext.sample_fmt()){
                    System.out.println("AudioContext Sample Format (" + audioContext.sample_fmt() + ") does not match " + AV_SAMPLE_FMT_S16);
                }
                if(44100 != audioContext.sample_rate()){
                    System.out.println("AudioContext Sample Rate (" + audioContext.sample_rate() + ") does not match 44100");
                }
                if(AV_CH_LAYOUT_STEREO != audioContext.channel_layout()){
                    System.out.println("AudioContext Channel Layout (" + audioContext.channel_layout() + ") does not match " + AV_CH_LAYOUT_STEREO);
                }
                swrContext = swr_alloc();
                if (swrContext == null) {
                    throw new LumaException("Unable to initialize audio resample context.");
                }
                av_opt_set_int(swrContext, "in_sample_fmt", AV_SAMPLE_FMT_S16, 0);
                av_opt_set_int(swrContext, "in_sample_rate", 44100, 0);
                av_opt_set_int(swrContext, "in_channel_layout", AV_CH_LAYOUT_STEREO, 0);
                av_opt_set_int(swrContext, "out_sample_fmt", audioContext.sample_fmt(), 0);
                av_opt_set_int(swrContext, "out_sample_rate", audioContext.sample_rate(), 0);
                av_opt_set_int(swrContext, "out_channel_layout", audioContext.channel_layout(), 0);
                int ret = swr_init(swrContext);
                if (ret < 0) {
                    throw new LumaException("Unable to open audio resample context: " + ret);
                }
            }

            /* open audio context */
            if(avcodec_open2(audioContext, null, (AVDictionary) null) < 0){
                throw new LumaException("Failed to open audio codec.");
            }
            //System.out.println(audioContext.frame_size());
            preResampleAudioFrame = allocAudioFrame(audioContext.sample_fmt(), audioContext.channel_layout(),
                    audioContext.sample_rate(), 1024);
            postResampleAudioFrame = allocAudioFrame(audioContext.sample_fmt(), audioContext.channel_layout(),
                    audioContext.sample_rate(), 1024);

            int ret = avcodec_parameters_from_context(audioStream.codecpar(), audioContext);
            if(ret < 0){
                throw new LumaException("Unable to copy stream parameters to the audio context");
            }

            this.audioProcessor = new UnsafeAudioProcessor(preResampleAudioFrame.data(0).address(), (int) preResampleAudioFrame.linesize(0));
            formatContext.audio_codec(audioCodec);
        }

        Files.deleteIfExists(f.toPath());
        av_dump_format(formatContext, 0, f.getAbsolutePath().replace('\\', '/'), 1);
        if((fmt.flags() & AVFMT_NOFILE) != AVFMT_NOFILE){
            AVIOContext avioContext = new AVIOContext();
            if(avio_open(avioContext, new BytePointer(f.getAbsolutePath().replace('\\', '/').getBytes()), AVIO_FLAG_WRITE) < 0){
                throw new LumaException("Failed to open export file for writing");
            }
            formatContext.pb(avioContext);
        }
        int ret = avformat_write_header(formatContext, (AVDictionary) null);
        if(ret < 0){
            byte[] errBuf = new byte[255];
            av_strerror(ret, errBuf, 255);
            System.err.println(new String(errBuf));
            System.err.println("Failed to write header: " + ret);
        }
    }

    private AVFrame allocAudioFrame(int sampleFmt, long channelLayout, int sampleRate, int numSamples) throws LumaException {
        AVFrame frame = av_frame_alloc();
        int ret;
        if(frame == null){
            throw new LumaException("Failed to allocate an audio frame.");
        }
        frame.format(sampleFmt);
        frame.channel_layout(channelLayout);
        frame.sample_rate(sampleRate);
        frame.nb_samples(numSamples);
        if(numSamples > 0){
            ret = av_frame_get_buffer(frame, 0);
            if(ret < 0){
                throw new LumaException("Failed to allocate an audio buffer: " + ret);
            }
        }
        return frame;
    }

    private AVFrame allocVideoFrame(int pixFmt, int width, int height) throws LumaException {
        AVFrame frame = av_frame_alloc();
        if(frame == null){
            throw new LumaException("Failed to allocate a video frame.");
        }
        frame.format(pixFmt);
        frame.width(width);
        frame.height(height);
        int ret = av_frame_get_buffer(frame, 0);
        if(ret < 0){
            throw new LumaException("Failed to allocate a video buffer: " + ret);
        }
        return frame;
    }

    private int pointerContainsOrElseFirst(IntPointer pointer, int check){
        if(pointer == null){
            return check;
        }
        boolean contains = false;
        for(int i = 0; i < pointer.sizeof(); i++){
            if(pointer.get(i) == check){
                contains = true;
                break;
            }
        }
        if(contains){
            return check;
        }
        return pointer.get();
    }

    private long pointerContainsOrElseFirst(LongPointer pointer, long check){
        if(pointer == null){
            return check;
        }
        boolean contains = false;
        for(int i = 0; i < pointer.sizeof(); i++){
            if(pointer.get(i) == check){
                contains = true;
                break;
            }
        }
        if(contains){
            return check;
        }
        return pointer.get();
    }

    @Override
    public boolean checkFrame(int rawIndex) {
        if(!shouldEncodeVideo){
            logger.debug("Ignoring video due to video encoding being disabled");
            return false;
        }
        if(ignoreFrames > 0){
            logger.debug("Ignoring raw index {} to remove broken frames.", rawIndex);
            ignoreFrames--;
            return false;
        }
        return true;
    }

    @Override
    public void handleVideoData(int index, Pointer buf, long offset, long writeLength) throws LumaException {
        int position = index % settings.getFrameblendIndex();

        frame.packet(buf, offset, writeLength, weighter, position, index);

        if(offset == this.frameSize - writeLength && position == topFrame){
            System.out.println("Writing media on frame: " + index + " and position " + position);
            encodeFrame(frame, index / settings.getFrameblendIndex());
            frame.reset();
        }
    }

    @Override
    public void handleAudioData(Pointer buf, long offset, long size) {
        if(shouldEncodeAudio) {
            audioProcessor.packet(buf, offset, size, this::encodeSamples);
        }
    }

    @Override
    public void encodeFrame(Frame frame, long index) throws LumaException {
        this.latestFrame = (index + frameOffset);
        logger.trace("Writing video with index: {}", this.latestFrame);

        if(av_frame_make_writable(preResampleVideoFrame) < 0){
            throw new LumaException("Unable to make pre-frame writable");
        }
        if(av_frame_make_writable(postResampleVideoFrame) < 0){
            throw new LumaException("Unable to make post-frame writable");
        }
        sws_scale(swsContext, preResampleVideoFrame.data(), preResampleVideoFrame.linesize(), 0, preResampleVideoFrame.height(), postResampleVideoFrame.data(), postResampleVideoFrame.linesize());
        postResampleVideoFrame.pts(this.latestFrame - ignoreFrames);
        int ret = avcodec_send_frame(videoContext, postResampleVideoFrame);
        if(ret < 0){
            throw new LumaException("Failed to submit a frame to encode: " + ret);
        }
        while(ret >= 0){
            ret = avcodec_receive_packet(videoContext, videoPacket);
            if(ret < 0 && ret != AVERROR_EAGAIN() && ret != AVERROR_EOF()){
                throw new LumaException("Encountered error while encoding frame: " + ret);
            } else if(ret >= 0) {
                videoPacket.pts(this.latestFrame - ignoreFrames);
                videoPacket.stream_index(videoStream.index());
                ret = av_interleaved_write_frame(formatContext, videoPacket);
                if(ret < 0){
                    throw new LumaException("Failed to write a video frame: " + ret);
                }
            }
        }
    }

    @Override
    public boolean checkSamples(MediaAudio samples) {
        if(!shouldEncodeAudio){
            logger.debug("Ignoring samples due to audio encoding being disabled");
            return false;
        }
        if(this.ignoreSamples >= samples.getNumSamples()){
            logger.debug("Ignoring samples at timestamp {} to remove broken frames.", samples.getTimeStamp());
            this.ignoreSamples -= samples.getNumSamples();
            return false;
        }
        return true;
    }

    @Override
    public void encodeSamples(Long index) {
        logger.debug("Encoding samples");
        this.latestSample = index + this.sampleOffset;

        AVFrame frame;
        if(shouldResampleAudio) {
            int ret = swr_convert(swrContext, postResampleAudioFrame.data(0), postResampleAudioFrame.nb_samples(),
                    preResampleAudioFrame.data(0), preResampleAudioFrame.nb_samples());
            if (ret < 0) {
                System.err.println("Failed to feed audio data to the resampler, skipping");
                return;
            }
            frame = postResampleAudioFrame;
        } else {
            frame = preResampleAudioFrame;
        }

        int ret = av_frame_make_writable(frame);
        if(ret < 0){
            System.err.println("Failed to make audio frame writable, skipping");
            return;
        }
        frame.pts(latestSample);

        ret = avcodec_send_frame(audioContext, frame);
        if(ret < 0){
            System.err.println("Failed to send frame to audio encoder, skipping: " + ret);
        }
        while(ret >= 0){
            ret = avcodec_receive_packet(audioContext, audioPacket);
            if(ret < 0 && ret != AVERROR_EAGAIN() && ret != AVERROR_EOF()){
                System.err.println("Failed to encode audio frame, skipping: " + ret);
            } else if(ret >= 0){
                audioPacket.pts(latestSample);
                audioPacket.stream_index(audioStream.index());
                ret = av_interleaved_write_frame(formatContext, audioPacket);
                if(ret < 0){
                    System.err.println("Failed to write audio packet, skipping: " + ret);
                }
            }
        }
    }

    @Override
    public void finish() throws IOException, InterruptedException, LumaException {
        if(shouldEncodeVideo) {
            //Flush video encoder:
            int ret = avcodec_send_frame(videoContext, null);
            if (ret < 0) {
                System.err.println("Failed to send flush packet to video encoder, skipping: " + ret);
            }
            while (ret >= 0) {
                ret = avcodec_receive_packet(videoContext, videoPacket);
                if (ret < 0 && ret != AVERROR_EAGAIN() && ret != AVERROR_EOF()) {
                    System.err.println("Failed to encode audio flush packet, skipping: " + ret);
                } else if (ret >= 0) {
                    videoPacket.pts((this.latestFrame - ignoreFrames) + 1);
                    videoPacket.stream_index(videoStream.index());
                    ret = av_interleaved_write_frame(formatContext, videoPacket);
                    if (ret < 0) {
                        System.err.println("Failed to write audio flush packet, skipping: " + ret);
                    }
                }
            }
        }
        if(shouldEncodeAudio) {
            //Flush audio encoder:
            int ret = avcodec_send_frame(audioContext, null);
            if (ret < 0) {
                System.err.println("Failed to send flush packet to audio encoder, skipping: " + ret);
            }
            while (ret >= 0) {
                ret = avcodec_receive_packet(audioContext, audioPacket);
                if (ret < 0 && ret != AVERROR_EAGAIN() && ret != AVERROR_EOF()) {
                    System.err.println("Failed to encode audio flush packet, skipping: " + ret);
                } else if (ret >= 0) {
                    audioPacket.pts(latestSample + postResampleAudioFrame.nb_samples());
                    audioPacket.stream_index(audioStream.index());
                    ret = av_interleaved_write_frame(formatContext, audioPacket);
                    if (ret < 0) {
                        System.err.println("Failed to write audio flush packet, skipping: " + ret);
                    }
                }
            }
        }
        forcefullyClose();
    }

    @Override
    public void forcefullyClose() throws LumaException {
        if(closed){
            return;
        }
        System.out.println(formatContext.pb().pos());
        int ret = av_write_trailer(formatContext);
        if(ret < 0){
            throw new LumaException("Failed to write trailer: " + ret);
        }

        if(videoContext != null) avcodec_free_context(videoContext);
        if(audioContext != null) avcodec_free_context(audioContext);
        if(videoPacket != null) av_packet_free(videoPacket);
        if(audioPacket != null) av_packet_free(audioPacket);
        if(preResampleVideoFrame != null) av_frame_free(preResampleVideoFrame);
        if(postResampleVideoFrame != null) av_frame_free(postResampleVideoFrame);
        if(preResampleAudioFrame != null) av_frame_free(preResampleAudioFrame);
        if(postResampleVideoFrame != null) av_frame_free(postResampleAudioFrame);
        if(swsContext != null) sws_freeContext(swsContext);
        if(swrContext != null) swr_free(swrContext);

        if((formatContext.oformat().flags() & AVFMT_NOFILE) != AVFMT_NOFILE){
            avio_close(formatContext.pb());
        }
        avformat_free_context(formatContext);
        closed = true;
    }

    @Override
    public void setIgnoreTime(double ignoreTime){
        if(shouldEncodeVideo) this.ignoreFrames = (long) (videoContext.time_base().den() * ignoreTime);
        if(shouldEncodeAudio) this.ignoreSamples = (long) (audioContext.sample_rate() * ignoreTime);
    }

    @Override
    public long getLatestFrame(){
        return this.latestFrame;
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
