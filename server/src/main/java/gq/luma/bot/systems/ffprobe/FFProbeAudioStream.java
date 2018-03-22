package gq.luma.bot.systems.ffprobe;

import com.eclipsesource.json.JsonObject;

public class FFProbeAudioStream extends FFProbeStream {
    private String sampleFormat;
    private int sampleRate;
    private int channels;
    private String channelLayout;
    private int bitsPerSample;

    @Override
    protected void generate(JsonObject json) {
        this.sampleFormat = json.get("sample_fmt").asString();
        this.sampleRate = Integer.parseInt(json.get("sample_rate").asString());
        this.channels = json.get("channels").asInt();
        this.channelLayout = json.get("channel_layout").asString();
        this.bitsPerSample = json.get("bits_per_sample").asInt();
    }

    public String getSampleFormat() {
        return sampleFormat;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public String getChannelLayout() {
        return channelLayout;
    }

    public int getBitsPerSample() {
        return bitsPerSample;
    }
}
