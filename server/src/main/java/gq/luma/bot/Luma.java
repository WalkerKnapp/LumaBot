package gq.luma.bot;

import com.fasterxml.jackson.core.JsonFactory;
import gq.luma.bot.services.apis.TwitchApi;
import gq.luma.bot.services.*;
import gq.luma.bot.services.apis.SteamApi;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.reference.KeyReference;
import gq.luma.bot.services.web.WebServer;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Luma {
    private static final Logger logger = LoggerFactory.getLogger(Luma.class);

    public static ScheduledExecutorService schedulerService = Executors.newScheduledThreadPool(12);
    public static ExecutorService executorService = new ThreadPoolExecutor(4, Integer.MAX_VALUE, 5, TimeUnit.MINUTES, new SynchronousQueue<>());
    public static OkHttpClient okHttpClient;
    public static JsonFactory jsonFactory;


    public static Database database;
    public static TwitchApi twitchApi;
    public static SteamApi steamApi;

    private static final List<Service> services;

    static {
        services = new ArrayList<>();
        services.add(new KeyReference());
        services.add(database = new Database());
        services.add(new WebServer());
        services.add(steamApi = new SteamApi());
        services.add(new Bot());
        services.add(new PinsService());
        services.add(new UndunceService());
        services.add(twitchApi = new TwitchApi());
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, KeyStoreException {
        logger.info("Starting Services.");

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore)null);

        for(TrustManager manager : tmf.getTrustManagers()){
            if(manager instanceof X509TrustManager){
                for(X509Certificate cert : ((X509TrustManager)manager).getAcceptedIssuers()){
                    logger.debug("Loaded cert: " + cert.getSerialNumber());
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
