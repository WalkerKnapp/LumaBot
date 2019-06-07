package gq.luma.bot;


public enum VideoOutputFormat {
    H264("mp4", 27, 86018, 0),
    GIF("gif", 97, -1, 20),
    DNXHD("mov", 99, 86018, 4),
    HUFFYUV("avi", 25, 86018, 4),
    RAW("mov", 13, 86018, 0),
    WAV("wav", -1, 65536, 0);

    private String outputContainer;
    private String format;
    private int videoCodec;
    private int audioCodec;
    private int pixelFormat;

    VideoOutputFormat(String container, int videoCodec, int audioCodec, int pixelFormat){
        this.outputContainer = container;
        this.format = container;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.pixelFormat = pixelFormat;
    }

    VideoOutputFormat(String outputContainer, String muxerFormat, int videoCodec, int audioCodec, int pixelFormat){
        this.outputContainer = outputContainer;
        this.format = muxerFormat;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.pixelFormat = pixelFormat;
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

    public int getPixelFormat() {
        return pixelFormat;
    }
}
