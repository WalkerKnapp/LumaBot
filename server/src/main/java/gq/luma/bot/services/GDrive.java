package gq.luma.bot.services;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class GDrive implements Service {
    private static final Logger logger = LoggerFactory.getLogger(GDrive.class);

    private Drive drive;

    @Override
    public void startService() throws Exception {
        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        try(FileInputStream fis = new FileInputStream(KeyReference.gdriveServiceAcc)) {
            Credential credential = GoogleCredential.fromStream(fis).createScoped(DriveScopes.all());
            drive = new Drive.Builder(transport, JacksonFactory.getDefaultInstance(), credential).setApplicationName("LumaBot").build();
        }
    }

    public String getUrlof(String code) throws IOException {
        File file = drive.files().get(code).setFields("*").execute();
        String privateUrl = file.getWebViewLink();

        //Queries the url
        /*URLConnection connection = new URL(privateUrl).openConnection();
        if(connection.getContentType().split(";")[0].equalsIgnoreCase("text/html")){
            //Google has given the "Cant scan this file for viruses" page.
            String webpage = new String(connection.getInputStream().readAllBytes());
            Document webDoc = Jsoup.parse(webpage);
            Element dlLink = webDoc.select("#uc-download-link").first();
            privateUrl = "https://drive.google.com" + dlLink.attr("href");
        }*/

        logger.debug(privateUrl);
        return privateUrl;
    }

    public String getFallbackUrlOf(String code){
        return "https://drive.google.com/open?id=" + code;
    }

    public String getViewUrlof(String code) throws IOException {
        File file = drive.files().get(code).setFields("*").execute();
        logger.debug("Grabbed view url of: " + file.getWebViewLink());
        return file.getWebViewLink();
    }
}
