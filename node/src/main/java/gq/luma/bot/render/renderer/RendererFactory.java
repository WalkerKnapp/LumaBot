package gq.luma.bot.render.renderer;

import gq.luma.bot.RenderSettings;
import gq.luma.bot.VideoOutputFormat;
import gq.luma.bot.LumaException;
import io.humble.video.*;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;

public class RendererFactory {
    public static SinglePassFFRenderer createSinglePass(RenderSettings settings, File exportFile) throws IOException, InterruptedException, LumaException {
        Muxer m = Muxer.make(exportFile.getAbsolutePath(), null, null);

        Codec codec = Codec.findEncodingCodecByIntID(settings.getFormat().getVideoCodec());
        System.out.println("Video Codec: " + codec.getName());
        Encoder videoEncoder = Encoder.make(codec);
        videoEncoder.setWidth(settings.getWidth());
        videoEncoder.setHeight(settings.getHeight());
        videoEncoder.setPixelFormat(PixelFormat.Type.PIX_FMT_YUV420P);
        videoEncoder.setTimeBase(Rational.make(1, settings.getFps()));
        if(settings.getFormat() == VideoOutputFormat.H264) {
            videoEncoder.setPixelFormat(PixelFormat.Type.PIX_FMT_YUV420P);
            videoEncoder.setProperty("preset", "slow");
            videoEncoder.setProperty("crf", settings.getCrf());
        } else if(settings.getFormat() == VideoOutputFormat.DNXHD){
            videoEncoder.setPixelFormat(PixelFormat.Type.PIX_FMT_YUV422P);
            videoEncoder.setProperty("b", "185M");
            //videoEncoder.setProperty("an", true);
        } else if(settings.getFormat() == VideoOutputFormat.HUFFYUV){
            videoEncoder.setPixelFormat(PixelFormat.Type.PIX_FMT_YUV422P);
        } else if(settings.getFormat() == VideoOutputFormat.GIF){
        }

        Codec audioCodec = Codec.findEncodingCodecByIntID(settings.getFormat().getAudioCodec());
        System.out.println("Audio Codec: " + audioCodec.getName());
        Encoder audioEncoder = Encoder.make(audioCodec);

        AudioFormat.Type findType = null;
        for(AudioFormat.Type type : audioCodec.getSupportedAudioFormats()) {
            if(findType == null) {
                findType = type;
            }
            if(type == AudioFormat.Type.SAMPLE_FMT_S16) {
                findType = type;
                break;
            }
        }

        if(findType == null)
            throw new LumaException("Unable to find valid audio format for audio codec: " + audioCodec.getName());

        audioEncoder.setSampleRate(44100);
        audioEncoder.setTimeBase(Rational.make(1, 44100));
        audioEncoder.setChannels(2);
        audioEncoder.setChannelLayout(AudioChannel.Layout.CH_LAYOUT_STEREO);
        audioEncoder.setSampleFormat(findType);

        if(m.getFormat().getFlag(MuxerFormat.Flag.GLOBAL_HEADER)) {
            videoEncoder.setFlag(Encoder.Flag.FLAG_GLOBAL_HEADER, true);
            audioEncoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true);
        }

        videoEncoder.open(null, null);
        audioEncoder.open(null, null);
        m.addNewStream(audioEncoder);
        m.addNewStream(videoEncoder);
        m.open(null, null);

        return new SinglePassFFRenderer(m, videoEncoder, audioEncoder);
    }

    public static TwoPassFFRenderer createTwoPass(RenderSettings settings, File exportFile) throws LumaException, IOException, InterruptedException {
        File huffyFile = new File(exportFile.getParent(), FilenameUtils.removeExtension(exportFile.getName()) + "." + VideoOutputFormat.HUFFYUV.getOutputContainer());
        Muxer m = Muxer.make(huffyFile.getAbsolutePath(), MuxerFormat.guessFormat(VideoOutputFormat.HUFFYUV.getFormat(), null, null), null);

        Codec codec = Codec.findEncodingCodecByIntID(VideoOutputFormat.HUFFYUV.getVideoCodec());
        Encoder videoEncoder = Encoder.make(codec);
        videoEncoder.setWidth(settings.getWidth());
        videoEncoder.setHeight(settings.getHeight());
        videoEncoder.setPixelFormat(PixelFormat.Type.PIX_FMT_YUV422P);
        videoEncoder.setTimeBase(Rational.make(1, settings.getFps()));

        Codec audioCodec = Codec.findEncodingCodecByIntID(VideoOutputFormat.HUFFYUV.getAudioCodec());
        Encoder audioEncoder = Encoder.make(audioCodec);

        AudioFormat.Type findType = null;
        for(AudioFormat.Type type : audioCodec.getSupportedAudioFormats()) {
            if(findType == null) {
                findType = type;
            }
            if(type == AudioFormat.Type.SAMPLE_FMT_S16) {
                findType = type;
                break;
            }
        }

        if(findType == null)
            throw new LumaException("Unable to find valid audio format for audio codec: " + audioCodec.getName());

        audioEncoder.setSampleRate(44100);
        audioEncoder.setTimeBase(Rational.make(1, 44100));
        audioEncoder.setChannels(2);
        audioEncoder.setChannelLayout(AudioChannel.Layout.CH_LAYOUT_STEREO);
        audioEncoder.setSampleFormat(findType);

        if(m.getFormat().getFlag(MuxerFormat.Flag.GLOBAL_HEADER)) {
            videoEncoder.setFlag(Encoder.Flag.FLAG_GLOBAL_HEADER, true);
            audioEncoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true);
        }

        videoEncoder.open(null, null);
        audioEncoder.open(null, null);
        m.addNewStream(audioEncoder);
        m.addNewStream(videoEncoder);
        m.open(null, null);

        return new TwoPassFFRenderer(m, videoEncoder, audioEncoder, huffyFile, exportFile, settings);
    }
}
