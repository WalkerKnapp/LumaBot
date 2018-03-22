package gq.luma.bot.commands;

import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import gq.luma.bot.reference.BotReference;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.commands.subsystem.MCommand;
import gq.luma.bot.utils.embeds.EmbedUtilities;

import java.sql.SQLException;
import java.time.temporal.ChronoField;

public class InfoCommand {
    @Command(aliases = {"info"}, description = "info_description", usage = "")
    public EmbedBuilder onInfoCommand(CommandEvent event){
        return new EmbedBuilder()
                .addField(event.getLocalization().get("info_who_topic"), event.getLocalization().get("info_who_content"), true)
                .addField(event.getLocalization().get("info_author_topic"), event.getLocalization().get("info_author_content"), true)
                .addField(event.getLocalization().get("info_support_topic"), event.getLocalization().get("info_support_content"), true)
                .setThumbnail(event.getApi().getYourself().getAvatar().getUrl().toString())
                .setColor(BotReference.LUMA_COLOR);
    }

    @Command(aliases = {"ping"}, description = "ping_description", usage = "")
    public EmbedBuilder onPing(CommandEvent event){
        return new EmbedBuilder()
            .addField(event.getLocalization().get("ping_response"),event.getMessage().getCreationTimestamp().minusMillis(System.currentTimeMillis()).get(ChronoField.NANO_OF_SECOND)/1000000 + " ms", true)
            .setColor(BotReference.LUMA_COLOR);
    }

    @Command(aliases = {"help"}, description = "help_description", usage = "")
    public EmbedBuilder onHelp(CommandEvent event){
        try {
            if(event.getCommandArgs().length == 0) {
                EmbedBuilder eb = new EmbedBuilder().setColor(BotReference.LUMA_COLOR).setTitle(event.getLocalization().get("help_title"));
                for (MCommand command : event.getExecutor().getCommands()) {
                    if(event.getExecutor().canRun(command, event.getAuthor(), event.getServer())) {
                        eb.addField(event.getExecutor().generateCommandString(command, event.getChannel(), event.getLocalization(), command.getCommand().aliases()[0]), event.getLocalization().get(command.getCommand().description()), false);
                    }
                }
                return eb;
            } else {
                if(event.getCommandArgs()[0].equalsIgnoreCase("render")){
                    return new EmbedBuilder()
                            .setColor(BotReference.LUMA_COLOR)
                            .setTitle("?l render")
                            .addField("Syntax", "?l [r/render] {options}", false)
                            .addField("Uploading a demo", "This bot supports the formats `dem` and `zip` right now, with `rar` support coming in the future.\n\n" +
                                    "You can upload a demo in various ways:\n" +
                                    "-Attach a file via discord.\n" +
                                    "-Link directly to a demo or zip file\n" +
                                    "-Link to an indirect source such as board.iverb.me.\n\n" +
                                    "The bot will search the past 100 messages, starting with the request message, for any of these.\n", false)
                            .addField("Options", "`-preset`: Sets a preset. Specific options can be overwritten by other options.\n" +
                                    "`-resolution`: Sets the resolution. Supports the format `widthxheight` or `heightp`\n" +
                                    "`-width` and `-height`: Same as the previous but more specific.\n" +
                                    "`-fps`: Sets the fps of the final video to export.\n" +
                                    "`-frameblend`: Sets the frameblend amount. Use `1` for no frameblend, otherwise it's not recommended to use under `4`. Default is `1`.\n" +
                                    "`-startodd`: Determines if a demo is started on an odd or even tick. Default is `true`.\n" +
                                    "`-hq`: When set to `true`, the bot will use the settings found at `https://i1.theportalwiki.net/img/e/e6/Portal_2_walkthrough_config.cfg`. Default is `false`.\n" +
                                    "`-interpolate`: Sets the state of the `demo_interpolateview` cvar. Default is `true`.\n" +
                                    "`-oob`: Gets oob vision for all demos. Default is `false`.\n" +
                                    "`-format`: Sets the output format. Default is `h264`.\n" +
                                    "`-coalesce`: When used on a compressed file, it renders all demos into one video.", false)
                            .addField("Presets", "`Dirt`: 480p, 30 fps, No Frameblend, No HQ. Expect about realtime performance.\n" +
                                    "`Low`: 720p, 30fps, No Frameblend, No HQ. Expect about 2x slower performance.\n" +
                                    "`Medium`: 1080p, 60fps, No Frameblend, Yes HQ. Expect about 4.5x slower performance.\n" +
                                    "`High`: 1080p, 60fps, 8x Frameblend, Yes HQ. Expect about 36x slower performance.\n" +
                                    "`Insane`: 1080p, 60fps, 32x Frameblend, Yes HQ. Expect about 144x slower performance.", false)
                            .addField("Formats", "`H264`\n`DNXHD`\n`HuffyYUV`\n`Raw`\n", false)
                            .addField("Example", "?l render -preset medium -fps 120 -format dnxhd", false);
                }
            }
        } catch (SQLException e){
            return EmbedUtilities.getErrorMessage(e.getMessage(), event.getLocalization());
        }
        return null;
    }

    @Command(aliases = {"invite"}, description = "invite_description", usage = "")
    public EmbedBuilder onInvite(CommandEvent event){
        return new EmbedBuilder()
                .setColor(BotReference.LUMA_COLOR)
                .addField(event.getLocalization().get("invite_title"), "https://discordapp.com/oauth2/authorize?client_id=396497193915383808&scope=bot&permissions=0", false);
    }
}
