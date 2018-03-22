package gq.luma.bot.uploader;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.*;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Permission;

import java.io.*;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.text.NumberFormat;

public class GoogleDriveUploader implements Uploader {

    private static final Permission anyoneReadPerm = new Permission().setType("anyone").setRole("reader").setAllowFileDiscovery(true);

    private Drive drive;

    public GoogleDriveUploader(String credentialString){
        try {
            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = GoogleCredential.fromStream(new ByteArrayInputStream(credentialString.getBytes())).createScoped(DriveScopes.all());
            drive = new Drive.Builder(transport, JacksonFactory.getDefaultInstance(), credential).setApplicationName("LumaBot").build();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getType() {
        return "gdrive";
    }

    @Override
    public String uploadFile(File file) throws IOException {
        String mimeType = Files.probeContentType(file.toPath());
        System.out.println("Found mime type: " + mimeType + " for path: " + file.toPath());

        try(BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            return uploadInputStream(bis, file.getName(), mimeType, file.length());
        }
    }

    public String uploadInputStream(InputStream inputStream, String name, String mimeType, long size) throws IOException {
        InputStreamContent streamContent = new InputStreamContent(mimeType, inputStream);
        streamContent.setLength(size);

        com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File();
        metadata.setName(name);
        metadata.setMimeType(mimeType);

        Drive.Files.Create upload = drive.files().create(metadata, streamContent).setFields("id");

        //upload.getMediaHttpUploader().setDirectUploadEnabled(false);
        //upload.getMediaHttpUploader().setChunkSize(10 * MediaHttpUploader.MINIMUM_CHUNK_SIZE);
        upload.getMediaHttpUploader().setProgressListener(p -> {
            switch (p.getUploadState()){
                case INITIATION_STARTED:
                    System.out.println("Initialization has started");
                    break;
                case INITIATION_COMPLETE:
                    System.out.println("Upload Initialization is complete");
                    break;
                case MEDIA_IN_PROGRESS:
                    System.out.println("Upload isa in progress: " + NumberFormat.getPercentInstance().format(p.getProgress()));
                    break;
                case MEDIA_COMPLETE:
                    System.out.println("Upload is complete!");
                    break;
            }
        });

        com.google.api.services.drive.model.File finalFile = upload.execute();
        drive.permissions().create(finalFile.getId(), anyoneReadPerm).setFields("id").execute();

        finalFile.setWebViewLink("https://drive.google.com/open?id=" + finalFile.getId());
        finalFile.setWebContentLink("https://drive.google.com/uc?export=download&id=" + finalFile.getId());
        System.out.println("Access: " + finalFile.getWebViewLink());
        System.out.println("Content: " + finalFile.getWebContentLink());
        return finalFile.getId();
    }
}
