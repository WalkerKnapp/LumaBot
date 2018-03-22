package gq.luma.bot;

import gq.luma.bot.systems.YoutubeApi;
import gq.luma.bot.services.*;
import gq.luma.bot.services.node.NodeServer;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.utils.WordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Luma {
    private static Logger logger = LoggerFactory.getLogger(Luma.class);

    public static ScheduledExecutorService lumaExecutorService = Executors.newScheduledThreadPool(64);
    public static NodeServer nodeServer;
    public static Clarifai clarifai;

    static {
        try {
            nodeServer = new NodeServer();
            clarifai = new Clarifai();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private static final Service[] services = {new FileReference(), new KeyReference(), new Database(), new WordEncoder(), new YoutubeApi(), nodeServer, new TaskScheduler(), new WebServer(), clarifai, new TesseractApi(), new Bot()};

    public static void main(String[] args){
        logger.info("Starting Services.");

        try {
            for (Service s : services) {
                logger.info("Starting " + s.getClass().getSimpleName());
                s.startService();
            }
        } catch (Exception e){
            e.printStackTrace();
            System.exit(-1);
        }

        logger.info("Finished loading services!");
    }
}
