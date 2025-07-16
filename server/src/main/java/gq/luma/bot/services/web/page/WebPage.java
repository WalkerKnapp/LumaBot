package gq.luma.bot.services.web.page;

import io.undertow.io.IoCallback;
import io.undertow.server.HttpServerExchange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class WebPage {

    private final ArrayList<String> compositionParts = new ArrayList<>();
    private final ArrayList<String> inserts = new ArrayList<>();

    public WebPage(Path path) throws IOException {
        try (SeekableByteChannel sbc = Files.newByteChannel(path);
             InputStream stream = Channels.newInputStream(sbc)) {
            int c;
            int insertDepth = 0;
            StringBuilder sb = new StringBuilder();
            while ((c = stream.read()) != -1) {
                if(c == '?') {
                    if(insertDepth > 1) {
                        insertDepth = 0;
                        inserts.add(sb.toString());
                        sb = new StringBuilder();
                    } else {
                        insertDepth++;
                        if(insertDepth == 2) {
                            compositionParts.add(sb.toString());
                            sb = new StringBuilder();
                        }
                    }
                } else {
                    if (insertDepth == 1) {
                        insertDepth = 0;
                        sb.append('?');
                    }
                    sb.append((char)c);
                }
            }
            if(sb.length() > 0) {
                compositionParts.add(sb.toString());
            }
        }
    }

    public void serve(HttpServerExchange exchange, String... inserts) {
        System.out.println("Serving Page");
        StringBuilder sb = new StringBuilder();
        if(inserts.length % 2 == 0) {
            for(int i = 0; i < compositionParts.size(); i++) {
                System.out.println("Serving CompPart " + i + " size " + compositionParts.get(i).length());
                sb.append(compositionParts.get(i));
                if(this.inserts.size() > i) {
                    sb.append(searchInsertVal(this.inserts.get(i), inserts));
                }
            }
        } else {
            throw new IllegalArgumentException("Serving webpage with invalid inserts.");
        }
        exchange.getResponseSender().send(sb.toString(), IoCallback.END_EXCHANGE);
    }

    public String searchInsertVal(String insertTag, String... inputInserts) {
        for(int i = 0; i < inputInserts.length; i += 2) {
            if(insertTag.equals(inputInserts[i])) {
                return inputInserts[i + 1];
            }
        }
        return "";
    }
}
