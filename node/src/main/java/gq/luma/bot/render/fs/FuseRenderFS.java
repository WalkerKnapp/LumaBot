package gq.luma.bot.render.fs;

import gq.luma.bot.LumaException;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.RenderSettings;
import gq.luma.bot.render.audio.AudioProcessor;
import gq.luma.bot.render.audio.BufferedAudioProcessor;
import jnr.constants.platform.OpenFlags;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.Struct.UnsignedLong;
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


    private final long directIOOffset = Runtime.getSystemRuntime().longSize() + 32;
    private final long uid;
    private final long pid;

    private CompletableFuture<Void> errorHandler;
    private FFRenderer renderer;

    private String latestCreated = "";

    public FuseRenderFS(){
        this.uid = getContext().uid.get();
        this.pid = getContext().pid.get();
    }

    @Override
    public void configure(RenderSettings settings, FFRenderer renderer){
        this.errorHandler = new CompletableFuture<>();
        this.errorHandler.exceptionally(t -> {
            t.printStackTrace();
            return null;
        });

        this.renderer = renderer;
    }

    @Override
    public CompletableFuture<Void> getErrorHandler() {
        return this.errorHandler;
    }

    @Override
    public void waitToFinish() throws IOException, InterruptedException, LumaException {
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

        //Struct.getMemory(fi).putByte(directIOOffset, (byte) 1);
        fi.fh.set(Integer.MAX_VALUE);
        //System.out.println(path + "=" + OpenFlags.valueOf(fi.flags.get()).toString());

        return 0;
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        //System.err.println("FILE WAS CREATED::: " + path);
        this.latestCreated = path;

        fi.flags.set(fi.flags.get() | OpenFlags.O_ASYNC.intValue());

        char[] pathChars = path.toCharArray();
        int length = pathChars.length;
        if ((pathChars[length - 1] == 'A' || pathChars[length - 1] == 'a') &&
                (pathChars[length - 2] == 'G' || pathChars[length - 2] == 'g') &&
                (pathChars[length - 3] == 'T' || pathChars[length - 3] == 't')) {
            fi.fh.set(extractIndex(pathChars));
        } else if ((pathChars[length - 1] == 'V' || pathChars[length - 1] == 'v') &&
                (pathChars[length - 2] == 'A' || pathChars[length - 2] == 'a') &&
                (pathChars[length - 3] == 'W' || pathChars[length - 3] == 'w')) {
            fi.fh.set(Integer.MAX_VALUE);
        }
        //Struct.getMemory(fi).putByte(directIOOffset, (byte) 1);
        //System.out.println(path + "=" + OpenFlags.valueOf(fi.flags.get()).toString());
        return 0;
    }


    @Override
    public int getattr(String path, FileStat stat) {
        char[] pathChars = path.toCharArray();
        int length = pathChars.length;
        if(path.equals("/")){
            stat.st_mode.set(FileStat.S_IFDIR | 0777);
            //stat.st_uid.set(uid);
            //stat.st_gid.set(pid);
            return 0;
        } else if(path.equals(latestCreated)){
            //FileStat.S_IFREG | 0777
            stat.st_mode.set(FileStat.S_IXUGO);
            //stat.st_size.set(0);
            //stat.st_uid.set(uid);
            //stat.st_gid.set(pid);
            //System.out.println("Getattr: " + path + " exists! Returning 0 due to equalling last created");
            return 0;
        } else if(!latestCreated.isEmpty() && (length > 2 && (pathChars[length - 1] == 'V' || pathChars[length - 1] == 'v') &&
                (pathChars[length - 2] == 'A' || pathChars[length - 2] == 'a') &&
                (pathChars[length - 3] == 'W' || pathChars[length - 3] == 'w'))){
            stat.st_mode.set(FileStat.S_IXUGO);
            //`stat.st_size.set(0);
            //stat.st_uid.set(uid);
            //stat.st_gid.set(pid);
            //stat.st_blksize.set(4096);
            //System.out.println("Getattr: " + path + " exists! Returning 0 due to lastcreated being present and audio");
            return 0;
        } else  {
            //System.out.println("Getattr: " + path + " does not exist! Returning " + -ErrorCodes.ENOENT());
            return -ErrorCodes.ENOENT();
        }
    }


    /*@Override
    public int statfs(String path, Statvfs stbuf) {
        if (Platform.getNativePlatform().getOS() == WINDOWS) {
            if ("/".equals(path)) {
                stbuf.f_blocks.set(1024 * 1024); // total data blocks in file system
                stbuf.f_frsize.set(1024);        // fs block size
                stbuf.f_bfree.set(1024 * 1024);  // free blocks in fs
            }
        }
        return super.statfs(path, stbuf);
    }*/

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi){
        /*if(offset == 0){
            byte[] header = new byte[44];
            buf.get(0, header, 0, 44);
            System.out.println("Header=" + new String(Hex.encodeHex(header)));
        }
        System.out.println("Write=" + path + ",Size=" + size + ",fh=" + fi.fh.get() + ",flags=" + fi.flags.get());*/
        try {
            long fh = fi.fh.get();
            if (fh == Integer.MAX_VALUE) {
                System.out.println("Handling audio data...");
                renderer.handleAudioData(buf, offset, size);
            } else {
                int index = (int)fh;
                if(renderer.checkFrame(index)) {
                    renderer.handleVideoData(index, buf, offset, size);
                }
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
        return 0;
    }

    public Runtime getRuntime(){
        return getContext().getRuntime();
    }

    public void resetStatus(){
        this.latestCreated = "";
    }

}
