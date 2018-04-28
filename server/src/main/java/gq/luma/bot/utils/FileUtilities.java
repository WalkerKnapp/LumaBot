package gq.luma.bot.utils;

import gq.luma.bot.reference.FileReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileUtilities {

    private static ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);

    public static Collection<File> unzip(File directory, File zipFile) throws IOException {
        Collection<File> files = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            byte[] buffer = new byte[1024];
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                File newOut = new File(directory, zipEntry.getName());
                try (FileOutputStream fos = new FileOutputStream(newOut)){
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                files.add(newOut);
            }

            zis.closeEntry();
        }

        return files;
    }

    public static File zip(Collection<File> files, File zipFile) throws IOException {
        try(FileOutputStream fos = new FileOutputStream(zipFile);
            ZipOutputStream zos = new ZipOutputStream(fos)){
            byte[] buffer = new byte[1024];
            for(File srcFile : files){
                try (FileInputStream fis = new FileInputStream(srcFile)){
                    ZipEntry entry = new ZipEntry(srcFile.getName());
                    zos.putNextEntry(entry);

                    int length;
                    while((length = fis.read(buffer)) >= 0){
                        zos.write(buffer, 0, length);
                    }
                }
            }
        }
        return zipFile;
    }

    public static byte[] longToBytes(long x){
        longBuffer.putLong(0, x);
        return longBuffer.array();
    }

    public static String relatavizePathToTemp(File file){
        return FileReference.tempDir.toURI().relativize(file.toURI()).getPath();
    }
}
