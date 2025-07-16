package gq.luma.bot.services;

import gq.luma.bot.Luma;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class UndunceService implements Service {
    private static final long P2SR_SERVER_ID = 146404426746167296L;
    private static final long DUNCE_ROLE_ID = 312324674275115008L;

    @Override
    public void startService() {
        Luma.schedulerService.scheduleAtFixedRate(this::checkUndunces, 0, 5, TimeUnit.SECONDS);
    }

    public void checkUndunces() {
        Luma.database.getCurrentDunces((userId, instant) -> {
            if (Instant.now().isAfter(instant)) {
                Bot.api.getUserById(userId)
                        .thenAccept(targetUser -> {
                            // Remove instant in database
                            Luma.database.removeUndunceInstant(targetUser.getId());

                            // Give back previous roles
                            Luma.database.popDunceStoredRoles(targetUser, Bot.api.getServerById(P2SR_SERVER_ID).orElseThrow(AssertionError::new));

                            // Remove dunce role
                            Bot.api.getRoleById(DUNCE_ROLE_ID).orElseThrow(AssertionError::new)
                                    .removeUser(targetUser);
                        });
            }
        });
    }
}
