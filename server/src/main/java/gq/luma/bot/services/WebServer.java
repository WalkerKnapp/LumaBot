package gq.luma.bot.services;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;

import gq.luma.bot.Luma;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.utils.StringUtilities;
import gq.luma.bot.utils.WordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class WebServer extends RouterNanoHTTPD implements Service {
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

    public WebServer() {
        super(80);
        addMappings();
    }

    @Override
    public void addMappings(){
        super.addMappings();
        addRoute("/", IndexHandler.class);
        addRoute("/res/:resource", ResourceHandler.class);
        addRoute("/renders/:renderid", RenderHandler.class);
    }

    @Override
    public void startService() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    public static class IndexHandler extends RouterNanoHTTPD.DefaultStreamHandler {
        @Override
        public String getMimeType() {
            return "text/html";
        }

        @Override
        public NanoHTTPD.Response.IStatus getStatus() {
            return NanoHTTPD.Response.Status.OK;
        }

        @Override
        public InputStream getData() {
            File indexFile = new File(FileReference.webRoot, "index.html");
            try{
                return new FileInputStream(indexFile);
            } catch (IOException e){
                logger.error("Encountered an error: ", e);
                return null;
            }
        }
    }

    public static class ResourceHandler extends RouterNanoHTTPD.DefaultHandler {

        @Override
        public String getText() {
            return "Not yet implemented";
        }

        @Override
        public String getMimeType() {
            return "text/html";
        }

        @Override
        public Response get(UriResource uriResource, Map<String, String> urlParams, IHTTPSession session) {
            File resource = new File(FileReference.webRoot, "res/" + urlParams.get("resource"));
            try {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, Files.probeContentType(resource.toPath()), new FileInputStream(resource), resource.length());
            } catch (IOException e) {
                logger.error("Encountered error: ", e);
                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "File not found. :(");
            }
        }

        @Override
        public Response.IStatus getStatus() {
            return null;
        }
    }

    public static class RenderHandler extends RouterNanoHTTPD.DefaultHandler {
        @Override
        public String getMimeType() {
            return "text/html";
        }

        @Override
        public String getText() {
            return "Not implemented.";
        }

        @Override
        public Response get(UriResource uriResource, Map<String, String> urlParams, IHTTPSession session) {
            System.out.println("UriResource: " + uriResource.getUri());
            System.out.println("Url Params: " + urlParams.toString());
            System.out.println("Session: " + session.getHeaders());
            System.out.println("Params: " + session.getMethod());
            System.out.println("Query String: " + session.getQueryParameterString());

            try {
                String extension = StringUtilities.lastOf(urlParams.get("renderid").split("\\."));
                String renderId = urlParams.get("renderid").split("\\.")[0];
                int renderCode = WordEncoder.decode(renderId);
                System.out.println("Decoded id: " + renderCode + " from string " + renderId);
                ResultSet rs = Luma.database.getResult(renderCode);

                if (rs.next()) {
                    if (session.getQueryParameterString() != null && session.getQueryParameterString().equalsIgnoreCase("dl=1")) {
                        if(extension.equalsIgnoreCase("png")){
                            Response response = NanoHTTPD.newFixedLengthResponse(MOVED_TEMPORARILY, "text/plain", "");
                            response.addHeader("Location", Luma.gDrive.getUrlof(rs.getString("thumbnail")));
                            return response;
                        }
                        Response response = NanoHTTPD.newFixedLengthResponse(MOVED_TEMPORARILY, "text/plain", "");
                        response.addHeader("Location", Luma.gDrive.getUrlof(rs.getString("code")));
                        return response;
                    } else {
                        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/html", "");
                        response.addHeader("Location", Luma.gDrive.getViewUrlof(rs.getString("code")));
                        return response;
                        /*File file = new File(FileReference.webRoot, "render-template.html");
                        System.out.println("Sending template: " + file.getName());
                        try (FileInputStream fis = new FileInputStream(file)) {
                            String template = new String(fis.readAllBytes());
                            String fullHtml = template
                                    .replace("{render-name}", rs.getString("name"))
                                    .replace("{render-url}", "/renders/" + renderId)
                                    .replace("{render-raw-link}", "/renders/" + renderId + "?dl=1")
                                    .replace("{render-download-link}", "/renders/" + renderId + "?dl=1")
                                    .replace("{thumbnail-link}", "/renders/" + renderId + ".png?dl=1")
                                    .replace("{render-width}", rs.getString("width"))
                                    .replace("{render-height}", rs.getString("height"))
                                    .replace("{video-type}", "video/mp4");
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html", fullHtml);
                        }*/
                    }
                } else {
                    return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Whoops! We were unable to find this file.");
                }
            } catch (SQLException | IOException e) {
                logger.error("Encountered error: ", e);
                return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Whoops! It looks like something went wrong. Make sure the url is correct and try again.");
            }

            //return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", "Lol");
        }

        @Override
        public Response.IStatus getStatus() {
            return null;
        }
    }

}
