package gq.luma.bot.services.node;

import gq.luma.bot.WebsocketDecoder;
import gq.luma.bot.reference.FileReference;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CdnDecoder extends WebsocketDecoder {

    private static final String URL_STUB = "https://cdn.luma.gq/";

    private FileOutputStream fos;
    private String fileName;

    public CdnDecoder(String fileName) throws IOException {
        super(fileName);
        Path outputFile = Paths.get(FileReference.webRoot.getAbsolutePath(), "cdn", fileName);
        Files.createDirectories(outputFile.getParent());
        this.fos = new FileOutputStream(outputFile.toFile());

        this.fileName = fileName;
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException {
        for(int i = 0; i < buffer.remaining(); i++) {
            this.fos.write(buffer.get());
        }
    }

    @Override
    public String finish() throws IOException {
        this.fos.close();
        return URL_STUB + fileName;
    }
}
