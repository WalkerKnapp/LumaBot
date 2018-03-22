package gq.luma.bot.commands.subsystem;

import java.lang.reflect.Method;

public class MCommand {

    Command command;
    Object clazz;
    Method method;

    public MCommand(Command command, Object clazz, Method method) {
        this.command = command;
        this.clazz = clazz;
        this.method = method;
    }

    public Command getCommand() {
        return command;
    }

    public Object getCaller() {
        return clazz;
    }

    public Method getMethod() {
        return method;
    }
}
