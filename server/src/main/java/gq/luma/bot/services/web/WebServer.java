package gq.luma.bot.services.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries;
import com.lukaspradel.steamapi.webapi.request.GetPlayerSummariesRequest;
import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory;
import gq.luma.bot.Luma;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.services.Bot;
import gq.luma.bot.services.Service;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.io.IoCallback;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.util.HttpString;
import io.undertow.util.RedirectBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.undertow.context.UndertowWebContext;
import org.pac4j.undertow.handler.CallbackHandler;
import org.pac4j.undertow.handler.LogoutHandler;
import org.pac4j.undertow.handler.SecurityHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class WebServer implements Service {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    private Path INDEX_PATH;
    private Path VERIFY_PATH;
    private Path VERIFY_SUCCESS_PATH;
    private Path VERIFY_DASHBOARD_PATH;
    private Path CDN_PATH;

    private Undertow webpageServer;

    @Override
    public void startService() throws IOException {
        INDEX_PATH = Paths.get(FileReference.webRoot.getAbsolutePath(), "index.html");
        VERIFY_PATH = Paths.get(FileReference.webRoot.getAbsolutePath(), "verify.html");
        VERIFY_SUCCESS_PATH = Paths.get(FileReference.webRoot.getAbsolutePath(), "verifysuccess.html");
        initVerifySuccessPage();
        VERIFY_DASHBOARD_PATH = Paths.get(FileReference.webRoot.getAbsolutePath(), "verifydashboard.html");
        initVerifyDashboardPage();
        CDN_PATH = Paths.get(FileReference.webRoot.getAbsolutePath(), "cdn");

        final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

        final Config securityConfig = new WebSecConfigFactory().build();

        HttpHandler rootHandler = Handlers.path()
                .addExactPath("/", exchange -> exchange.getResponseSender()
                .transferFrom(FileChannel.open(INDEX_PATH), IoCallback.END_EXCHANGE));
        HttpHandler cdnHandler = exchange -> {
            String[] split = exchange.getRelativePath().split("/");
            String filename = split[split.length - 1];

            System.out.println("Rel path: " + exchange.getRelativePath());
            System.out.println("File name: " + filename);

            for(Path path : Files.list(CDN_PATH).collect(Collectors.toCollection(ArrayList::new))) {
                System.out.println(path.getFileName().toString() + "=?=" + filename);
                if(path.getFileName().toString().equalsIgnoreCase(filename)) {
                    try {
                        exchange.getResponseSender().transferFrom(FileChannel.open(path), IoCallback.END_EXCHANGE);
                    } catch (IOException e) {
                        logger.error("Error while sending to cdn: ", e);
                        exchange.setStatusCode(500);
                        exchange.getResponseSender().send("Something went wrong retrieving this file. Please try again later", IoCallback.END_EXCHANGE);
                    }
                    return;
                }
            }

            exchange.setStatusCode(404);
            exchange.getResponseSender().send("Could not find file " + filename + ". Please try again later.");
        };
        HttpHandler verifyWebpageHandler = new SessionAttachmentHandler(Handlers.path()
                .addExactPath("/", exchange -> {
                    exchange.getResponseHeaders()
                            .add(new HttpString("Cache-Control"), "no-cache, no-store, must-revalidate")
                            .add(new HttpString("Pragma"), "no-cache")
                            .add(new HttpString("Expires"), "0");

                    UndertowWebContext context = new UndertowWebContext(exchange, securityConfig.getSessionStore());
                    ProfileManager profileManager = new ProfileManager(context);
                    List<? extends CommonProfile> profiles = profileManager.getAll(true);

                    profiles.forEach(profile -> System.out.println(profile.getClass().getSimpleName()));

                    DiscordProfile latestDiscordProfile = profiles.stream()
                            .filter(profile -> profile instanceof DiscordProfile).map(profile -> (DiscordProfile)profile)
                            .findFirst().orElse(null);
                    SteamOpenIdProfile latestSteamProfile = profiles.stream()
                            .filter(profile -> profile instanceof SteamOpenIdProfile).map(profile -> (SteamOpenIdProfile)profile)
                            .findFirst().orElse(null);

                    if(latestDiscordProfile != null) {
                        serveVerifySuccess(exchange, latestDiscordProfile, latestSteamProfile);

                        Bot.api.getServerById(146404426746167296L).ifPresent(server ->
                                Bot.api.getUserById(latestDiscordProfile.getId()).thenAccept(user ->
                                        Bot.api.getRoleById(558133536784121857L).ifPresent( role ->
                                                server.addRoleToUser(user, role))));

                        //TODO: Offer a sign in with srcom

                        Luma.database.verifyUser(Long.parseLong(latestDiscordProfile.getId()), 146404426746167296L, latestDiscordProfile.getAccessToken());
                        Luma.database.addVerifiedIP(Long.parseLong(latestDiscordProfile.getId()), 146404426746167296L, exchange.getRequestHeaders().get("CF-Connecting-IP").getFirst());

                        System.out.println("Discord user accessed verify.luma.gq:");
                        System.out.println("Id: "+ latestDiscordProfile.getId());
                        System.out.println("Name: " + latestDiscordProfile.getUsername() + "#" + latestDiscordProfile.getDiscriminator());
                        System.out.println("IP: " + exchange.getRequestHeaders().get("CF-Connecting-IP").getFirst());
                        String jsonConnections = null;
                        try {
                            jsonConnections = Objects.requireNonNull(okHttpClient.newCall(new Request.Builder()
                                    .url("https://discordapp.com/api/v6/users/@me/connections")
                                    .addHeader("Authorization", "Bearer " + latestDiscordProfile.getAccessToken())
                                    .build()).execute().body()).string();
                        } catch (IOException e) {
                            e.printStackTrace();
                            exchange.setStatusCode(500);
                            exchange.getResponseSender().send("Failed to get the user connections. Please try again.", IoCallback.END_EXCHANGE);
                        }
                        System.out.println("Connections: " + jsonConnections);

                        if(latestSteamProfile != null) {
                            System.out.println("They also have a steam logged in, id=" + latestSteamProfile.getId());
                            GetPlayerSummariesRequest request = SteamWebApiRequestFactory.createGetPlayerSummariesRequest(List.of(latestSteamProfile.getId()));
                            GetPlayerSummaries summary = Luma.steamApi.steamWebApiClient.processRequest(request);
                            String playerName = summary.getResponse().getPlayers().get(0).getPersonaname();
                            Luma.database.addVerifiedConnection(Long.parseLong(latestDiscordProfile.getId()), 146404426746167296L, latestSteamProfile.getId(), "steam", playerName, latestSteamProfile.getLinkedId());
                        }

                        for(JsonValue val : Json.parse(jsonConnections).asArray()) {
                            JsonObject connectionObj = val.asObject();
                            System.out.println("trying to add connection: " + connectionObj.toString());
                            Luma.database.addVerifiedConnection(Long.parseLong(latestDiscordProfile.getId()), 146404426746167296L, connectionObj.get("id").asString(), connectionObj.get("type").asString(), connectionObj.get("name").asString(), null);
                        }

                        return;
                    }
                    new RedirectHandler("/login/discord").handleRequest(exchange);
                    //RedirectBuilder.redirect(exchange, "/login/discord");
                    //exchange.getResponseSender().transferFrom(FileChannel.open(VERIFY_PATH), IoCallback.END_EXCHANGE);
                })
                .addExactPath("/login/discord", exchange -> {
                    securityConfig.getClients().findClient("discord").redirect(new UndertowWebContext(exchange));
                    exchange.endExchange();
                })
                .addExactPath("/login/steam", SecurityHandler.build(exchange -> {
                    securityConfig.getClients().findClient("steam").redirect(new UndertowWebContext(exchange));
                    exchange.endExchange();
                }, securityConfig, "discord"))
                .addExactPath("/dashboard", SecurityHandler.build(exchange -> {
                    serveVerifyDashboard(exchange, Bot.api);
                }, securityConfig, "discord", "discord_admin"))
                .addExactPath("/callback", CallbackHandler.build(securityConfig, null, true))
                .addExactPath("/logout", new LogoutHandler(securityConfig, "/")),
                new InMemorySessionManager("SessionManager"),
                new SessionCookieConfig());

        webpageServer = Undertow.builder()
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .addHttpListener(80, "0.0.0.0")
                .setHandler(exchange -> {
                    System.out.println("Call to host name: " + exchange.getHostName());
                    switch (exchange.getHostName()) {
                        case "luma.gq":
                        case "www.luma.gq":
                            rootHandler.handleRequest(exchange);
                            break;
                        case "cdn.luma.gq":
                            cdnHandler.handleRequest(exchange);
                            break;
                        case "verify.luma.gq":
                            verifyWebpageHandler.handleRequest(exchange);
                            break;
                        default:
                            System.err.println("No host name found for host: " + exchange.getHostName());
                            exchange.setStatusCode(404);
                            exchange.getResponseSender().send("Invalid subdomain.", IoCallback.END_EXCHANGE);
                    }
                })
                .build();

        webpageServer.start();
    }

    private String verifySuccessBasePage;
    private int avatarUrlRep;
    private int discriminatedNameRep;
    private int currentlyAttachedSteamRep;
    private int currentlyAttachedSrcomRep;

    private void initVerifySuccessPage() throws IOException {

        byte[] verifySuccessBuffer = new byte[(int) Files.size(VERIFY_SUCCESS_PATH)];

        try (SeekableByteChannel sbc = Files.newByteChannel(VERIFY_SUCCESS_PATH);
             InputStream stream = Channels.newInputStream(sbc)) {
            int c;
            int i = 0;
            while ((c = stream.read()) != -1) {
                if(c == '$') {
                    if((c = stream.read()) == '$') {
                        if((c = stream.read()) == '{') {
                            StringBuilder sb = new StringBuilder();

                            while((c = stream.read()) != '}') {
                                sb.append((char)c);
                            }

                            switch (sb.toString()) {
                                case "avatar_url":
                                    avatarUrlRep = i; break;
                                case "discriminated_name":
                                    discriminatedNameRep = i; break;
                                case "currently_attached_steam":
                                    currentlyAttachedSteamRep = i; break;
                                case "currently_attached_srcom":
                                    currentlyAttachedSrcomRep = i; break;
                            }

                            continue;
                        } else {
                            verifySuccessBuffer[i] = '$';
                            i++;
                            verifySuccessBuffer[i] = '$';
                            i++;
                        }
                    } else {
                        verifySuccessBuffer[i] = '$';
                        i++;
                    }
                }
                verifySuccessBuffer[i] = (byte) c;
                i++;
            }
        }

        verifySuccessBasePage = new String(verifySuccessBuffer).trim();
    }

    private void serveVerifySuccess(HttpServerExchange exchange, DiscordProfile profile, SteamOpenIdProfile steamOpenIdProfile) {
        StringBuilder sb = new StringBuilder();
        sb.append(verifySuccessBasePage, 0, avatarUrlRep);
        profile.getUser(Bot.api).ifPresentOrElse(user -> sb.append(user.getAvatar().getUrl()),
                () -> sb.append(profile.getPictureUrl()));
        sb.append(verifySuccessBasePage, avatarUrlRep, discriminatedNameRep);
        sb.append(profile.getUsername()).append("#").append(profile.getDiscriminator());
        sb.append(verifySuccessBasePage, discriminatedNameRep, currentlyAttachedSteamRep);
        if(steamOpenIdProfile != null) {
            sb.append(steamOpenIdProfile.getUsername());
        }
        sb.append(verifySuccessBasePage, currentlyAttachedSteamRep, currentlyAttachedSrcomRep);
        //TODO: Add in srcom support
        sb.append(verifySuccessBasePage, currentlyAttachedSrcomRep, verifySuccessBasePage.length() - 1);

        exchange.getResponseSender().send(sb.toString(), IoCallback.END_EXCHANGE);
    }

    private String verifyDashboardBasePage;
    private int userBoxesRep;

    private void initVerifyDashboardPage() throws IOException {

        byte[] verifyDashboardBuffer = new byte[(int) Files.size(VERIFY_DASHBOARD_PATH)];

        try (SeekableByteChannel sbc = Files.newByteChannel(VERIFY_DASHBOARD_PATH);
             InputStream stream = Channels.newInputStream(sbc)) {
            int c;
            int i = 0;
            while ((c = stream.read()) != -1) {
                if(c == '$') {
                    if((c = stream.read()) == '$') {
                        if((c = stream.read()) == '{') {
                            StringBuilder sb = new StringBuilder();

                            while((c = stream.read()) != '}') {
                                sb.append((char)c);
                            }

                            switch (sb.toString()) {
                                case "user_boxes":
                                    userBoxesRep = i; break;
                            }

                            continue;
                        } else {
                            verifyDashboardBuffer[i] = '$';
                            i++;
                            verifyDashboardBuffer[i] = '$';
                            i++;
                        }
                    } else {
                        verifyDashboardBuffer[i] = '$';
                        i++;
                    }
                }
                verifyDashboardBuffer[i] = (byte) c;
                i++;
            }
        }

        verifyDashboardBasePage = new String(verifyDashboardBuffer).trim();
    }

    private void serveVerifyDashboard(HttpServerExchange exchange, DiscordApi api) {
        StringBuilder sb = new StringBuilder();
        sb.append(verifyDashboardBasePage, 0, userBoxesRep);

        Server server = api.getServerById(146404426746167296L).orElse(null);

        if(server != null) {
            final Collection<User> members = server.getMembers();

            for(User user : members) {
                sb.append("<div class=\"userbox hidden\" name=\"").append(user.getDiscriminatedName()).append("\">");
                boolean userIsVerified = Luma.database.isUserVerified(user.getId());
                boolean userHasVerifiedRole = user.getRoles(server).stream().anyMatch(role -> role.getId() == 558133536784121857L);
                if(userIsVerified) {
                    // Add the checkmark
                    sb.append("<svg class=\"tablemargin\" width=\"30\" height=\"30\" viewBox=\"0 0 48 48\" fill=\"none\" stroke=\"green\" stroke-width=\"4\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><polyline points=\"40 12 18 34 8 24\"></polyline></svg>");
                } else if(userHasVerifiedRole) {
                    // Add the question mark
                    sb.append("<svg class=\"tablemargin\" width=\"30\" height=\"30\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"yellow\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"10\"></circle><path d=\"M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3\"></path><line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"17\"></line></svg>");
                } else {
                    // Add the X mark
                    sb.append("<svg class=\"tablemargin\" width=\"30\" height=\"30\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"red\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"></line><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"></line></svg>");
                }
                sb.append("<div class=\"image-cropper tablemargin\">")
                        .append("<img src=\"").append(user.getAvatar().getUrl().toString()).append("\" alt=\"Cannot Load PFP\"></img>")
                        .append("</div>");
                sb.append("<span class=\"tablemargin\">").append(user.getDiscriminatedName()).append("</span>");

                if(userIsVerified) {
                    sb.append("<div class = \"tablemargin\" style=\"display:block;\">");
                    Luma.database.writeConnectionBoxes(user.getId(), sb);
                    sb.append("</div>");

                    sb.append("<div class=\"tablemargin\" style=\"display:block\">");
                    Luma.database.writeIps(user.getId(), sb);
                    sb.append("</div>");

                    sb.append("<div class = \"tablemargin\" style=\"display:block\">");
                    //TODO: Find alts
                    sb.append("</div>");
                }

                sb.append("</br></div>");
            }
        } else {
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("Error retrieving server data", IoCallback.END_EXCHANGE);
        }

        sb.append(verifyDashboardBasePage, userBoxesRep, verifyDashboardBasePage.length() - 1);

        exchange.getResponseSender().send(sb.toString(), IoCallback.END_EXCHANGE);
    }
}
