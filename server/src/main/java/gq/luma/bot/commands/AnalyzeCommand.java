package gq.luma.bot.commands;

import com.google.api.services.youtube.model.Video;
import de.btobastian.javacord.entities.message.Message;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.systems.ffprobe.*;
import gq.luma.bot.systems.srcdemo.SrcDemo;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.commands.subsystem.Localization;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.params.io.input.*;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.utils.LumaException;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import gq.luma.bot.utils.embeds.FilteredEmbed;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImageParser;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.common.bytesource.ByteSourceInputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AnalyzeCommand {
    @Command(aliases = {"analyze", "a"}, description = "analyze_desc", usage = "analyze_usage")
    public void onAnalyze(CommandEvent event){
        Localization loc = event.getLocalization();
        CompletableFuture<Message> responseMessage = event.getMessage().getChannel().sendMessage(loc.get("analyze_pending"));

        List<InputStream> openedStreams = new ArrayList<>();

        try {
            FileInput input = ParamUtilities.getInput(event.getMessage(), InputType.IMAGE, InputType.VIDEO, InputType.AUDIO, InputType.DEMO);
            Map<String, String> params = ParamUtilities.getParams(event.getCommandRemainder());
            InputType type = input.getInputType();

            FilteredEmbed embed = new FilteredEmbed()
                .setColor(BotReference.LUMA_COLOR);

            if(type == InputType.IMAGE){
                embed.setThumbnail("https://i.imgur.com/tCCBstl.png")
                    .addFieldTitle(loc.get("media_type"), loc.get("media_type_image"), false);

                InputStream imageStream = input.getStream();
                openedStreams.add(imageStream);
                String extension = input.getExtension();
                long size = input.getSize();
                ImageParser parser = Arrays
                        .stream(ImageParser.getAllImageParsers())
                        .filter(imageParser -> extension.equalsIgnoreCase(imageParser.getDefaultExtension().substring(1)))
                        .findFirst()
                        .orElseThrow(() -> new LumaException(loc.get("analyze_error_parse_image") + " " + extension));
                ImageInfo imageInfo = parser.getImageInfo(new ByteSourceInputStream(imageStream, input.getName()));

                embed.addField(loc.get("delivery_type"), loc.get(input.getInputName()), false)
                        .addField(loc.get("name"), input.getName(), false)
                        .addField(loc.get("size"), size / 1000 + " kb", false)
                        .addFieldTitle(BotReference.ZERO_LENGTH_CHAR, "**__" + loc.get("image_details") + "__**", false)
                        .addField(loc.get("dimensions"), imageInfo.getWidth() + "x" + imageInfo.getHeight(), true)
                        .addField(loc.get("format"), imageInfo.getFormat().getName(), true)
                        .addField(loc.get("color_type"), imageInfo.getColorType().toString(), true)
                        .addField(loc.get("compression_algorithm"), imageInfo.getCompressionAlgorithm().toString(), true)
                        .addField(loc.get("bits_per_pixel"), String.valueOf(imageInfo.getBitsPerPixel()), true)
                        .addField(loc.get("transparent"), String.valueOf(imageInfo.isTransparent()), true)
                        .addField(loc.get("progressive"), String.valueOf(imageInfo.isProgressive()), true)
                        .addField(loc.get("uses_palette"), String.valueOf(imageInfo.usesPalette()), true);

                if(!imageInfo.getComments().isEmpty())
                    embed.addField(loc.get("comments"), String.join("\n", imageInfo.getComments()), false);

                imageStream.close();

            }
            else if(type == InputType.VIDEO){
                embed.setThumbnail("https://i.imgur.com/GzCnShr.png")
                        .addFieldTitle(loc.get("media_type"), loc.get("media_type_video"), false)
                        .addField(loc.get("delivery_type"), input.getInputName(), false)
                        .addField(loc.get("name"), input.getName(), false);

                InputStream stream = input.getStream();
                openedStreams.add(stream);

                embed.addField(loc.get("size"), input.getSize() / 1000 + " kb", false);

                if(input instanceof RawUrlInput || input instanceof AttachmentInput) {
                    FFProbeResult result = FFProbe.analyzeByStream(stream).join();

                    List<FFProbeStream> streams = result.getStreams();

                    embed.addField(loc.get("format"), result.getFormatLongName(), true)
                            .addField(loc.get("duration"), String.valueOf(result.getDuration()), true);

                    StringBuilder sb = new StringBuilder();
                    result.getTags().forEach((s, s2) -> sb.append(s).append(": ").append(s2).append("\n"));
                    if (!result.getTags().isEmpty())
                        embed.addField(loc.get("tags"), sb.toString(), false);

                    for (int i = 0; i < streams.size(); i++) {
                        FFProbeStream ffStream = streams.get(i);
                        embed.addField(BotReference.ZERO_LENGTH_CHAR, "**__" + String.format(loc.get("stream_num"), i + 1) + "__**", false)
                                .addField(loc.get("codec"), ffStream.getCodecLongName(), false)
                                .addField(loc.get("codec_type"), String.valueOf(ffStream.getCodecType().name()), true)
                                .addField(loc.get("bitrate"), String.valueOf(ffStream.getBitRate() / 1000) + " kb/s", true);
                        if (ffStream.getCodecType() == FFProbeCodecType.VIDEO) {
                            FFProbeVideoStream videoStream = ffStream.asVideoStream();
                            //embed.addField(loc.get("pixel_format"), videoStream.getPixelFormat(), true)
                            embed.addField(loc.get("dimensions"), videoStream.getWidth() + "x" + videoStream.getHeight(), true)
                                    .addField(loc.get("framerate"), String.valueOf(videoStream.getDoubleFrameRate()) + " fps", true);
                        } else if (ffStream.getCodecType() == FFProbeCodecType.AUDIO) {
                            FFProbeAudioStream audioStream = ffStream.asAudioStream();
                            embed.addField(loc.get("channels"), String.valueOf(audioStream.getChannels()), true)
                                    .addField(loc.get("sample_rate"), String.valueOf(audioStream.getSampleRate()) + " Hz", true);
                        }
                    }
            }
            else if(input instanceof YoutubeInput){
                System.out.println("Found yt video at url: " + input.getName());
                Video youtubeVideo = ((YoutubeInput)input).getVideo();

                embed.addFieldTitle(BotReference.ZERO_LENGTH_CHAR, "**__" + loc.get("youtube_details") + "__**", false)
                        .addField(loc.get("title"), youtubeVideo.getSnippet().getTitle(), false)
                        .addField(loc.get("uploader"), youtubeVideo.getSnippet().getChannelTitle(), false)
                        .addField(loc.get("views"), String.valueOf(youtubeVideo.getStatistics().getViewCount()), true)
                        .addField(loc.get("likes_dislikes"), youtubeVideo.getStatistics().getLikeCount() + "/" + youtubeVideo.getStatistics().getDislikeCount(), true)
                            .addField(loc.get("tags"), String.join(", ", youtubeVideo.getSnippet().getTags()), false)
                            .addField(loc.get("duration"), youtubeVideo.getContentDetails().getDuration().substring(2).toLowerCase(), true);

                    FFProbeResult probe = FFProbe.analyzeByStream(stream).join();
                    ((YoutubeInput) input).getProcess().destroyForcibly();

                    embed.addFieldTitle(BotReference.ZERO_LENGTH_CHAR, "**__" + loc.get("file_details") + "__**", false)
                            .addField(loc.get("format"), probe.getFormatLongName(), true);

                    List<FFProbeStream> streams = probe.getStreams();

                    for (int i = 0; i < streams.size(); i++) {
                        FFProbeStream ffStream = streams.get(i);
                        embed.addField(BotReference.ZERO_LENGTH_CHAR, "**__" + String.format(loc.get("stream_num"), i + 1) + "__**", false)
                                .addField(loc.get("codec"), ffStream.getCodecLongName(), false)
                                .addField(loc.get("codec_type"), String.valueOf(ffStream.getCodecType().name()), true)
                                .addField(loc.get("bitrate"), String.valueOf(ffStream.getBitRate() / 1000) + " kb/s", true);
                        if (ffStream.getCodecType() == FFProbeCodecType.VIDEO) {
                            FFProbeVideoStream videoStream = ffStream.asVideoStream();
                            //embed.addField(loc.get("pixel_format"), videoStream.getPixelFormat(), true)
                            embed.addField(loc.get("dimensions"), videoStream.getWidth() + "x" + videoStream.getHeight(), true)
                                    .addField(loc.get("framerate"), String.valueOf(videoStream.getDoubleFrameRate()) + " fps", true);
                        } else if (ffStream.getCodecType() == FFProbeCodecType.AUDIO) {
                            FFProbeAudioStream audioStream = ffStream.asAudioStream();
                            embed.addField(loc.get("channels"), String.valueOf(audioStream.getChannels()), true)
                                    .addField(loc.get("sample_rate"), String.valueOf(audioStream.getSampleRate()) + " Hz", true);
                        }
                    }
                }
            }
            else if(type == InputType.AUDIO){

                embed.setThumbnail("https://i.imgur.com/6XiREPx.png")
                        .addFieldTitle(loc.get("media_type"), loc.get("media_type_audio"), false)
                        .addField(loc.get("delivery_type"), loc.get(input.getInputName()), false)
                        .addField(loc.get("name"), input.getName(), true);

                InputStream stream = input.getStream();
                openedStreams.add(stream);
                long size = input.getSize();

                embed.addField(loc.get("size"), size / 1000 + " kb", true);


                try {
                    AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(stream));
                    openedStreams.add(audioStream);
                    AudioFormat format = audioStream.getFormat();

                    embed.addField(loc.get("duration"), String.valueOf(size/(format.getFrameSize() * format.getFrameRate())) + " s", true)
                            .addField(loc.get("format"), input.getExtension(), true)
                            .addField(loc.get("encoding"), format.getEncoding().toString(), true)
                            .addField(loc.get("channels"), String.valueOf(format.getChannels()), true)
                            .addField(loc.get("sample_rate"), String.valueOf(format.getSampleRate()), true)
                            .addField(loc.get("sample_size"), String.valueOf(format.getSampleSizeInBits()), true)
                            .addField(loc.get("endian"), format.isBigEndian() ? loc.get("big_endian") : loc.get("small_endian"), true);

                    if(!format.properties().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        format.properties().forEach((name, object) -> sb.append(name).append(":").append(object).append("\n"));
                        embed.addField(loc.get("properties"), sb.toString(), false);
                    }

                }
                catch (UnsupportedAudioFileException e){
                    responseMessage.join().edit("", EmbedUtilities.getErrorMessage(loc.get("analyze_error_parse_audio") + " " + input.getName(), loc));
                    stream.close();
                    return;
                }
                stream.close();
            }
            else if(type == InputType.DEMO){
                File download = input.download(FileReference.tempDir);
                SrcDemo demo = SrcDemo.of(download);
                if(!download.delete()){
                    System.err.println("Unable to delete file: " + download.getAbsolutePath());
                }

                embed.setThumbnail("https://i.imgur.com/Dsu7JWF.png")
                        .addFieldTitle(loc.get("media_type"), loc.get("media_type_demo"), false)
                        .addField(loc.get("delivery_type"), loc.get(input.getInputName()), true)
                        .addField(loc.get("name"), input.getName(), true)
                        .addFieldTitle(loc.get("game"), demo.getGame().getDirectoryName(), false)
                        .addField(loc.get("map_name"), demo.getMapName(), true)
                        .addField(loc.get("player_name"), demo.getClientName(), true)
                        .addField(loc.get("playback_time"), String.valueOf(demo.getPlaybackTime()), true)
                        .addField(loc.get("playback_ticks"), String.valueOf(demo.getPlaybackTicks()), true);

                if(params.containsKey("v") || params.containsKey("verbose")) {
                    embed.addField(loc.get("demo_branch"), demo.getFilestamp(), true)
                            .addField(loc.get("network_protocol"), String.valueOf(demo.getNetworkProtocol()), true)
                            .addField(loc.get("protocol"), String.valueOf(demo.getProtocol()), true)
                            .addField(loc.get("sign_on_length"), String.valueOf(demo.getSignOnLength()), true);
                }
            }
            EmbedUtilities.sendCompiledEmbedAsEdit(embed, responseMessage.join());
        } catch (IOException | LumaException | ImageReadException | InterruptedException e) {
            e.printStackTrace();
            responseMessage.join().edit("", EmbedUtilities.getErrorMessage(e.getMessage(), loc));
        } finally {
            for(InputStream stream : openedStreams){
                try {
                    stream.close();
                } catch (IOException ignored) { }
            }
        }
    }
}
