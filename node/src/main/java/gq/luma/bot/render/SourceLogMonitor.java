package gq.luma.bot.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class SourceLogMonitor {
    private static final Logger logger = LoggerFactory.getLogger(SourceLogMonitor.class);

    private SourceLogMonitor(){
        //Unused
    }

    public static CompletableFuture<Void> monitor(String monitor, File log){
        return monitor(monitor, log, 15);
    }

    public static CompletableFuture<Void> monitor(String monitor, File log, int lines){
        return CompletableFuture.runAsync(() -> {
            try {
                while (!log.exists() || !tail2(log, lines).contains(monitor)) {
                    Thread.sleep(100);
                }
                logger.debug("---------------Found {}---------------", monitor);
            } catch (InterruptedException | IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    private static String tail2(File file, int lines) throws IOException {
        try(RandomAccessFile fileHandler = new RandomAccessFile( file, "r" )) {
            long fileLength = fileHandler.length() - 1;
            StringBuilder sb = new StringBuilder();
            int line = 0;

            for (long filePointer = fileLength; filePointer != -1; filePointer--) {
                fileHandler.seek(filePointer);
                int readByte = fileHandler.readByte();

                if (readByte == 0xA) {
                    if (filePointer < fileLength) {
                        line = line + 1;
                    }
                } else if (readByte == 0xD && (filePointer < fileLength - 1)) {
                    line = line + 1;
                }
                if (line >= lines) {
                    break;
                }
                sb.append((char) readByte);
            }
            fileHandler.close();
            return sb.reverse().toString();
        }
    }
}
