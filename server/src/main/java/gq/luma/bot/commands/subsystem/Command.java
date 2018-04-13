package gq.luma.bot.commands.subsystem;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {
    String[] aliases();

    String description();

    String usage();

    String parent() default "";

    String neededPerms() default "";

    String whilelistedGuilds() default "";
}
