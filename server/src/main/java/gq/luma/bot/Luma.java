package gq.luma.bot;

import com.fasterxml.jackson.core.JsonFactory;
import gq.luma.bot.services.GDrive;
import gq.luma.bot.services.apis.TesseractApi;
import gq.luma.bot.services.apis.TwitchApi;
import gq.luma.bot.services.apis.YoutubeApi;
import gq.luma.bot.services.*;
import gq.luma.bot.services.apis.SteamApi;
import gq.luma.bot.services.node.NodeServer;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.services.web.WebServer;
import gq.luma.bot.systems.filtering.FilterManager;
import gq.luma.bot.utils.WordEncoder;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Luma {
    private static Logger logger = LoggerFactory.getLogger(Luma.class);

    public static ScheduledExecutorService schedulerService = Executors.newScheduledThreadPool(12);
    public static ExecutorService executorService = new ThreadPoolExecutor(4, Integer.MAX_VALUE, 5, TimeUnit.MINUTES, new SynchronousQueue<>());
    public static OkHttpClient okHttpClient;
    public static JsonFactory jsonFactory;


    public static Database database;
    //public static NodeServer nodeServer;
    public static TwitchApi twitchApi;
    public static Clarifai clarifai;
    //public static ClamAV clamAV;
    public static FilterManager filterManager;
    public static GDrive gDrive;
    //public static YoutubeApi youtubeApi;
    public static SteamApi steamApi;
    public static SkillRoleService skillRoleService;
    public static EvilsService evilsService;
    public static Bot bot;

    private static final List<Service> services;

    static {
        services = new ArrayList<>();
        services.add(new FileReference());
        services.add(new KeyReference());
        services.add(database = new Database());
        services.add(evilsService = new EvilsService());
        //services.add(new WordEncoder());
        //services.add(youtubeApi = new YoutubeApi());
        //services.add(nodeServer = new NodeServer());
        //services.add(new TaskScheduler());
        services.add(new WebServer());
        //services.add(clarifai = new Clarifai());
        //services.add(clamAV = new ClamAV());
        //services.add(new TesseractApi());
        //services.add(filterManager = new FilterManager());
        //services.add(gDrive = new GDrive());
        services.add(steamApi = new SteamApi());
        services.add(bot = new Bot());
        services.add(new PinsService());
        services.add(new UndunceService());
        //services.add(skillRoleService = new SkillRoleService());
        services.add(twitchApi = new TwitchApi());
        //services.add(new TwitchNotifier());
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, KeyStoreException {
        logger.info("Starting Services.");

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore)null);

        for(TrustManager manager : tmf.getTrustManagers()){
            if(manager instanceof X509TrustManager){
                for(X509Certificate cert : ((X509TrustManager)manager).getAcceptedIssuers()){
                    logger.debug("Loaded cert: " + cert.getSerialNumber());
                    if(cert.getSerialNumber().equals(new BigInteger("158646793650523935")))
                        logger.debug("Found google!");
                }
            }
        }

        okHttpClient = new OkHttpClient.Builder().build();
        jsonFactory = new JsonFactory();

        try {
            for (Service s : services) {
                System.out.println("Starting " + s.getClass().getSimpleName());
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
