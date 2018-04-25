package gq.luma.bot.services;

public interface Service {
    void startService() throws Exception;

    default void reload() throws Exception {

    }
}
