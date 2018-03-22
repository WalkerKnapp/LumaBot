package gq.luma.bot.systems;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;

public class GDrive {

    private static Drive drive;
    private static HttpTransport transport;

    static {
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = GoogleCredential.fromStream(GDrive.class.getResourceAsStream("/LumaBot-449002735b83.json")).createScoped(DriveScopes.all());
            drive = new Drive.Builder(transport, JacksonFactory.getDefaultInstance(), credential).setApplicationName("LumaBot").build();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    public static String getUrlof(String code) throws IOException {
        File file = drive.files().get(code).setFields("*").execute();
        String privateUrl = file.getWebContentLink();

        //Queries the url
        URLConnection connection = new URL(privateUrl).openConnection();
        if(connection.getContentType().split(";")[0].equalsIgnoreCase("text/html")){
            //Google has given the "Cant scan this file for viruses" page.
            String webpage = new String(connection.getInputStream().readAllBytes());
            Document webDoc = Jsoup.parse(webpage);
            Element dlLink = webDoc.select("#uc-download-link").first();
            privateUrl = "https://drive.google.com" + dlLink.attr("href");
        }

        System.out.println(privateUrl);
        return privateUrl;
    }

    public static String getViewUrlof(String code) throws IOException {
        File file = drive.files().get(code).setFields("*").execute();
        System.out.println("Grabbed view url of: " + file.getWebViewLink());
        return file.getWebViewLink();
    }

    public static void main(String[] args) throws IOException {

    }

}
