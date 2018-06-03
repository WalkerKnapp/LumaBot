package gq.luma.bot;


public enum VideoOutputFormat {
    H264("mp4", 28, 86018),
    GIF("gif", 98, -1),
    DNXHD("mov", 100, 86018),
    HUFFYUV("avi", "huffyuv", 26, 86018),
    RAW("mov", 14, 86018);

    private String outputContainer;
    private String format;
    private int videoCodec;
    private int audioCodec;

    VideoOutputFormat(String container, int videoCodec, int audioCodec){
        this.outputContainer = container;
        this.format = container;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
    }

    VideoOutputFormat(String outputContainer, String muxerFormat, int videoCodec, int audioCodec){
        this.outputContainer = outputContainer;
        this.format = muxerFormat;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
    }

    public String getOutputContainer(){
        return this.outputContainer;
    }

    public String getFormat() {
        return format;
    }

    public int getVideoCodec() {
        return videoCodec;
    }

    public int getAudioCodec() {
        return audioCodec;
    }
}
