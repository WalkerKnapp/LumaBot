package gq.luma.bot.services.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.lukaspradel.steamapi.core.exception.SteamApiException;
import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries;
import com.lukaspradel.steamapi.webapi.request.GetPlayerSummariesRequest;
import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory;
import gq.luma.bot.Luma;
import gq.luma.bot.reference.FileReference;
import gq.luma.bot.services.Bot;
import gq.luma.bot.services.Service;
import gq.luma.bot.services.apis.SRcomApi;
import gq.luma.bot.services.web.page.WebPage;
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
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.PathTemplateMatch;
import okhttp3.Request;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.undertow.context.UndertowSessionStore;
import org.pac4j.undertow.context.UndertowWebContext;
import org.pac4j.undertow.handler.CallbackHandler;
import org.pac4j.undertow.handler.LogoutHandler;
import org.pac4j.undertow.handler.SecurityHandler;
import org.pac4j.undertow.http.UndertowHttpActionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.streams.ChannelOutputStream;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebServer implements Service {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    private static class ProfileRestrictedHandler implements HttpHandler {
        private final WebPage pageToServe;
        private final Config securityConfig;
        private final boolean redirect;

        private ProfileRestrictedHandler(WebPage page, Config securityConfig, boolean redirect) {
            this.pageToServe = page;
            this.securityConfig = securityConfig;
            this.redirect = redirect;
        }

        public void servePage(HttpServerExchange exchange, DiscordProfile discordProfile, SteamOpenIdProfile steamProfile, OidcProfile twitchProfile) {
            pageToServe.serve(exchange, "discordId", discordProfile.getId());
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            System.out.println(exchange.isRequestChannelAvailable());
            exchange.getResponseHeaders()
                    .add(new HttpString("Access-Control-Allow-Origin"), "*")
                    .add(new HttpString("Cache-Control"), "no-cache, no-store, must-revalidate")
                    .add(new HttpString("Pragma"), "no-cache")
                    .add(new HttpString("Expires"), "0");

            UndertowWebContext context = new UndertowWebContext(exchange);
            ProfileManager profileManager = new ProfileManager(context, securityConfig.getSessionStoreFactory().newSessionStore(exchange));
            List<UserProfile> profiles = profileManager.getProfiles();

            //TODO: Make this configurable
            long serverId = 146404426746167296L;

            //profiles.forEach(profile -> System.out.println(profile.getClass().getSimpleName()));

            DiscordProfile latestDiscordProfile = profiles.stream()
                    .filter(profile -> profile instanceof DiscordProfile).map(profile -> (DiscordProfile)profile)
                    .findFirst().orElse(null);
            SteamOpenIdProfile latestSteamProfile = profiles.stream()
                    .filter(profile -> profile instanceof SteamOpenIdProfile).map(profile -> (SteamOpenIdProfile)profile)
                    .findFirst().orElse(null);
            OidcProfile latestTwitchProfile = profiles.stream()
                    .filter(profile -> profile instanceof OidcProfile).map(profile -> (OidcProfile)profile)
                    .findFirst().orElse(null);


            if(latestDiscordProfile != null) {
                Luma.database.addUserRecord(Long.parseLong(latestDiscordProfile.getId()), serverId, latestDiscordProfile.getAccessToken());
                String ip;
                if (exchange.getRequestHeaders().contains("X-Forwarded-For")) {
                    ip = exchange.getRequestHeaders().get("X-Forwarded-For").getFirst();
                } else {
                    ip = exchange.getConnection().getPeerAddress().toString();
                }

                boolean lookupConnectionsSucceeded = lookupConnections(latestDiscordProfile, latestSteamProfile, latestTwitchProfile, serverId);

                if(Luma.database.getUserVerified(Long.parseLong(latestDiscordProfile.getId())) < 1) {
                    attemptToVerifyUser(latestDiscordProfile, serverId, ip);
                } else {
                    // Still try to give them the verified role
                    Role dunce = Bot.api.getRoleById(312324674275115008L).orElseThrow(AssertionError::new);
                    Role unverified = Bot.api.getRoleById(UNVERIFIED_ROLE).orElseThrow(AssertionError::new);

                    Bot.api.getServerById(serverId).ifPresent(server -> {
                        Bot.api.getRoleById(VERIFIED_ROLE).ifPresent(role -> {
                            Bot.api.getUserById(latestDiscordProfile.getId()).thenAccept(user -> {
                               if(!user.getRoles(server).contains(dunce) && !user.getRoles(server).contains(role)) {
                                   user.addRole(role);

                                   if (user.getRoles(server).contains(unverified)) {
                                       user.removeRole(unverified);
                                   }
                               }
                            });
                        });
                    });
                }

                Luma.database.addUserConnectionAttempt(Long.parseLong(latestDiscordProfile.getId()), serverId, ip);

                if(lookupConnectionsSucceeded) {
                    servePage(exchange, latestDiscordProfile, latestSteamProfile, latestTwitchProfile);
                } else {
                    exchange.setStatusCode(500);
                    exchange.getResponseSender().send("Failed to get the user connections. Please try again.", IoCallback.END_EXCHANGE);
                }
            } else {
                if(redirect) {
                    new RedirectHandler("/login/discord").handleRequest(exchange);
                } else {
                    exchange.setStatusCode(403);
                    exchange.getResponseSender().send("Cannot access endpoint without being logged in.", IoCallback.END_EXCHANGE);
                }
            }
        }

        private static final long VERIFIED_ROLE = 558133536784121857L;
        private static final long UNVERIFIED_ROLE = 804200620869156885L;
        private static final long MOD_NOTIFICATIONS_ROLE = 797178777754140722L;

        private static final long VERIFICATION_LOG_CHANNEL = 782042783442927616L;

        private synchronized void attemptToVerifyUser(DiscordProfile profile, long serverId, String ip) {
            Bot.api.getUserById(profile.getId()).thenAccept(user -> Bot.api.getTextChannelById(VERIFICATION_LOG_CHANNEL).ifPresent(channel -> {
                new MessageBuilder()
                        .append("Verification: ")
                        .append(user.getMentionTag())
                        .append(" (").append(user.getDiscriminatedName()).append(")")
                        .send(channel);
            }));

            long userId = Long.parseLong(profile.getId());

            Role modNotifRole = Bot.api.getRoleById(MOD_NOTIFICATIONS_ROLE).orElseThrow(AssertionError::new);

            Bot.api.getServerById(serverId).ifPresent(server -> {
                Bot.api.getUserById(userId).thenAccept(user -> {
                    AtomicBoolean altNotice = new AtomicBoolean(false);

                    StringBuilder altNoticeText = new StringBuilder(modNotifRole.getMentionTag())
                            .append(" Alt notice: ").append(user.getMentionTag())
                            .append(" (").append(user.getDiscriminatedName()).append(")\n");

                    // Check if the IP address matches an existing user
                    List<Long> prevConnections = Luma.database.getUserConnectionAttemptsByIP(ip);

                    for (long id : prevConnections) {
                        // Skip previous connections that match the current user.
                        if (id == user.getId()) {
                            continue;
                        }

                        altNotice.set(true);

                        try {
                            User mention = Bot.api.getUserById(id).get(5, TimeUnit.SECONDS);

                            altNoticeText.append("\t - Has the same IP address as user: ")
                                    .append(mention.getMentionTag()).append(" (")
                                    .append(mention.getDiscriminatedName()).append(")\n");
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            e.printStackTrace();
                            altNoticeText.append("\t - <@").append(id).append("> (lookup failed: ").append(e.getMessage()).append(") \n");
                        }
                    }

                    Luma.database.getVerifiedConnectionsByUser(userId, serverId).forEach((type, ids) -> {
                        for (String id : ids) {
                            Luma.database.getVerifiedConnectionsById(id, type).stream()
                                    .filter(checkUser -> !checkUser.equals(user))
                                    .forEach(checkUser -> {
                                        altNotice.set(true);
                                        altNoticeText.append("\t - Shares a ").append(type)
                                                .append(" account (").append(id).append(") with user: ")
                                                .append(checkUser.getMentionTag())
                                                .append(" (").append(checkUser.getDiscriminatedName()).append(")\n");
                                    });
                        }
                    });

                    if (altNotice.get()) {
                        Bot.api.getTextChannelById(VERIFICATION_LOG_CHANNEL).ifPresent(channel -> {
                            channel.sendMessage(altNoticeText.toString());
                        });
                    }
                });
            });

            Role dunce = Bot.api.getRoleById(312324674275115008L).orElseThrow(AssertionError::new);
            Role unverified = Bot.api.getRoleById(UNVERIFIED_ROLE).orElseThrow(AssertionError::new);

            Bot.api.getServerById(146404426746167296L).ifPresent(server ->
                    Bot.api.getUserById(profile.getId()).thenAccept(user -> {
                        if (!user.getRoles(server).contains(dunce)) {
                            Bot.api.getRoleById(VERIFIED_ROLE).ifPresent( role ->
                                    server.addRoleToUser(user, role));

                            if (user.getRoles(server).contains(unverified)) {
                                user.removeRole(unverified);
                            }
                        }
                    }));

            Luma.database.updateUserRecordVerified(Long.parseLong(profile.getId()), serverId, 2);
        }

        private boolean lookupConnections(DiscordProfile discordProfile, SteamOpenIdProfile steamProfile, OidcProfile twitchProfile, long serverId) {
            logger.trace("Discord user accessed luma.portal2.sr:");
            logger.trace("Id: "+ discordProfile.getId());
            logger.trace("Name: " + discordProfile.getUsername() + "#" + discordProfile.getDiscriminator());
            //logger.trace("IP: " + exchange.getRequestHeaders().get("X-Forwarded-For").getFirst());
            try {
                String jsonConnections = Objects.requireNonNull(Luma.okHttpClient.newCall(new Request.Builder()
                        .url("https://discord.com/api/v6/users/@me/connections")
                        .addHeader("Authorization", "Bearer " + discordProfile.getAccessToken())
                        .build()).execute().body()).string();

                //System.out.println("Connections: " + jsonConnections);

                if(steamProfile != null) {
                    //System.out.println("They also have a steam logged in, id=" + steamProfile.getId());
                    GetPlayerSummariesRequest request = SteamWebApiRequestFactory.createGetPlayerSummariesRequest(List.of(steamProfile.getId()));
                    GetPlayerSummaries summary = Luma.steamApi.steamWebApiClient.processRequest(request);
                    String playerName = summary.getResponse().getPlayers().get(0).getPersonaname();
                    String id = summary.getResponse().getPlayers().get(0).getSteamid();
                    id = String.valueOf(Long.parseLong(id) | 0x100000000L);
                    Luma.database.addVerifiedConnection(Long.parseLong(discordProfile.getId()), serverId, id, "steam", playerName, steamProfile.getLinkedId());
                }

                if(twitchProfile != null) {
                    long twitchUserId = Long.parseLong(twitchProfile.getId());
                    String displayName = Luma.twitchApi.client.getHelix().getUsers(Luma.twitchApi.appAccessToken, List.of(String.valueOf(twitchUserId)), null).execute().getUsers().get(0).getDisplayName();
                    //Luma.twitchApi.client.getUserEndpoint().getUser(twitchUserId).getDisplayName();

                    Luma.database.addVerifiedConnection(Long.parseLong(discordProfile.getId()),
                            serverId,
                            twitchProfile.getId(),
                            "twitch",
                            displayName,
                            twitchProfile.getAccessToken().toJSONString());
                }

                for(JsonValue val : Json.parse(jsonConnections).asArray()) {
                    JsonObject connectionObj = val.asObject();
                    logger.trace("trying to add connection: " + connectionObj.toString());
                    String id = connectionObj.get("id").asString();
                    String type = connectionObj.get("type").asString();
                    if("steam".equals(type)) {
                        id = String.valueOf(Long.parseLong(id) | 0x100000000L);
                        //GetPlayerSummariesRequest request = SteamWebApiRequestFactory.createGetPlayerSummariesRequest(List.of(id));
                        //GetPlayerSummaries summary = Luma.steamApi.steamWebApiClient.processRequest(request);
                        //id = summary.getResponse().getPlayers().get(0).getSteamid();
                    }
                    Luma.database.addVerifiedConnection(Long.parseLong(discordProfile.getId()), serverId, id, type, connectionObj.get("name").asString(), null);
                }

            } catch (IOException | SteamApiException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        private static HttpHandler build(WebPage page, Config config, boolean redirect) {
            return new ProfileRestrictedHandler(page, config, redirect);
        }
    }

    private static class FileServeHandler implements HttpHandler {
        private final ByteBuffer uncompressedBuffer;

        private final boolean supportsBr;
        private ByteBuffer brBuffer;
        private final boolean supportsGzip;
        private ByteBuffer gzipBuffer;

        private FileServeHandler(Path path) throws IOException {
            System.out.println("Loading page " + path.toString() + " =====");
            this.uncompressedBuffer = ByteBuffer.wrap(Files.readAllBytes(path));
            logger.debug("Uncompressed - " + this.uncompressedBuffer.capacity());
            Path brPath = Paths.get(path + ".br");
            Path gzipPath = Paths.get(path + ".gz");
            supportsBr = Files.exists(brPath);
            supportsGzip = Files.exists(gzipPath);
            if(supportsBr) {
                this.brBuffer = ByteBuffer.wrap(Files.readAllBytes(brPath));
                logger.debug("Brotli - " + this.brBuffer.capacity());
            }
            if(supportsGzip) {
                this.gzipBuffer = ByteBuffer.wrap(Files.readAllBytes(gzipPath));
                logger.debug("GZip - " + this.gzipBuffer.capacity());
            }
        }

        private FileServeHandler(String... path) throws IOException {
            this(Paths.get(FileReference.webRoot.getAbsolutePath(), path));
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) {
            final List<String> res = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING);
            if(res.contains("br") && supportsBr) {
                brBuffer.rewind();
                exchange.getResponseHeaders().add(Headers.CONTENT_ENCODING, "br");
                exchange.getResponseSender().send(brBuffer);
            } else if(res.contains("gzip") && supportsGzip) {
                gzipBuffer.rewind();
                exchange.getResponseHeaders().add(Headers.CONTENT_ENCODING, "gzip");
                exchange.getResponseSender().send(gzipBuffer);
            } else {
                uncompressedBuffer.rewind();
                exchange.getResponseSender().send(uncompressedBuffer);
            }
        }
    }

    @Override
    public void startService() throws IOException {
        WebPage profilePage = new WebPage(Paths.get(FileReference.webRoot.getAbsolutePath(), "profile", "profile.html"));
        WebPage srcomLoginPage = new WebPage(Paths.get(FileReference.webRoot.getAbsolutePath(), "loginsrcom", "loginsrcom.html"));

        final Config securityConfig = new WebSecConfigFactory().build();
        final InMemorySessionManager sessionManager = new InMemorySessionManager("SessionManager");
        final SessionCookieConfig cookieConfig = new SessionCookieConfig();

        securityConfig.setSessionStoreFactory(parameters -> new UndertowSessionStore((HttpServerExchange) parameters[0]));
        securityConfig.setHttpActionAdapter(UndertowHttpActionAdapter.INSTANCE);

        long serverId = 146404426746167296L;

        HttpHandler verifyWebpageHandler = Handlers.routing()
                .get("/", ProfileRestrictedHandler.build(profilePage, securityConfig, true))
                .get("/profile/bundle.js", new FileServeHandler("profile", "bundle.js"))
                .get("/login/srcom", ProfileRestrictedHandler.build(srcomLoginPage, securityConfig, true))
                .get("/loginsrcom/bundle.js", new FileServeHandler("loginsrcom", "bundle.js"))
                .get("/login/discord", exchange -> {
                    securityConfig.getHttpActionAdapter()
                            .adapt(securityConfig.getClients().findClient("discord").orElseThrow()
                                    .getRedirectionAction(new UndertowWebContext(exchange), securityConfig.getSessionStoreFactory().newSessionStore(exchange)).orElseThrow(),
                                    new UndertowWebContext(exchange));
                    exchange.endExchange();
                })
                .get("/login/steam", SecurityHandler.build(exchange -> {
                    securityConfig.getHttpActionAdapter()
                            .adapt(securityConfig.getClients().findClient("steam").orElseThrow()
                                            .getRedirectionAction(new UndertowWebContext(exchange), securityConfig.getSessionStoreFactory().newSessionStore(exchange)).orElseThrow(),
                                    new UndertowWebContext(exchange));
                    exchange.endExchange();
                }, securityConfig, "discord"))
                .get("/login/twitch", SecurityHandler.build(exchange -> {
                    securityConfig.getHttpActionAdapter()
                            .adapt(securityConfig.getClients().findClient("twitch").orElseThrow()
                                            .getRedirectionAction(new UndertowWebContext(exchange), securityConfig.getSessionStoreFactory().newSessionStore(exchange)).orElseThrow(),
                                    new UndertowWebContext(exchange));
                    exchange.endExchange();
                }, securityConfig, "discord"))
                .get("/callback", CallbackHandler.build(securityConfig, null, null))
                .get("/logout", new LogoutHandler(securityConfig, "/"))
                .get("/user/{did}", //new ProfileRestrictedHandler(null, securityConfig, false) {
                        //@Override
                        //public void servePage(HttpServerExchange exchange, DiscordProfile discordProfile, SteamOpenIdProfile steamProfile) {
                        exchange -> {
                            exchange.getResponseHeaders()
                                    .add(new HttpString("Access-Control-Allow-Origin"), "*")
                                    .add(new HttpString("Cache-Control"), "no-cache, no-store, must-revalidate")
                                    .add(new HttpString("Pragma"), "no-cache")
                                    .add(new HttpString("Expires"), "0");
                            PathTemplateMatch pathMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
                            long discordId = Long.parseLong(pathMatch.getParameters().get("did"));
                            //if(discordId != Long.valueOf(discordProfile.getId())) {
                            //    exchange.setStatusCode(403);
                            //    exchange.endExchange();
                            //    return;
                            //}
                            Bot.api.getCachedUserById(discordId).ifPresentOrElse(user -> {
                                try (StreamSinkChannel sinkChannel = exchange.getResponseChannel()){
                                    try (JsonGenerator generator = Luma.jsonFactory.createGenerator(new BufferedOutputStream(new ChannelOutputStream(sinkChannel)))) {
                                        generator.writeStartObject();
                                        generator.writeStringField("avatarUrl", user.getAvatar().getUrl().toString());
                                        generator.writeStringField("discrimName", user.getDiscriminatedName());
                                        generator.writeNumberField("verified", Luma.database.getUserVerified(discordId));
                                        Luma.database.writeConnectionsJson(discordId, serverId, generator);
                                        generator.writeEndObject();
                                        generator.flush();
                                    }
                                    sinkChannel.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                exchange.endExchange();
                            }, () -> {
                                exchange.setStatusCode(404);
                                exchange.endExchange();
                            });
                        })
                .post("/user/{did}/connections/srcom", new ProfileRestrictedHandler(null, securityConfig, false) {
                    @Override
                    public void servePage(HttpServerExchange exchange, DiscordProfile discordProfile, SteamOpenIdProfile steamProfile, OidcProfile twitchProfile) {

                        PathTemplateMatch pathMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
                        long discordId = Long.parseLong(pathMatch.getParameters().get("did"));

                        byte[] keyData;
                        if (exchange.isRequestChannelAvailable()) {
                            try(StreamSourceChannel channel = exchange.getRequestChannel()) {
                                keyData = new byte[256];
                                ByteBuffer buffer = ByteBuffer.wrap(keyData);
                                System.out.println(channel.read(buffer));
                            } catch (IOException e) {
                                e.printStackTrace();
                                exchange.setStatusCode(403);
                                exchange.endExchange();
                                return;
                            }
                        } else {
                            exchange.setStatusCode(403);
                            System.err.println("Channel is not available for POST request");
                            exchange.endExchange();
                            return;
                        }
                        //exchange.getInputStream()

                        exchange.dispatch(Luma.executorService, () -> {
                            String apiKey = new String(keyData).trim();
                            System.out.println(apiKey);

                            try {
                                if (discordId == Long.parseLong(discordProfile.getId()) && !apiKey.isEmpty()) {
                                    if (SRcomApi.requestProfile(discordId, serverId, apiKey)) {
                                        System.out.println("Profile requested successfully!");
                                        exchange.setStatusCode(200);
                                        exchange.getResponseSender().send("{\"success\":true}", IoCallback.END_EXCHANGE);
                                        //exchange.endExchange();
                                    } else {
                                        System.out.println("Profile requested failed");
                                        exchange.setStatusCode(200);
                                        exchange.getResponseSender().send("{\"success\":false}", IoCallback.END_EXCHANGE);
                                        //exchange.endExchange();
                                    }
                                } else {
                                    exchange.setStatusCode(403);
                                    exchange.endExchange();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                exchange.setStatusCode(403);
                                exchange.endExchange();
                            }
                        });
                    }
                })
                .delete("/user/{did}/connections/{connid}", new ProfileRestrictedHandler(null, securityConfig, false) {
                    @Override
                    public void servePage(HttpServerExchange exchange, DiscordProfile discordProfile, SteamOpenIdProfile steamProfile, OidcProfile twitchProfile) {
                        PathTemplateMatch pathMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
                        long discordId = Long.parseLong(pathMatch.getParameters().get("did"));
                        String connId = pathMatch.getParameters().get("connid");

                        if (discordId == Long.parseLong(discordProfile.getId())) {
                            Luma.database.setRemovedConnection(discordId, serverId, connId, 1);
                            exchange.setStatusCode(200);
                            exchange.endExchange();
                        } else {
                            exchange.setStatusCode(403);
                            exchange.endExchange();
                        }
                    }
                });

        Undertow webpageServer = Undertow.builder()
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .addHttpListener(8000, "0.0.0.0")
                .setHandler(new SessionAttachmentHandler(verifyWebpageHandler, sessionManager, cookieConfig))
                .build();

        webpageServer.start();
    }
}
