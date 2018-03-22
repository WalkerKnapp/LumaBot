package gq.luma.bot;

import io.humble.ferry.Buffer;
import io.humble.video.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BeepSoundTest {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static void main(String[] args) throws Exception {
        BeepSoundTest test = new BeepSoundTest();
        test.mp2AACTest();
    }

    public void playTest() throws Exception {
        logger.info("start playTest");
        SourceDataLine audioLine = null;
        MediaAudio samples = beepSamples();
        logger.info("sample is ready");

        AudioFormat format = new AudioFormat((float)samples.getSampleRate(), (int)samples.getBytesPerSample() * 8, samples.getChannels(), true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        audioLine = (SourceDataLine) AudioSystem.getLine(info);
        audioLine.open(format);
        logger.info("beepstart");
        audioLine.start();
        Buffer buffer = samples.getData(0);
        audioLine.write(buffer.getByteArray(0, samples.getDataPlaneSize(0)), 0, samples.getDataPlaneSize(0));
        audioLine.drain();
        logger.info("beepend");
        audioLine.close();
        audioLine = null;
    }
    public void flvNellymoserTest() throws Exception {
        logger.info("flvNellymoserTest");
        Muxer muxer = Muxer.make(this.getClass().getCanonicalName()+".flv_nellymoser.flv", null, null);
        Encoder encoder = Encoder.make(Codec.findEncodingCodec(Codec.ID.CODEC_ID_NELLYMOSER));
        io.humble.video.AudioFormat.Type findType = null;
        for(io.humble.video.AudioFormat.Type type : encoder.getCodec().getSupportedAudioFormats()) {
            if(findType == null) {
                findType = type;
            }
            if(type == io.humble.video.AudioFormat.Type.SAMPLE_FMT_S16) {
                findType = type;
                break;
            }
        }
        logger.info(findType.toString());
        int sampleRate = 44100;
        encoder.setSampleRate(sampleRate);
        Rational encoderTimeBase = Rational.make(1, sampleRate);
        encoder.setTimeBase(encoderTimeBase);
        encoder.setChannels(1);
        encoder.setChannelLayout(AudioChannel.Layout.CH_LAYOUT_MONO);
        encoder.setSampleFormat(findType);
        encoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true);
        encoder.open(null, null);
        muxer.addNewStream(encoder);
        processConvert(muxer, encoder);
        logger.info("done");
    }
    public void oggVorbisTest() throws Exception {
        logger.info("oggVorbisTest");
        Muxer muxer = Muxer.make(this.getClass().getCanonicalName()+".ogg_vorbis.ogg", null, null);
        Encoder encoder = Encoder.make(Codec.findEncodingCodec(Codec.ID.CODEC_ID_VORBIS));
        io.humble.video.AudioFormat.Type findType = null;
        for(io.humble.video.AudioFormat.Type type : encoder.getCodec().getSupportedAudioFormats()) {
            if(findType == null) {
                findType = type;
            }
            if(type == io.humble.video.AudioFormat.Type.SAMPLE_FMT_S16) {
                findType = type;
                break;
            }
        }
        logger.info(findType.toString());
        int sampleRate = 44100;
        encoder.setSampleRate(sampleRate);
        Rational encoderTimeBase = Rational.make(1, sampleRate);
        encoder.setTimeBase(encoderTimeBase);
        encoder.setChannels(2);
        encoder.setChannelLayout(AudioChannel.Layout.CH_LAYOUT_STEREO);
        encoder.setSampleFormat(findType);
        encoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true);
        encoder.open(null, null);
        muxer.addNewStream(encoder);
        processConvert(muxer, encoder);
        logger.info("done");
    }
    public void flvMp3Test() throws Exception {
        logger.info("flvMp3Test");
        Muxer muxer = Muxer.make(this.getClass().getCanonicalName()+".flv_mp3.flv", null, null);
        Encoder encoder = Encoder.make(Codec.findEncodingCodec(Codec.ID.CODEC_ID_MP3));
        io.humble.video.AudioFormat.Type findType = null;
        for(io.humble.video.AudioFormat.Type type : encoder.getCodec().getSupportedAudioFormats()) {
            if(findType == null) {
                findType = type;
            }
            if(type == io.humble.video.AudioFormat.Type.SAMPLE_FMT_S16) {
                findType = type;
                break;
            }
        }
        logger.info(findType.toString());
        int sampleRate = 44100;
        encoder.setSampleRate(sampleRate);
        Rational encoderTimeBase = Rational.make(1, sampleRate);
        encoder.setTimeBase(encoderTimeBase);
        encoder.setChannels(2);
        encoder.setChannelLayout(AudioChannel.Layout.CH_LAYOUT_STEREO);
        encoder.setSampleFormat(findType);
        encoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true);
        encoder.open(null, null);
        muxer.addNewStream(encoder);
        processConvert(muxer, encoder);
        logger.info("done");
    }

    public void mp2AACTest() throws Exception {
        logger.info("mp2AACTest");
        Muxer muxer = Muxer.make(this.getClass().getCanonicalName()+".mp4_aac.mp4", null, null);
        Encoder encoder = Encoder.make(Codec.findEncodingCodecByName("libvo_aacenc"));
        io.humble.video.AudioFormat.Type findType = null;
        for(io.humble.video.AudioFormat.Type type : encoder.getCodec().getSupportedAudioFormats()) {
            if(findType == null) {
                findType = type;
            }
            if(type == io.humble.video.AudioFormat.Type.SAMPLE_FMT_S16) {
                findType = type;
                break;
            }
        }
        logger.info(findType.toString());
        int sampleRate = 44100;
        encoder.setSampleRate(sampleRate);
        Rational encoderTimeBase = Rational.make(1, sampleRate);
        encoder.setTimeBase(encoderTimeBase);
        encoder.setChannels(2);
        encoder.setChannelLayout(AudioChannel.Layout.CH_LAYOUT_STEREO);
        encoder.setSampleFormat(findType);
        encoder.setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true);
        encoder.open(null, null);
        muxer.addNewStream(encoder);
        processConvert(muxer, encoder);
        logger.info("done");
    }

    private void processConvert(Muxer muxer, Encoder encoder) throws Exception {
        muxer.open(null, null);
        MediaPacket packet = MediaPacket.make();
        MediaAudio samples = beepSamples();
        logger.info(samples.toString());
        if(samples.getSampleRate() != encoder.getSampleRate()
                || samples.getFormat() != encoder.getSampleFormat()
                || samples.getChannelLayout() != encoder.getChannelLayout()) {
            final MediaAudioResampler resampler = MediaAudioResampler.make(
                    encoder.getChannelLayout(), encoder.getSampleRate(), encoder.getSampleFormat(),
                    samples.getChannelLayout(), samples.getSampleRate(), samples.getFormat());
            resampler.open();
            MediaAudio spl = MediaAudio.make(samples.getNumSamples(), encoder.getSampleRate(), encoder.getChannels(), encoder.getChannelLayout(), encoder.getSampleFormat());
            resampler.resample(spl, samples);
            logger.info(spl.toString());
            logger.info("{}", spl.getNumSamples());
            //Assert.assertEquals(spl.getNumSamples(), samples.getNumSamples());
            samples = spl;
        }
        logger.info(samples.toString());

        // we only have one set of samples.
        encoder.encodeAudio(packet, samples);
        logger.info("{}", packet);
        if(packet.isComplete())
            muxer.write(packet, false);

        // Flush the encoders
        do {
            encoder.encodeAudio(packet, null);
            logger.info("{}", packet);
            if(packet.isComplete()) {
                muxer.write(packet, false);
            }
        } while (packet.isComplete());
        muxer.close();
    }

    /**
     * make sine wave humble MediaAudio.
     * @return
     */
    private MediaAudio beepSamples() {
        int sampleRate = 44100; // 44.1KHz
        int sampleNum  = 44100; // 44100 samples(1sec)
        int channel    = 2;     // 2channel(stereo)
        int tone       = 440;   // 440Hz tone.
        int bit        = 16;    // 16bit
        ByteBuffer buffer = ByteBuffer.allocate((int)sampleNum * bit * channel / 8);
        double rad = tone * 2 * Math.PI / sampleRate; // radian for each sample.
        double max = (1 << (bit - 2)) - 1; // ampletude
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for(int i = 0;i < sampleNum;i ++) {
            short data = (short)(Math.sin(rad * i) * max);
            for(int j = 0;j < channel;j ++) {
                buffer.putShort(data);
            }
        }
        buffer.flip();

        logger.info("data size for 1sec buffer.:" + buffer.remaining());
        MediaAudio samples = MediaAudio.make(sampleNum, sampleRate, channel, AudioChannel.Layout.CH_LAYOUT_STEREO, io.humble.video.AudioFormat.Type.SAMPLE_FMT_S16);
        samples.getData(0).put(buffer.array(), 0, 0, buffer.remaining());
        logger.info("{}", samples.getDataPlaneSize(0)); // why this size is little bit bigger than original buffer?
        samples.setComplete(true);
        samples.setTimeBase(Rational.make(1, 44100));
        samples.setTimeStamp(0);
        samples.setNumSamples(sampleNum);
        return samples;
    }
}