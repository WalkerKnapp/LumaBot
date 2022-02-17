package gq.luma.bot.commands;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.Command;
import gq.luma.bot.commands.subsystem.CommandEvent;
import gq.luma.bot.services.Database;
import gq.luma.bot.services.SkillRoleService;
import gq.luma.bot.utils.embeds.EmbedUtilities;

public class RoleCommands {
    @Command(aliases = {"assign"}, description = "assign_description", usage = "", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public void onAssign(CommandEvent event) {
        if(event.getCommandArgs().length >= 2){
            String user = event.getCommandArgs()[0];
            String roleName = event.getCommandArgs()[1];
            event.getServer().flatMap(server -> server.getRolesByName(roleName).stream().findFirst())
                    .ifPresentOrElse(role -> {
                        if (event.getMessage().getMentionedUsers().size() > 0) {
                            Luma.database.assignRole(role.getId(), 146404426746167296L, event.getMessage().getMentionedUsers().get(0).getId());
                            Luma.skillRoleService.onScoreUpdate(event.getMessage().getMentionedUsers().get(0), new SkillRoleService.SkillRoleStatistics());
                            event.getChannel().sendMessage("", EmbedUtilities.getSuccessMessage("Assigned " + event.getMessage().getMentionedUsers().get(0).getMentionTag() + " to @" + role.getName() + " role.", event.getLocalization()));
                        } else {
                            try {
                                long userId = Long.parseLong(user);
                                event.getServer().flatMap(server -> server.getMemberById(userId)).ifPresentOrElse(u -> {
                                    Luma.database.assignRole(role.getId(), 146404426746167296L, u.getId());
                                    Luma.skillRoleService.onScoreUpdate(u, new SkillRoleService.SkillRoleStatistics());
                                    event.getChannel().sendMessage("", EmbedUtilities.getSuccessMessage("Assigned " + u.getMentionTag() + " to @" + role.getName() + " role.", event.getLocalization()));
                                }, () -> {
                                    event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage("No user found by mention or id: " + user, event.getLocalization()));
                                });
                            } catch (NumberFormatException e) {
                                event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage("No user found by mention or id: " + user, event.getLocalization()));
                            }
                        }
                    }, () -> {
                        event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage("No role found: `" + roleName + "`", event.getLocalization()));
                    });
        } else {
            event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage(event.getLocalization().get("not_enough_arguments"), event.getLocalization()));
        }
    }

    @Command(aliases = {"unassign"}, description = "unassign_description", usage = "", neededPerms = "CLEANUP", whilelistedGuilds = "146404426746167296")
    public void onUnassign(CommandEvent event) {
        if(event.getCommandArgs().length >= 2){
            String user = event.getCommandArgs()[0];
            String roleName = event.getCommandArgs()[1];
            event.getServer().flatMap(server -> server.getRolesByName(roleName).stream().findFirst())
                    .ifPresentOrElse(role -> {
                        if (event.getMessage().getMentionedUsers().size() > 0) {
                            Luma.database.unassignRole(role.getId(), 146404426746167296L, event.getMessage().getMentionedUsers().get(0).getId());
                            Luma.skillRoleService.onScoreUpdate(event.getMessage().getMentionedUsers().get(0), new SkillRoleService.SkillRoleStatistics());
                            event.getChannel().sendMessage("", EmbedUtilities.getSuccessMessage("Unassigned " + event.getMessage().getMentionedUsers().get(0).getMentionTag() + " from @" + role.getName() + " role.", event.getLocalization()));
                        } else {
                            try {
                                long userId = Long.parseLong(user);
                                event.getServer().flatMap(server -> server.getMemberById(userId)).ifPresentOrElse(u -> {
                                    Luma.database.unassignRole(role.getId(), 146404426746167296L, u.getId());
                                    Luma.skillRoleService.onScoreUpdate(u, new SkillRoleService.SkillRoleStatistics());
                                    event.getChannel().sendMessage("", EmbedUtilities.getSuccessMessage("Unassigned " + u.getMentionTag() + " from @" + role.getName() + " role.", event.getLocalization()));
                                }, () -> {
                                    event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage("No user found by mention or id: " + user, event.getLocalization()));
                                });
                            } catch (NumberFormatException e) {
                                event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage("No user found by mention or id: " + user, event.getLocalization()));
                            }
                        }
                    }, () -> {
                        event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage("No role found: `" + roleName + "`", event.getLocalization()));
                    });
        } else {
            event.getChannel().sendMessage("", EmbedUtilities.getErrorMessage(event.getLocalization().get("not_enough_arguments"), event.getLocalization()));
        }
    }
}
