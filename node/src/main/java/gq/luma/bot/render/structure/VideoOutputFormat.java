package gq.luma.bot.render.structure;

import io.humble.video.Codec;
import io.humble.video.MuxerFormat;

public enum VideoOutputFormat {
    H264("mp4", Codec.ID.CODEC_ID_H264, Codec.ID.CODEC_ID_AAC),
    DNXHD("mov", Codec.ID.CODEC_ID_DNXHD, Codec.ID.CODEC_ID_AAC),
    HUFFYUV("avi", MuxerFormat.guessFormat("huffyuv", null, null), Codec.ID.CODEC_ID_HUFFYUV, Codec.ID.CODEC_ID_AAC),
    RAW("mov", Codec.ID.CODEC_ID_RAWVIDEO, Codec.ID.CODEC_ID_AAC);

    private String outputContainer;
    private MuxerFormat format;
    private Codec.ID videoCodec;
    private Codec.ID audioCodec;

    VideoOutputFormat(String container, Codec.ID videoCodec, Codec.ID audioCodec){
        this.outputContainer = container;
        this.format = MuxerFormat.guessFormat(container, null, null);
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
    }

    VideoOutputFormat(String outputContainer, MuxerFormat muxerFormat, Codec.ID videoCodec, Codec.ID audioCodec){
        this.outputContainer = outputContainer;
        this.format = muxerFormat;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
    }

    public String getOutputContainer(){
        return this.outputContainer;
    }

    public MuxerFormat getFormat() {
        return format;
    }

    public Codec.ID getVideoCodec() {
        return videoCodec;
    }

    public Codec.ID getAudioCodec() {
        return audioCodec;
    }
}
