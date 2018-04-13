package gq.luma.bot;

import gq.luma.bot.services.GDrive;
import gq.luma.bot.services.YoutubeApi;
import gq.luma.bot.services.*;
import gq.luma.bot.services.node.NodeServer;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.utils.WordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Luma {
    private static Logger logger = LoggerFactory.getLogger(Luma.class);

    public static ScheduledExecutorService lumaExecutorService = Executors.newScheduledThreadPool(64);
    public static Database database;
    public static NodeServer nodeServer;
    public static Clarifai clarifai;
    public static GDrive gDrive;
    public static YoutubeApi youtubeApi;

    private static List<Service> services;

    static {
        services = new ArrayList<>();
        services.add(new FileReference());
        services.add(new KeyReference());
        services.add(database = new Database());
        services.add(new WordEncoder());
        services.add(youtubeApi = new YoutubeApi());
        services.add(nodeServer = new NodeServer());
        services.add(new TaskScheduler());
        services.add(new WebServer());
        services.add(clarifai = new Clarifai());
        services.add(new TesseractApi());
        services.add(gDrive = new GDrive());
        services.add(new Bot());
    }

    public static void main(String[] args){
        logger.info("Starting Services.");

        try {
            for (Service s : services) {
                logger.info("Starting " + s.getClass().getSimpleName());
                s.startService();
            }
        } catch (Exception e){
            logger.error("Encountered error while starting services:", e);
            System.exit(-1);
        }

        logger.info("Finished loading services!");
    }
}
