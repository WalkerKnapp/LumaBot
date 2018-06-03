package gq.luma.bot.render.fs;

import com.dokany.java.DokanyFileSystem;
import com.dokany.java.Win32FindStreamData;
import com.dokany.java.constants.FileAttribute;
import com.dokany.java.structure.*;
import com.sun.jna.platform.win32.WinBase;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.RenderSettings;
import gq.luma.bot.RenderWeighterType;
import gq.luma.bot.render.audio.AudioProcessor;
import gq.luma.bot.render.audio.BufferedAudioProcessor;
import gq.luma.bot.render.fs.frame.Frame;
import gq.luma.bot.render.fs.frame.UnweightedFrame;
import gq.luma.bot.render.fs.frame.WeightedFrame;
import gq.luma.bot.render.fs.weighters.DemoWeighter;
import gq.luma.bot.render.fs.weighters.LinearDemoWeighter;
import gq.luma.bot.render.fs.weighters.QueuedGaussianDemoWeighter;
import gq.luma.bot.LumaException;
import io.humble.video.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Deprecated
public class DokanyRenderFS extends DokanyFileSystem implements RenderFS {
    private int frameSize;
    private int topFrame;

    private RenderSettings settings;
    private DemoWeighter demoWeighter;

    private Frame currentFrame;

    private CompletableFuture<Void> errorHandler;
    private FFRenderer renderer;

    private AudioProcessor audioProcessor;

    private long timeOfLastFrame;
    private Thread timeOutThread;

    DokanyRenderFS(DeviceOptions deviceOptions, VolumeInformation volumeInfo, RenderSettings settings, FFRenderer renderer) {
        super(deviceOptions, volumeInfo, new FreeSpace(1024L * 1024L * 4096L, 1024L * 1024L), new Date(), "/");

        this.errorHandler = new CompletableFuture<>();

        if(settings != null) {
            int pixelCount = settings.getHeight() * settings.getWidth() * 3;
            this.frameSize = pixelCount + 18;
            this.settings = settings;
            this.topFrame = settings.getFrameblendIndex() - 1;

            this.renderer = renderer;

            MediaPicture videoPicture = renderer.generateResampledTemplate();
            MediaPicture resampleFrame = renderer.generateOriginalTemplate();

            this.audioProcessor = new BufferedAudioProcessor();
            this.timeOfLastFrame = System.currentTimeMillis();

            if (settings.getFrameblendIndex() == 1) {
                this.currentFrame = new UnweightedFrame(resampleFrame, videoPicture);
            } else {
                System.out.println("Pixel count: " + pixelCount);
                this.currentFrame = new WeightedFrame(resampleFrame, videoPicture, pixelCount);
                if (settings.getWeighterType() == RenderWeighterType.LINEAR) {
                    System.out.println("Setting weighter to linear");
                    this.demoWeighter = new LinearDemoWeighter(settings.getFrameblendIndex());
                } else if (settings.getWeighterType() == RenderWeighterType.GAUSSIAN) {
                    System.out.println("Setting weighter to gaussian");
                    this.demoWeighter = new QueuedGaussianDemoWeighter(settings.getFrameblendIndex(), 0, 5d);
                }
            }

            this.timeOutThread = new Thread(() -> {
                try {
                    while (timeOfLastFrame > System.currentTimeMillis() - 120000) {
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ignored) {
                    return;
                }
                errorHandler.completeExceptionally(new LumaException("The game has timed out. Please retry the demo."));
            });
            this.timeOutThread.start();
        }
    }

    @Override
    public void waitToFinish() throws IOException, InterruptedException {
        this.timeOutThread.interrupt();
        this.renderer.finish();
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void configure(RenderSettings settings, FFRenderer renderer) {

    }

    @Override
    public CompletableFuture<Void> getErrorHandler() {
        return errorHandler;
    }

    @Override
    public void mounted() {
        System.out.println("Mounted filesystem. " + this.volumeInfo.toString());
    }

    @Override
    public void unmounted() {
        System.out.println("Unmounted filesystem. " + this.volumeInfo.toString());
    }

    @Override
    public int write(String path, int offset, byte[] data, int writeLength) {
        this.timeOfLastFrame = System.currentTimeMillis();

        try {
            String extension = path.substring(path.length() - 3);
            if (extension.equalsIgnoreCase("tga")) {
                int index = extractIndex(path);
                addFrame(index, data, offset, writeLength);
            } else if (extension.equalsIgnoreCase("wav")) {
                audioProcessor.packet(data, offset, writeLength, this.renderer::encodeSamples);
            }
        }
        catch (Exception e){
            errorHandler.completeExceptionally(e);
        }

        return writeLength;
    }

    private void addFrame(int index, byte[] data, int offset, int writeLength) {
        int position = index % settings.getFrameblendIndex();

        currentFrame.packet(data, offset, writeLength, demoWeighter, position, index);

        if(offset == this.frameSize - data.length && position == topFrame){
            System.out.println("Writing media on frame: " + index + " and position " + position);
            this.renderer.encodeFrame(currentFrame, index / settings.getFrameblendIndex());
            currentFrame.reset();
        }
    }

    @Override
    public void createEmptyFile(String path, long options, EnumIntegerSet<FileAttribute> attributes) {
        System.out.printf("[%d] Recieved Create Empty File IO Request. Path: %s, Options: %d", System.currentTimeMillis(), path, options);
    }

    @Override
    public boolean doesPathExist(String s) {
        return true;
    }

    @Override
    public Set<WinBase.WIN32_FIND_DATA> findFilesWithPattern(String s, DokanyFileInfo dokanyFileInfo, String s1) {
        return Collections.emptySet();
        //throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public Set<Win32FindStreamData> findStreams(String s) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void unlock(String s, int i, int i1) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void lock(String s, int i, int i1) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void move(String s, String s1, boolean b) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void deleteFile(String s, DokanyFileInfo dokanyFileInfo) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void deleteDirectory(String s, DokanyFileInfo dokanyFileInfo) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public FileData read(String s, int i, int i1) {
        //throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
        return new FileData(new byte[0], 0);
    }

    @Override
    public void createEmptyDirectory(String s, long l, EnumIntegerSet<FileAttribute> enumIntegerSet) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void flushFileBuffers(String s) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void cleanup(String s, DokanyFileInfo dokanyFileInfo) {
        //throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void close(String s, DokanyFileInfo dokanyFileInfo) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public int getSecurity(String s, int i, byte[] bytes) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void setSecurity(String s, int i, byte[] bytes) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public long truncate(String s) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void setAllocationSize(String s, int i) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void setEndOfFile(String s, int i) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public void setAttributes(String s, EnumIntegerSet<FileAttribute> enumIntegerSet) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    @Override
    public FullFileInfo getInfo(String s) throws FileNotFoundException {
        //throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
        return new FullFileInfo(s, 0, new EnumIntegerSet<>(FileAttribute.class), this.volumeInfo.getSerialNumber());
    }

    @Override
    public void setTime(String s, WinBase.FILETIME filetime, WinBase.FILETIME filetime1, WinBase.FILETIME filetime2) {
        throw new UnsupportedOperationException("Unsupported operation with this filesystem attempted.");
    }

    public void interrupt(){
        timeOutThread.interrupt();
    }
}
