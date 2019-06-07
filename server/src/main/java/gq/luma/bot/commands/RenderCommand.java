package gq.luma.bot.commands;

import com.eclipsesource.json.JsonObject;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.systems.srcdemo.SrcDemo;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.commands.subsystem.Localization;
import gq.luma.bot.systems.demorender.CoalescedSrcDemoRenderTask;
import gq.luma.bot.RenderSettings;
import gq.luma.bot.systems.demorender.SingleSrcRenderTask;
import gq.luma.bot.commands.params.ParamUtilities;
import gq.luma.bot.commands.params.io.input.FileInput;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.commands.params.io.output.WebserverOutputter;
import gq.luma.bot.services.TaskScheduler;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.utils.AlphanumComparator;
import gq.luma.bot.utils.FileUtilities;
import gq.luma.bot.LumaException;
import gq.luma.bot.utils.embeds.EmbedUtilities;
import org.apache.commons.io.FilenameUtils;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RenderCommand {

    private static Set<PosixFilePermission> perms = new HashSet<>();

    static {
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);

        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_WRITE);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);

        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
    }

    @Command(aliases = {"render", "r"}, description = "render_description", usage = "render_usage")
    public void onRender(CommandEvent event){
        Localization loc = event.getLocalization();
        CompletableFuture<Message> responseFuture = event.getChannel().sendMessage(loc.get("render_pending"));
        User author = event.getAuthor();

        Map<String, String> params = ParamUtilities.getParams(event.getCommandRemainder());

        try {
            FileInput input = ParamUtilities.getFirstInput(event.getMessage(), InputType.DEMO, InputType.COMPRESSED);
            InputType type = input.getInputType();

            long workingDir = System.currentTimeMillis();

            File renderBaseDir = new File(FileReference.tempDir, "demo-" + workingDir);
            if (!renderBaseDir.mkdir())
                throw new LumaException(loc.get("temp_dir_failure"));

            File dlFile = input.download(renderBaseDir);

            String operSys = System.getProperty("os.name").toLowerCase();
            if(operSys.contains("nux") || operSys.contains("nix")) {
                Files.setPosixFilePermissions(renderBaseDir.toPath(), perms);
                Files.setPosixFilePermissions(dlFile.toPath(), perms);
            }


            if (type == InputType.DEMO) {
                RenderSettings settings = RenderSettings.parse(params);
                TaskScheduler.scheduleTask(new SingleSrcRenderTask(author.getId(), SrcDemo.of(dlFile, true), settings, dlFile, renderBaseDir))
                        .thenAccept(jsonObject -> {
                            try {
                                String link;
                                if(jsonObject.getBoolean("no-upload", false)){
                                    link = "(No Upload enabled.)";
                                } else {
                                    link = new WebserverOutputter().uploadFile(jsonObject, workingDir, input.getName(), author.getId(), settings.getWidth(), settings.getHeight());
                                }
                                responseFuture.join().edit("",
                                        new EmbedBuilder()
                                                .setColor(BotReference.LUMA_COLOR)
                                                .setFooter(String.format(loc.get("render_finished_footer"), author.getName() + "#" + author.getDiscriminator()))
                                                .setTitle(String.format(loc.get("render_finished_title"), input.getName()))
                                                .setDescription(link));
                                author.sendMessage(String.format(loc.get("render_finished_pm"), input.getName(), link));
                            } catch (SQLException | IOException | LumaException e) {
                                responseFuture.join().edit("", EmbedUtilities.getErrorMessage(e.getMessage(), loc));
                                e.printStackTrace();
                            }
                        })
                        .exceptionally(throwable -> {
                            responseFuture.join().edit("", EmbedUtilities.getErrorMessage(throwable.getMessage(), loc));
                            throwable.printStackTrace();
                            return null;
                        });
            } else if (type == InputType.COMPRESSED) {
                if(params.containsKey("coalesce")){
                    RenderSettings settings = RenderSettings.parse(params);
                    Collection<File> unsortedFiles = FileUtilities.unzip(renderBaseDir, dlFile);
                    List<File> demoFiles = unsortedFiles.stream().sorted(AlphanumComparator.FILE_COMPARATOR).collect(Collectors.toList());
                    List<SrcDemo> srcDemos = new ArrayList<>();
                    for(File f : demoFiles){
                        srcDemos.add(SrcDemo.of(f, true));
                    }

                    TaskScheduler.scheduleTask(
                            new CoalescedSrcDemoRenderTask(
                                    FilenameUtils.removeExtension(dlFile.getName()),
                                    author.getId(),
                                    renderBaseDir,
                                    settings,
                                    srcDemos))
                            .thenAccept(file -> {
                                try {
                                    responseFuture.join().edit("", EmbedUtilities.getSuccessMessage(new WebserverOutputter().uploadFile(file, workingDir, input.getName(), author.getId(), settings.getWidth(), settings.getHeight()), loc));
                                } catch (IOException | LumaException | SQLException e) {
                                    responseFuture.join().edit("", EmbedUtilities.getErrorMessage(e.getMessage(), loc));
                                    e.printStackTrace();
                                }
                            })
                            .exceptionally(t -> {
                                responseFuture.join().edit("", EmbedUtilities.getErrorMessage(t.getMessage(), loc));
                                t.printStackTrace();
                                return null;
                            });
                } else {
                    Collection<CompletableFuture<JsonObject>> futures = new ArrayList<>();
                    RenderSettings settings = RenderSettings.parse(params);
                    for(File file : FileUtilities.unzip(renderBaseDir, dlFile)){
                        futures.add(TaskScheduler.scheduleTask(new SingleSrcRenderTask(author.getId(), SrcDemo.of(file, true), settings, file, renderBaseDir)));
                    }
                    /*CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
                            .thenAccept(v -> {
                                Collection<JsonObject> finalFiles = new ArrayList<>();
                                futures.forEach(cf -> finalFiles.add(cf.join()));
                                try {
                                    File finalFile = FileUtilities.zip(, new File(FilenameUtils.removeExtension(dlFile.getName()) + ".zip"));
                                    String finalUrl = new WebserverOutputter().uploadFile(finalFile, workingDir, input.getName(), author.getId(), settings.getWidth(), settings.getHeight());
                                    responseFuture.join().edit("", EmbedUtilities.getSuccessMessage(finalUrl, event.getLocalization()));
                                } catch (IOException | LumaException | SQLException e){
                                    responseFuture.join().edit("", EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization()));
                                    e.printStackTrace();
                                }
                            }).exceptionally(throwable -> {
                        responseFuture.join().edit("", EmbedUtilities.getErrorMessage(throwable.getMessage(), event.getLocalization()));
                        throwable.printStackTrace();
                        return null;
                    });*/
                }
            }
            responseFuture.join().edit("", new EmbedBuilder()
                    .setColor(BotReference.LUMA_COLOR)
                    .addField(loc.get("render_queued_title"), loc.get("render_queued_message"), false));
        } catch (LumaException | IOException | InterruptedException e) {
            e.printStackTrace();
            responseFuture.join().edit("", EmbedUtilities.getErrorMessage(e.getMessage(), loc));
        }
    }

    @Command(aliases = {"queue", "q"}, description = "queue_description", usage = "")
    public EmbedBuilder onQueue(CommandEvent event){
        return TaskScheduler.generateStatusEmbed(event.getLocalization(), event.getApi());
    }

    /*@Command(aliases = {"cancel"}, description = "cancel_description", usage = "", neededGuildPerms = "cancel")
    public EmbedBuilder onCancel(CommandEvent event){
        if(event.getCommandArgs().length > 0){
            if(isInteger(event.getCommandArgs()[0])){
                Task task = getElementAtIndex(TaskScheduler.getTotalQueue(), Integer.parseInt(event.getCommandArgs()[0]));
                if(task == null){
                    return EmbedUtilities.getErrorMessage(event.getLocalization().get("cancel_out_of_range"), event.getLocalization());
                }
                TaskScheduler.getTotalQueue().remove(task);
                if(Integer.parseInt(event.getCommandArgs()[0]) == 1){
                    task.cancel();
                }
                return EmbedUtilities.getSuccessMessage(event.getLocalization().get("cancel_success_message"), event.getLocalization());
            }
            else{
                return EmbedUtilities.getErrorMessage(event.getLocalization().get("cancel_no_index_message"), event.getLocalization());
            }
        }
        else {
            return EmbedUtilities.getErrorMessage(event.getLocalization().get("cancel_no_index_message"), event.getLocalization());
        }
    }*/

    private static <T> T getElementAtIndex(Queue<T> queue, int check){
        int i = 0;
        for(T t : queue){
            i++;
            if(i == check){
                return t;
            }
        }
        return null;
    }

    private static boolean isInteger(String s) {
        return isInteger(s,10);
    }

    private static boolean isInteger(String s, int radix) {
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(i == 0 && s.charAt(i) == '-') {
                if(s.length() == 1) return false;
                else continue;
            }
            if(Character.digit(s.charAt(i),radix) < 0) return false;
        }
        return true;
    }
}
