package gq.luma.bot;

public enum RenderPreset {
    DIRT (854, 480, 30, 1, 22, true, false),
    LOW (1280, 720, 30, 1, 21, true, false),
    MEDIUM (1920, 1080, 60, 1, 19, true, true),
    HIGH (1920, 1080, 60, 8, 18, true, true),
    INSANE (1920, 1080, 60, 32, 17, true, true);

    private int width;
    private int height;

    private int framerate;
    private int frameblendIndex;
    private int crf;
    private boolean startOdd;
    private boolean hq;

    RenderPreset(int width, int height, int framerate, int frameblendIndex, int crf, boolean startOdd, boolean hq){
        this.width = width;
        this.height = height;
        this.framerate = framerate;
        this.frameblendIndex = frameblendIndex;
        this.startOdd = startOdd;
        this.crf = crf;
        this.hq = hq;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFramerate() {
        return framerate;
    }

    public int getFrameblendIndex() {
        return frameblendIndex;
    }

    public boolean isStartOdd() {
        return startOdd;
    }

    public boolean isHq() {
        return hq;
    }

    public int getCrf(){
        return crf;
    }
}
