package gq.luma.bot.commands.params.io.input;

import com.google.api.services.youtube.model.Video;
import gq.luma.bot.Luma;
import gq.luma.bot.services.YoutubeApi;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.utils.LumaException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class YoutubeInput implements FileInput {

    private URL url;
    private Process process;

    public YoutubeInput(String url) throws MalformedURLException {
        this.url = new URL(url);
    }

    public Video getVideo() throws IOException, LumaException {
        return Luma.youtubeApi.getVideo(Luma.youtubeApi.getIDFromUrl(url));
    }

    public Process getProcess() {
        return process;
    }

    @Override
    public String getInputName() {
        return "delivery_type_youtube";
    }

    @Override
    public String getName() throws IOException, LumaException {
        Video video = Luma.youtubeApi.getVideo(Luma.youtubeApi.getIDFromUrl(url));
        return video.getSnippet().getTitle() + "-" + video.getId() + ".mp4";
    }

    @Override
    public InputType getInputType() {
        return InputType.VIDEO;
    }

    @Override
    public File download(File destination) throws IOException, InterruptedException, LumaException {
        ProcessBuilder pb = new ProcessBuilder(FileReference.youtubeDL.getAbsolutePath(), url.toString(), "--no-check-certificate");
        pb.directory(destination);
        Process p = pb.start();
        p.waitFor();

        return new File(destination, getName());
    }

    @Override
    public long getSize() throws IOException, LumaException {
        return getVideo().size();
    }

    @Override
    public InputStream getStream() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(FileReference.youtubeDL.getAbsolutePath(), "-o", "-", url.toString(), "--no-check-certificate");
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        Process p = pb.start();
        process = p;

        return p.getInputStream();
    }

}
