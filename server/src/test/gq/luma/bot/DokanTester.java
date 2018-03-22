package gq.luma.bot;

import com.dokany.java.DokanyDriver;
import com.dokany.java.DokanyFileSystem;
import com.dokany.java.Win32FindStreamData;
import com.dokany.java.constants.FileAttribute;
import com.dokany.java.constants.FileSystemFeature;
import com.dokany.java.constants.MountOption;
import com.dokany.java.structure.*;
import com.sun.jna.platform.win32.WinBase;

import java.io.IOException;
import java.util.Date;
import java.util.Set;

public class DokanTester extends DokanyFileSystem {

    public DokanTester(DeviceOptions deviceOptions, VolumeInformation volumeInfo, FreeSpace freeSpace, Date rootCreationDate, String rootPath) {
        super(deviceOptions, volumeInfo, freeSpace, rootCreationDate, rootPath);
    }

    public static void main(String[] args){
        EnumIntegerSet<MountOption> mountOptions = new EnumIntegerSet<>(MountOption.class);
        mountOptions.add(MountOption.MOUNT_MANAGER);

        DeviceOptions deviceOptions = new DeviceOptions("N:\\", (short) 1, mountOptions, "", 10000, 4096, 4096);

        EnumIntegerSet<FileSystemFeature> fsFeatures = new EnumIntegerSet<>(FileSystemFeature.class);
        fsFeatures.add(FileSystemFeature.UNICODE_ON_DISK);

        VolumeInformation volumeInfo = new VolumeInformation(VolumeInformation.DEFAULT_MAX_COMPONENT_LENGTH, "NodeDir", 0x234234, "MelissaBotFS", fsFeatures);

        DokanTester tester = new DokanTester(deviceOptions, volumeInfo,  new FreeSpace(1024L * 1024L * 4096L, 1024L * 1024L), new Date(), "/");
        DokanyDriver dokanyDriver = new DokanyDriver(deviceOptions, tester);
        dokanyDriver.start();
    }

    @Override
    public void mounted() throws IOException {

    }

    @Override
    public void unmounted() throws IOException {

    }

    @Override
    public boolean doesPathExist(String path) throws IOException {
        return false;
    }

    @Override
    public Set<WinBase.WIN32_FIND_DATA> findFilesWithPattern(String pathToSearch, DokanyFileInfo dokanyFileInfo, String pattern) throws IOException {
        return null;
    }

    @Override
    public Set<Win32FindStreamData> findStreams(String pathToSearch) throws IOException {
        return null;
    }

    @Override
    public void unlock(String path, int offset, int length) throws IOException {

    }

    @Override
    public void lock(String path, int offset, int length) throws IOException {

    }

    @Override
    public void move(String oldPath, String newPath, boolean replaceIfExisting) throws IOException {

    }

    @Override
    public void deleteFile(String path, DokanyFileInfo dokanyFileInfo) throws IOException {

    }

    @Override
    public void deleteDirectory(String path, DokanyFileInfo dokanyFileInfo) throws IOException {

    }

    @Override
    public FileData read(String path, int offset, int readLength) throws IOException {
        return null;
    }

    @Override
    public int write(String path, int offset, byte[] data, int writeLength) throws IOException {
        return 0;
    }

    @Override
    public void createEmptyFile(String path, long options, EnumIntegerSet<FileAttribute> attributes) throws IOException {

    }

    @Override
    public void createEmptyDirectory(String path, long options, EnumIntegerSet<FileAttribute> attributes) throws IOException {

    }

    @Override
    public void flushFileBuffers(String path) throws IOException {

    }

    @Override
    public void cleanup(String path, DokanyFileInfo dokanyFileInfo) throws IOException {

    }

    @Override
    public void close(String path, DokanyFileInfo dokanyFileInfo) throws IOException {

    }

    @Override
    public int getSecurity(String path, int kind, byte[] out) throws IOException {
        return 0;
    }

    @Override
    public void setSecurity(String path, int kind, byte[] data) throws IOException {

    }

    @Override
    public long truncate(String path) throws IOException {
        return 0;
    }

    @Override
    public void setAllocationSize(String path, int length) throws IOException {

    }

    @Override
    public void setEndOfFile(String path, int offset) throws IOException {

    }

    @Override
    public void setAttributes(String path, EnumIntegerSet<FileAttribute> attributes) throws IOException {

    }

    @Override
    public FullFileInfo getInfo(String path) throws IOException {
        return null;
    }

    @Override
    public void setTime(String path, WinBase.FILETIME creation, WinBase.FILETIME lastAccess, WinBase.FILETIME lastModification) throws IOException {

    }
}
