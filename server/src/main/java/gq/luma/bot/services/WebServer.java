package gq.luma.bot.services;

import fi.iki.elonen.NanoHTTPD;

import gq.luma.bot.Luma;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.utils.StringUtilities;
import gq.luma.bot.utils.WordEncoder;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WebServer extends NanoHTTPD implements Service {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
    private static final Response.IStatus MOVED_TEMPORARILY = new Response.IStatus() {
        @Override
        public String getDescription() {
            return "TEMPORARY-REDIRECT";
        }

        @Override
        public int getRequestStatus() {
            return 302;
        }
    };

    private String indexPage;
    private byte[] favicon;

    public WebServer() {
        super(80);
    }

    @Override
    public void startService() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        reload();
    }

    @Override
    public void reload() throws IOException {
        try(FileInputStream fis = new FileInputStream(new File(FileReference.webRoot, "index.html"));
            ByteArrayOutputStream baos = new ByteArrayOutputStream()){
            IOUtils.copy(fis, baos);
            indexPage = new String(baos.toByteArray());
        }
        try(FileInputStream fis = new FileInputStream(new File(FileReference.webRoot, "icon.ico"));
            ByteArrayOutputStream baos = new ByteArrayOutputStream()){
            IOUtils.copy(fis, baos);
            favicon = baos.toByteArray();
        }
    }

    @Override
    public Response serve(IHTTPSession session){
        try {
            String[] tree = session.getUri().split("/");
            //logger.debug("Remote host: {}", session.getRemoteHostName());
            logger.debug("Tree: {}", String.join(",", tree));
            if (tree.length > 1) {
                if (tree[1].equalsIgnoreCase("favicon.ico")) {
                    return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "image/x-ico", new ByteArrayInputStream(favicon), favicon.length);
                }
                if (session.getHeaders().get("host").startsWith("cdn")) {
                    File fileToServe = new File(FileReference.webRoot, "cdn/" + tree[1]);
                    try {
                        FileInputStream fis = new FileInputStream(fileToServe);
                        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, Files.probeContentType(fileToServe.toPath()), fis, fileToServe.length());
                    } catch (FileNotFoundException e) {
                        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Unable to find file");
                    } catch (IOException e) {
                        logger.error("Something went wrong while serving a file: ", e);
                        return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Something went wrong.");
                    }
                } else if (session.getHeaders().get("host").startsWith("render")) {
                    try {
                        String extension = StringUtilities.lastOf(tree[1].split("\\."));
                        String renderId = tree[1].split("\\.")[0];
                        logger.debug("Render code: {}", renderId);
                        int renderCode = WordEncoder.decode(renderId);
                        logger.debug("Decoded id: {}", renderCode);

                        ResultSet rs = Luma.database.getResult(renderCode);
                        if (rs.next()) {
                            String dlCode = rs.getString("code");
                            System.out.println("Found dl code: " + dlCode);
                            if (session.getQueryParameterString() != null && session.getQueryParameterString().equalsIgnoreCase("dl=1")) {
                                if (extension.equalsIgnoreCase("png")) {
                                    return getRedirect(Luma.gDrive.getUrlof(rs.getString("thumbnail")));
                                }
                                return getRedirect(Luma.gDrive.getUrlof(dlCode));
                            } else {
                                return getRedirect(Luma.gDrive.getUrlof(dlCode));
                            }
                        } else {
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Whoops! We were unable to find this file.");
                        }
                    } catch (SQLException | IOException e) {
                        logger.error("Encountered error: ", e);
                        return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Whoops! It looks like something went wrong. Make sure the url is correct and try again.");
                    }
                }
            } else {
                //Serve main
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html", indexPage);
            }
            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "Please retype the URL.");
        } catch (Exception t){
            t.printStackTrace();
            throw t;
        }
    }

    private Response getRedirect(String url){
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        response.addHeader("Location", url);
        return response;
    }

}
