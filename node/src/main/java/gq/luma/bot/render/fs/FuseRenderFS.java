package gq.luma.bot.render.fs;

import gq.luma.bot.render.fs.frame.*;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.render.structure.RenderSettings;
import gq.luma.bot.render.structure.RenderWeighterType;
import gq.luma.bot.render.audio.AudioProcessor;
import gq.luma.bot.render.audio.BufferedAudioProcessor;
import gq.luma.bot.render.fs.weighters.DemoWeighter;
import gq.luma.bot.render.fs.weighters.LinearDemoWeighter;
import gq.luma.bot.render.fs.weighters.QueuedGaussianDemoWeighter;
import io.humble.video.MediaPicture;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import org.apache.commons.codec.binary.Hex;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static jnr.ffi.Platform.OS.WINDOWS;

public class FuseRenderFS extends FuseStubFS implements RenderFS {

    private CompletableFuture<Void> errorHandler;
    private RenderSettings settings;
    private AudioProcessor audioProcessor;
    private Frame currentFrame;
    private FFRenderer renderer;

    private DemoWeighter weighter;

    private long frameSize;
    private int topFrame;

    private String latestCreated = "";

    @Override
    public void configure(RenderSettings settings, FFRenderer renderer){
        this.errorHandler = new CompletableFuture<>();
        this.errorHandler.exceptionally(t -> {
            t.printStackTrace();
            return null;
        });

        MediaPicture videoPicture = renderer.generateResampledTemplate();
        MediaPicture resampleFrame = renderer.generateOriginalTemplate();

        this.audioProcessor = new BufferedAudioProcessor();
        this.topFrame = settings.getFrameblendIndex() - 1;
        this.renderer = renderer;
        int pixelCount = settings.getHeight() * settings.getWidth() * 3;
        this.frameSize = pixelCount + 18;
        this.settings = settings;

        if (settings.getFrameblendIndex() == 1) {
            this.currentFrame = new UnweightedFrame(resampleFrame, videoPicture);
        } else {
            //System.out.println("Pixel count: " + pixelCount);
            //this.currentFrame = new WeightedFrame(resampleFrame, videoPicture, pixelCount);
            this.currentFrame = new AparapiAccumulatorFrame(resampleFrame, videoPicture, pixelCount);
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
    public CompletableFuture<Void> getErrorHandler() {
        return this.errorHandler;
    }

    @Override
    public void waitToFinish() throws IOException, InterruptedException {
        this.renderer.finish();
    }

    @Override
    public void shutdown() {
        super.umount();
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        //System.err.println("FILE WAS OPENED::: " + path);
        //this.latestCreated = "";
        return 0;
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        //System.err.println("FILE WAS CREATED::: " + path);
        this.latestCreated = path;
        return 0;
    }


    @Override
    public int getattr(String path, FileStat stat) {
        if(path.equalsIgnoreCase("/")){
            stat.st_mode.set(FileStat.S_IFDIR | 0777);
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().pid.get());
            return 0;
        } else if(path.equalsIgnoreCase(latestCreated)){
            stat.st_mode.set(FileStat.S_IFREG | 0777);
            stat.st_size.set(0);
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().pid.get());
            //System.out.println("Getattr: " + path + " exists! Returning 0 due to equalling last created");
            return 0;
        } else if(!latestCreated.isEmpty() && path.substring(path.length() - 3).equalsIgnoreCase("wav")){
            stat.st_mode.set(FileStat.S_IFREG | 0777);
            stat.st_size.set(0);
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().pid.get());
            //System.out.println("Getattr: " + path + " exists! Returning 0 due to lastcreated being present and audio");
            return 0;
        } else  {
            //System.out.println("Getattr: " + path + " does not exist! Returning " + -ErrorCodes.ENOENT());
            return -ErrorCodes.ENOENT();
        }
    }


    @Override
    public int statfs(String path, Statvfs stbuf) {
        if (Platform.getNativePlatform().getOS() == WINDOWS) {
            if ("/".equals(path)) {
                stbuf.f_blocks.set(1024 * 1024); // total data blocks in file system
                stbuf.f_frsize.set(1024);        // fs block size
                stbuf.f_bfree.set(1024 * 1024);  // free blocks in fs
            }
        }
        return super.statfs(path, stbuf);
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi){
        try {
            String extension = path.substring(path.length() - 3);
            if (extension.equalsIgnoreCase("tga")) {
                int index = extractIndex(path);
                if(renderer.checkFrame(index)) {
                    addFrame(index, buf, offset, size);
                }
            } else if (extension.equalsIgnoreCase("wav")) {
                byte[] debugArray = new byte[(int) size];
                buf.get(0, debugArray, 0, (int) size);
                //System.out.println("Got original audio write: " + new String(Hex.encodeHex(debugArray)));
                audioProcessor.packet(buf, offset, size, this.renderer::encodeSamples);
            }
        } catch (Throwable e){
            e.printStackTrace();
            this.errorHandler.completeExceptionally(e);
        }
        return (int) size;
    }

    @Override
    public int write_buf(String path, FuseBufvec buf, @off_t long off, FuseFileInfo fi){
        System.err.println("Got write buf to path: " + path + " with offset: " + off);
        /*try {
            String extension = path.substring(path.length() - 3);
            long writeLength = libFuse.fuse_buf_size(buf);
            if (extension.equalsIgnoreCase("tga")) {
                int index = extractIndex(path);
                addFrame(index, buf, off, writeLength);
            } else if (extension.equalsIgnoreCase("wav")) {
                audioProcessor.packet(buf, off, writeLength, this.renderer::encodeSamples);
            }
            return (int)writeLength;
        }
        catch (Exception e){
            errorHandler.completeExceptionally(e);
        }*/
        return 0;
    }

    private void addFrame(int index, Pointer buf, long offset, long writeLength) {
        int position = index % settings.getFrameblendIndex();

        currentFrame.packet(buf, libFuse, offset, writeLength, weighter, position, index);

        if(offset == this.frameSize - writeLength && position == topFrame){
            System.out.println("Writing media on frame: " + index + " and position " + position);
            this.renderer.encodeFrame(currentFrame, index / settings.getFrameblendIndex());
            currentFrame.reset();
        }
    }

    public Runtime getRuntime(){
        return getContext().getRuntime();
    }

    public void resetStatus(){
        this.latestCreated = "";
    }

}
