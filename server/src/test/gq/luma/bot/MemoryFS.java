package gq.luma.bot;

import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;

import java.nio.file.Paths;

import static jnr.ffi.Platform.OS.WINDOWS;

public class MemoryFS extends FuseStubFS {
    private String latestCreated = "";

    public static void main(String[] args) {
        MemoryFS memfs = new MemoryFS();
        try {
            String path;
            switch (Platform.getNativePlatform().getOS()) {
                case WINDOWS:
                    path = "F:\\SteamLibrary\\steamapps\\common\\Portal 2\\portal2\\export";
                    break;
                default:
                    path = "/tmp/mntm";
            }
            memfs.mount(Paths.get(path), true, false);
        } finally {
            memfs.umount();
        }
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
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
            //System.out.println("Getattr: " + path + " exists! Returning " + 0);
            return 0;
        } else if(!latestCreated.isEmpty() && path.substring(path.length() - 3).equalsIgnoreCase("wav")){
            stat.st_mode.set(FileStat.S_IFREG | 0777);
            stat.st_size.set(0);
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().pid.get());
            //System.out.println("Getattr: " + path + " exists! Returning " + 0);
            return 0;
        } else  {
            //System.out.println("Getattr: " + path + " does not exist! Returning " + -ErrorCodes.ENOENT());
            return -ErrorCodes.ENOENT();
        }
    }


    @Override
    public int statfs(String path, Statvfs stbuf) {
        if (Platform.getNativePlatform().getOS() == WINDOWS) {
            // statfs needs to be implemented on Windows in order to allow for copying
            // data from other devices because winfsp calculates the volume size based
            // on the statvfs call.
            // see https://github.com/billziss-gh/winfsp/blob/14e6b402fe3360fdebcc78868de8df27622b565f/src/dll/fuse/fuse_intf.c#L654
            if ("/".equals(path)) {
                stbuf.f_blocks.set(1024 * 1024); // total data blocks in file system
                stbuf.f_frsize.set(1024);        // fs block size
                stbuf.f_bfree.set(1024 * 1024);  // free blocks in fs
            }
        }
        return super.statfs(path, stbuf);
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        System.out.println("Wrote: " + path + " size: " + size + " with offset: " + offset);
        return (int) size;
    }
}
