package gq.luma.bot.render.fs;

import com.dokany.java.DokanyDriver;
import com.dokany.java.constants.FileSystemFeature;
import com.dokany.java.constants.MountOption;
import com.dokany.java.structure.DeviceOptions;
import com.dokany.java.structure.EnumIntegerSet;
import com.dokany.java.structure.VolumeInformation;
import gq.luma.bot.render.renderer.FFRenderer;
import gq.luma.bot.RenderSettings;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class FSInterface {

    private DokanyDriver driver;
    private RenderFS renderFS;
    private Path mountPoint;

    private FSInterface(DokanyDriver driver, RenderFS renderFS, Path mountPoint){
        this.driver = driver;
        this.renderFS = renderFS;
        this.mountPoint = mountPoint;
    }

    public RenderFS getRenderFS() {
        return renderFS;
    }

    public Path getMountPoint(){
        return mountPoint;
    }

    @Deprecated
    public static CompletableFuture<FSInterface> openDokany(RenderSettings settings, FFRenderer renderer){
        CompletableFuture<FSInterface> cf = new CompletableFuture<>();
        new Thread(() -> {
            try {
                EnumIntegerSet<MountOption> mountOptions = new EnumIntegerSet<>(MountOption.class);
                mountOptions.add(MountOption.MOUNT_MANAGER, MountOption.DEBUG_MODE, MountOption.STD_ERR_OUTPUT);

                DeviceOptions deviceOptions = new DeviceOptions("N:\\", (short) 1, mountOptions, "", 10000, 4096, 4096);

                EnumIntegerSet<FileSystemFeature> fsFeatures = new EnumIntegerSet<>(FileSystemFeature.class);
                fsFeatures.add(FileSystemFeature.UNICODE_ON_DISK);

                VolumeInformation volumeInfo = new VolumeInformation(VolumeInformation.DEFAULT_MAX_COMPONENT_LENGTH, "NodeDir", 0x234234, "MelissaBotFS", fsFeatures);

                DokanyRenderFS renderFS = new DokanyRenderFS(deviceOptions, volumeInfo, settings, renderer);

                renderFS.getErrorHandler().exceptionally(throwable -> {
                    cf.completeExceptionally(throwable);
                    return null;
                });
                DokanyDriver dokanyDriver = new DokanyDriver(deviceOptions, renderFS);

                cf.complete(new FSInterface(dokanyDriver, renderFS, Paths.get("N:\\")));

                dokanyDriver.start();
            }
            catch (Exception e){
                cf.completeExceptionally(e);
            }

        }).start();
        return cf;
    }

    public void shutdown(){
        if(driver != null){
            driver.shutdown();
        } else {
            renderFS.shutdown();
        }
    }

    public static CompletableFuture<FSInterface> openFuse(Path mountPoint){
        CompletableFuture<FSInterface> cf = new CompletableFuture<>();
        new Thread(() -> {
            try {
                FuseRenderFS fuseRenderFS = new FuseRenderFS();
                //"
                fuseRenderFS.mount(mountPoint, false, false, new String[]{"-o", "splice_read,splice_write,splice_move"});
                cf.complete(new FSInterface(null, fuseRenderFS, mountPoint));
            }
            catch (Exception e){
                cf.completeExceptionally(e);
            }
        }).start();
        return cf;
    }
}
