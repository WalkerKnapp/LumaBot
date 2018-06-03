package gq.luma.bot.render;

import gq.luma.bot.ClientSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class SourceLogMonitor {
    private static final Logger logger = LoggerFactory.getLogger(SourceLogMonitor.class);

    private String[] monitor;
    private File logFile;
    private int lines;

    public SourceLogMonitor(File log, int lines, String... monitor) throws InterruptedException {
        this.monitor = monitor;
        while (!log.exists()){
            Thread.sleep(100);
            //System.out.println("Log doesn't exist");
        }
        this.logFile = log;
        this.lines = lines;
    }

    public SourceLogMonitor(File log, String... monitor) throws InterruptedException {
        this(log, 15, monitor);
    }

    public CompletableFuture<Void> monitor(){
        return CompletableFuture.runAsync(() -> {
            try {
                while (true) {
                    String lastLines = tail2();
                    for(String mon : monitor){
                        if(lastLines.contains(mon)){
                            logger.debug("---------------Found {}---------------", mon);
                            return;
                        }
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException | IOException e) {
                throw new CompletionException(e);
            }
        }, ClientSocket.executorService);
    }

    private String tail2() throws IOException {
        try(RandomAccessFile log = new RandomAccessFile(logFile,"r" )) {
            long fileLength = log.length() - 1;
            StringBuilder sb = new StringBuilder();
            int line = 0;
            for (long filePointer = fileLength; filePointer != -1; filePointer--) {
                log.seek(filePointer);
                int readByte = log.readByte();

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
            log.close();
            return sb.reverse().toString();
        }
    }
}
