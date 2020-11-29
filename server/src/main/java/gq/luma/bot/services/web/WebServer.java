package gq.luma.bot.services.web;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.lukaspradel.steamapi.core.exception.SteamApiException;
import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries;
import com.lukaspradel.steamapi.webapi.request.GetPlayerSummariesRequest;
import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory;
import gq.luma.bot.Luma;
import gq.luma.bot.commands.subsystem.permissions.PermissionSet;
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
import io.undertow.util.Methods;
import io.undertow.util.PathTemplateMatch;
import okhttp3.Request;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.oidc.profile.OidcProfile;
import org.pac4j.undertow.context.UndertowWebContext;
import org.pac4j.undertow.handler.CallbackHandler;
import org.pac4j.undertow.handler.LogoutHandler;
import org.pac4j.undertow.handler.SecurityHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.streams.ChannelOutputStream;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class WebServer implements Service {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    private Path VERIFY_DASHBOARD_PATH;
    private Path CDN_PATH;

    private Undertow webpageServer;

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

            UndertowWebContext context = new UndertowWebContext(exchange, securityConfig.getSessionStore());
            ProfileManager profileManager = new ProfileManager(context);
            List<? extends CommonProfile> profiles = profileManager.getAll(true);

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
                String ip = exchange.getRequestHeaders().get("CF-Connecting-IP").getFirst();

                boolean lookupConnectionsSucceeded = lookupConnections(latestDiscordProfile, latestSteamProfile, latestTwitchProfile, serverId);

                if(Luma.database.getUserVerified(Long.parseLong(latestDiscordProfile.getId())) < 1) {
                    attemptToVerifyUser(latestDiscordProfile, serverId, ip);
                } else {
                    // Still try to give them the verified role
                    Bot.api.getServerById(serverId).ifPresent(server -> {
                        Bot.api.getRoleById(VERIFIED_ROLE).ifPresent(role -> {
                            Bot.api.getUserById(latestDiscordProfile.getId()).thenAccept(user -> {
                               if(!user.getRoles(server).contains(role)) {
                                   user.addRole(role);
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

        private Map<Long, LinkedList<UserJoin>> last5JoinTimes = new HashMap<>();

        private class UserJoin {
            private long userId;
            private long time;
            public boolean quarantined = false;

            private UserJoin(long userId, long time) {
                this.userId = userId;
                this.time = time;
            }
        }

        private static final long VERIFIED_ROLE = 558133536784121857L;
        private static final long VERIFICATION_REVIEW_ROLE = 596219515251982347L;

        private static final long VERIFICATION_REVIEW_CHANNEL = 586288946271617024L;
        private static final long VERIFICATION_LOG_CHANNEL = 782042783442927616L;

        private synchronized void attemptToVerifyUser(DiscordProfile profile, long serverId, String ip) {
            Bot.api.getUserById(profile.getId()).thenAccept(user -> Bot.api.getTextChannelById(VERIFICATION_LOG_CHANNEL).ifPresent(channel -> {
                new MessageBuilder()
                        .append("Verification: ")
                        .append(user.getMentionTag())
                        .append(" (").append(user.getDiscriminatedName()).append(")")
                        .send(channel);
            }));


            // Quarantine if users joined in last 5 minutes exceeds 5
            LinkedList<UserJoin> last5Joins = last5JoinTimes.computeIfAbsent(serverId, l -> new LinkedList<>());
            while (last5Joins.peek() != null && last5Joins.peek().time < System.currentTimeMillis() - (1000 * 60 * 5)) {
                last5Joins.pop();
            }
            UserJoin thisJoin = new UserJoin(Long.parseLong(profile.getId()), System.currentTimeMillis());
            last5Joins.push(thisJoin);

            /*
            if(last5Joins.size() > 5) {
                Bot.api.getServerById(serverId).ifPresent(server -> {
                    for (UserJoin join : last5Joins) {
                        if (!join.quarantined) {
                            join.quarantined = true;
                            Bot.api.getUserById(join.userId).thenAccept(user -> {
                                Bot.api.getRoleById(VERIFIED_ROLE).ifPresent( role -> {
                                    server.removeRoleFromUser(user, role);
                                });
                                Bot.api.getRoleById(VERIFICATION_REVIEW_ROLE).ifPresent( role -> {
                                    server.addRoleToUser(user, role);
                                });
                                Bot.api.getTextChannelById(VERIFICATION_REVIEW_CHANNEL).ifPresent(channel -> {
                                    new MessageBuilder()
                                            .append(user)
                                            .append("\nWelcome to the Portal 2 Speedrun Server")
                                            .append("\nIt looks like something went wrong while verifying your profile. Please wait for a few minutes until a Mod can review.")
                                            .append("\nIn the mean time, you can help us by telling us why you joined the server.")
                                            .send(channel);
                                });
                                Bot.api.getTextChannelById(DELETED_MESSAGES_CHANNEL).ifPresent(channel -> {
                                    new MessageBuilder()
                                            .append("Quarantined user ").append(user.getDiscriminatedName()).append(".\n")
                                            .append("Reason:\n")
                                            .append("Joined within 5 minutes of more than 5 other users.")
                                            .send(channel);
                                });
                            });
                        }
                        Luma.database.updateUserRecordVerified(Long.parseLong(profile.getId()), serverId, 1);
                    }
                });
                return;
            }*/

            Bot.api.getServerById(serverId).ifPresent(server -> {
                Bot.api.getUserById(thisJoin.userId).thenAccept(user -> {
                    AtomicBoolean altNotice = new AtomicBoolean(false);

                    StringBuilder altNoticeText = new StringBuilder("Alt notice: ").append(user.getMentionTag())
                            .append(" (").append(user.getDiscriminatedName()).append("\n")
                            .append("Reasons: \n");

                    // Check if the IP address matches an existing user
                    List<Long> prevConnections = Luma.database.getUserConnectionAttemptsByIP(ip);
                    if(prevConnections.size() > 0) {
                        altNotice.set(true);
                        thisJoin.quarantined = true;
                        /*Bot.api.getRoleById(VERIFIED_ROLE).ifPresent(role -> {
                            server.removeRoleFromUser(user, role).join();
                        });
                        Bot.api.getRoleById(VERIFICATION_REVIEW_ROLE).ifPresent(role -> {
                            server.addRoleToUser(user, role).join();
                        });
                        Bot.api.getTextChannelById(VERIFICATION_REVIEW_CHANNEL).ifPresent(channel -> {
                            new MessageBuilder()
                                    .append(user)
                                    .append("\nWelcome to the Portal 2 Speedrun Server")
                                    .append("\nIt looks like something went wrong while verifying your profile. Please wait for a few minutes until a Mod can review.")
                                    .append("\nIn the mean time, you can help us by telling us why you joined the server.")
                                    .send(channel).join();
                        });*/

                        altNoticeText.append("Has the same IP address as users: ");
                        CompletableFuture<?>[] userLookups = prevConnections.stream()
                                .map(id -> Bot.api.getUserById(id).thenAccept(mention ->
                                        altNoticeText.append(mention.getDiscriminatedName()).append(" "))).toArray(CompletableFuture[]::new);
                        altNoticeText.append('\n');

                        CompletableFuture.allOf(userLookups).join();

                        //Luma.database.updateUserRecordVerified(Long.parseLong(profile.getId()), serverId, 1);
                        //return;
                    }

                    Luma.database.getVerifiedConnectionsByUser(thisJoin.userId, serverId).forEach((type, ids) -> {
                        for (String id : ids) {
                            Luma.database.getVerifiedConnectionsById(id, type).stream()
                                    .filter(checkUser -> !checkUser.equals(user))
                                    .forEach(checkUser -> {
                                        altNotice.set(true);
                                        altNoticeText.append("Shares a ").append(type)
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


            Bot.api.getServerById(146404426746167296L).ifPresent(server ->
                    Bot.api.getUserById(profile.getId()).thenAccept(user ->
                            Bot.api.getRoleById(VERIFIED_ROLE).ifPresent( role ->
                                    server.addRoleToUser(user, role))));
            Luma.database.updateUserRecordVerified(Long.parseLong(profile.getId()), serverId, 2);
        }

        private boolean lookupConnections(DiscordProfile discordProfile, SteamOpenIdProfile steamProfile, OidcProfile twitchProfile, long serverId) {
            logger.trace("Discord user accessed verify.luma.gq:");
            logger.trace("Id: "+ discordProfile.getId());
            logger.trace("Name: " + discordProfile.getUsername() + "#" + discordProfile.getDiscriminator());
            //logger.trace("IP: " + exchange.getRequestHeaders().get("CF-Connecting-IP").getFirst());
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
                        id = String.valueOf(Long.valueOf(id) | 0x100000000L);
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

    private class FileServeHandler implements HttpHandler {
        private final ByteBuffer uncompressedBuffer;

        private final boolean supportsBr;
        private ByteBuffer brBuffer;
        private final boolean supportsGzip;
        private ByteBuffer gzipBuffer;

        private FileServeHandler(Path path) throws IOException {
            System.out.println("Loading page " + path.toString() + " =====");
            this.uncompressedBuffer = ByteBuffer.wrap(Files.readAllBytes(path));
            logger.debug("Uncompressed - " + this.uncompressedBuffer.capacity());
            Path brPath = Paths.get(path.toString() + ".br");
            Path gzipPath = Paths.get(path.toString() + ".gz");
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
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            final List<String> res = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING);
            //System.out.println("Got request for resource. =========");
            //System.out.println(exchange.getRequestHeaders().toString());
            //System.out.println("Accept-Encoding Headers: " + res.size());
            //res.forEach(System.out::println);
            //System.out.println(res.size());
            //System.out.println(exchange.getRequestHeaders().count("accept-encoding"));
            //System.out.println(res.get(0));
            //int algo = res.size() > 0 ? getOptimalCompressionAlgoritm(res.get(0).toCharArray()) : 0;
            //System.out.println(algo);
            if(res.contains("br") && supportsBr) {
                //System.out.println("Sending br buffer.");
                brBuffer.rewind();
                exchange.getResponseHeaders().add(Headers.CONTENT_ENCODING, "br");
                exchange.getResponseSender().send(brBuffer);
            } else if(res.contains("gzip") && supportsGzip) {
                //System.out.println("Sending gzip buffer.");
                gzipBuffer.rewind();
                exchange.getResponseHeaders().add(Headers.CONTENT_ENCODING, "gzip");
                exchange.getResponseSender().send(gzipBuffer);
            } else {
                //System.out.println("Sending uncompressed buffer.");
                uncompressedBuffer.rewind();
                exchange.getResponseSender().send(uncompressedBuffer);
            }
        }
    }

    private int getOptimalCompressionAlgoritm(char[] compressionCookie) {
        boolean gzipSupportPersist = false;

        boolean gzipSupport = false;
        boolean brSupport = false;
        int paramRead = 0;
        for(char c : compressionCookie) {
            if(c == ',' || c == ' ') {
                if(brSupport) {
                    return 2;
                }
                gzipSupportPersist = gzipSupportPersist || gzipSupport;
                paramRead = 0;
                continue;
            } else {
                paramRead++;
            }

            if(paramRead == 1) {
                if(c == 'g') {
                    gzipSupport = true;
                } else if (c == 'b') {
                    brSupport = true;
                }
            } else if (paramRead == 2) {
                if(c == 'z') {
                    brSupport = false;
                } else if(c == 'r') {
                    gzipSupport = false;
                } else {
                    brSupport = false;
                    gzipSupport = false;
                }
            } else if (paramRead == 3) {
                brSupport = false;
                if(c != 'i') {
                    gzipSupport = false;
                }
            } else if (paramRead == 4) {
                if (c != 'p') {
                    gzipSupport = false;
                }
            } else {
                gzipSupport = false;
            }
        }
        if(brSupport && gzipSupportPersist) {
          return 3;
        } if(brSupport) {
            return 2;
        } else if (gzipSupportPersist) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public void startService() throws IOException {
        WebPage profilePage = new WebPage(Paths.get(FileReference.webRoot.getAbsolutePath(), "profile", "profile.html"));
        WebPage srcomLoginPage = new WebPage(Paths.get(FileReference.webRoot.getAbsolutePath(), "loginsrcom", "loginsrcom.html"));
        VERIFY_DASHBOARD_PATH = Paths.get(FileReference.webRoot.getAbsolutePath(), "verifydashboard.html");
        initVerifyDashboardPage();
        CDN_PATH = Paths.get(FileReference.webRoot.getAbsolutePath(), "cdn");

        final Config securityConfig = new WebSecConfigFactory().build();
        final InMemorySessionManager sessionManager = new InMemorySessionManager("SessionManager");
        final SessionCookieConfig cookieConfig = new SessionCookieConfig();

        long serverId = 146404426746167296L;

        HttpHandler rootHandler = Handlers.path().addExactPath("/", new FileServeHandler("index.html"));

        HttpHandler cdnHandler = exchange -> {
            String[] split = exchange.getRelativePath().split("/");
            if(split.length < 1) {
                return;
            }
            String filename = split[split.length - 1];

            //System.out.println("Rel uncompressedBuffer: " + exchange.getRelativePath());
            logger.trace("Cdn request for file name: " + filename);

            for(Path path : Files.list(CDN_PATH).collect(Collectors.toCollection(ArrayList::new))) {
                //System.out.println(path.getFileName().toString() + "=?=" + filename);
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

        HttpHandler verifyWebpageHandler = Handlers.routing()
                .get("/", ProfileRestrictedHandler.build(profilePage, securityConfig, true))
                .get("/profile/bundle.js", new FileServeHandler("profile", "bundle.js"))
                .get("/login/srcom", ProfileRestrictedHandler.build(srcomLoginPage, securityConfig, true))
                .get("/loginsrcom/bundle.js", new FileServeHandler("loginsrcom", "bundle.js"))
                .get("/login/discord", exchange -> {
                    securityConfig.getClients().findClient("discord").redirect(new UndertowWebContext(exchange));
                    exchange.endExchange();
                })
                .get("/login/steam", SecurityHandler.build(exchange -> {
                    securityConfig.getClients().findClient("steam").redirect(new UndertowWebContext(exchange));
                    exchange.endExchange();
                }, securityConfig, "discord"))
                .get("/login/twitch", SecurityHandler.build(exchange -> {
                    securityConfig.getClients().findClient("twitch").redirect(new UndertowWebContext(exchange));
                    exchange.endExchange();
                }, securityConfig, "discord"))
                .get("/dashboard", SecurityHandler.build(exchange -> {
                    serveVerifyDashboard(exchange, Bot.api, 146404426746167296L);
                }, securityConfig, "discord", "discord_admin"))
                .get("/dashboard/twitch", SecurityHandler.build(new FileServeHandler("twitchverifydash", "twitchverifydash.html"), securityConfig, "discord", "discord_admin"))
                .get("/twitchverifydash/bundle.js", SecurityHandler.build(new FileServeHandler("twitchverifydash", "bundle.js"), securityConfig, "discord", "discord_admin"))
                .get("/callback", CallbackHandler.build(securityConfig, null, true))
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
                            long discordId = Long.parseLong(pathMatch.getParameters().get("did"));;
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
                                if (discordId == Long.valueOf(discordProfile.getId()) && !apiKey.isEmpty()) {
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
                .add(Methods.PATCH, "/user/{did}/connections/twitch/{connid}", new ProfileRestrictedHandler(null, securityConfig, false) {
                    @Override
                    public void servePage(HttpServerExchange exchange, DiscordProfile discordProfile, SteamOpenIdProfile steamProfile, OidcProfile twitchProfile) {
                        PathTemplateMatch pathMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
                        long discordId = Long.parseLong(pathMatch.getParameters().get("did"));
                        String connId = pathMatch.getParameters().get("connid");

                        if (discordProfile.getRoles().contains("ROLE_DISCORD_ADMIN")) {
                            int notifyValue = extractTopLevelJsonInt(readRequestBody(exchange, 256), "notify");
                            if(notifyValue >= 0 && notifyValue <= 2) {
                                Luma.database.setNotifyConnection(discordId, serverId, connId, notifyValue);
                                exchange.setStatusCode(200);
                                exchange.endExchange();
                            } else {
                                exchange.setStatusCode(400);
                                exchange.endExchange();
                            }
                        } else if (discordId == Long.valueOf(discordProfile.getId())) {
                            int notifyValue = extractTopLevelJsonInt(readRequestBody(exchange, 256), "notify");
                            if(notifyValue == 0 || notifyValue == 1) {
                                Luma.database.setNotifyConnection(discordId, serverId, connId, notifyValue);
                                exchange.setStatusCode(200);
                                exchange.endExchange();
                            } else {
                                exchange.setStatusCode(400);
                                exchange.endExchange();
                            }
                        } else {
                            exchange.setStatusCode(403);
                            exchange.endExchange();
                        }
                    }
                })
                .delete("/user/{did}/connections/{connid}", new ProfileRestrictedHandler(null, securityConfig, false) {
                    @Override
                    public void servePage(HttpServerExchange exchange, DiscordProfile discordProfile, SteamOpenIdProfile steamProfile, OidcProfile twitchProfile) {
                        PathTemplateMatch pathMatch = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
                        long discordId = Long.parseLong(pathMatch.getParameters().get("did"));
                        String connId = pathMatch.getParameters().get("connid");

                        if (discordId == Long.valueOf(discordProfile.getId())) {
                            Luma.database.setRemovedConnection(discordId, serverId, connId, 1);
                            exchange.setStatusCode(200);
                            exchange.endExchange();
                        } else {
                            exchange.setStatusCode(403);
                            exchange.endExchange();
                        }
                    }
                })
                .get("/connections/twitch", new ProfileRestrictedHandler(null, securityConfig, false) {
                    @Override
                    public void servePage(HttpServerExchange exchange, DiscordProfile discordProfile, SteamOpenIdProfile steamProfile, OidcProfile twitchProfile) {

                        if (discordProfile.getRoles().contains("ROLE_DISCORD_ADMIN")) {
                            try (StreamSinkChannel sinkChannel = exchange.getResponseChannel()){
                                try (JsonGenerator generator = Luma.jsonFactory.createGenerator(new BufferedOutputStream(new ChannelOutputStream(sinkChannel)))) {
                                    Luma.database.writeStreamsJson(serverId, generator);
                                    generator.flush();
                                }
                                sinkChannel.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            exchange.endExchange();
                        } else {
                            exchange.setStatusCode(403);
                            exchange.endExchange();
                        }
                    }
                });


        webpageServer = Undertow.builder()
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .addHttpListener(80, "0.0.0.0")
                .setHandler(new SessionAttachmentHandler(exchange -> {
                    logger.trace("Call to host name: " + exchange.getHostName());
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
                        //case "api.luma.gq":
                            //apiHandler.handleRequest(exchange);
                            //break;
                        default:
                            logger.debug("No host name found for host: " + exchange.getHostName());
                            exchange.setStatusCode(404);
                            exchange.getResponseSender().send("Invalid subdomain.", IoCallback.END_EXCHANGE);
                    }
                }, sessionManager, cookieConfig))
                .build();

        webpageServer.start();
    }

    private byte[] readRequestBody(HttpServerExchange exchange, int size) {
        if (exchange.isRequestChannelAvailable()) {
            try(StreamSourceChannel channel = exchange.getRequestChannel()) {
                byte[] patchData = new byte[size];
                ByteBuffer buffer = ByteBuffer.wrap(patchData);
                channel.read(buffer);
                return patchData;
            } catch (IOException e) {
                e.printStackTrace();
                exchange.setStatusCode(403);
                exchange.endExchange();
                return null;
            }
        } else {
            exchange.setStatusCode(403);
            logger.debug("Channel is not available for PATCH request");
            exchange.endExchange();
            return null;
        }
    }

    private int extractTopLevelJsonInt(byte[] json, String key) {
        if(json == null) {
            return -1;
        }
        try(JsonParser parser = Luma.jsonFactory.createParser(json)) {
            while(!parser.isClosed()) {
                if (parser.nextToken() == JsonToken.FIELD_NAME) {
                    if (key.equals(parser.getCurrentName())) {
                        if (parser.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                            logger.debug(key + " is not a string for inputted data");
                            return -1;
                        }
                        return parser.getValueAsInt();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private boolean userHasPerm(long serverId, long userId, PermissionSet.Permission permission) {
        try {
            for (PermissionSet checkPerm : Luma.database.getPermission(serverId, PermissionSet.PermissionTarget.USER, userId)) {
                if (checkPerm.effectivelyHasPermission(permission)) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
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

    private void serveVerifyDashboard(HttpServerExchange exchange, DiscordApi api, long serverId) {
        StringBuilder sb = new StringBuilder();
        sb.append(verifyDashboardBasePage, 0, userBoxesRep);

        Server server = api.getServerById(serverId).orElse(null);

        if(server != null) {
            final Collection<User> members = server.getMembers();

            for(User user : members) {
                sb.append("<div class=\"userbox hidden\" name=\"").append(user.getDiscriminatedName()).append("\">");
                boolean userIsVerified = Luma.database.getUserVerified(user.getId()) == 2;
                boolean userHasVerifiedRole = user.getRoles(server).stream().anyMatch(role -> role.getId() == 558133536784121857L);
                if(userIsVerified) {
                    // Add the checkmark
                    sb.append("<svg class=\"tablemargin\" width=\"30\" height=\"30\" viewBox=\"0 0 48 48\" fill=\"none\" stroke=\"green\" stroke-width=\"4\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><polyline points=\"40 12 18 34 8 24\"></polyline></svg>");
                } else if(userHasVerifiedRole) {
                    // Add the question mark
                    sb.append("<svg class=\"tablemargin\" width=\"30\" height=\"30\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"yellow\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"10\"></circle><uncompressedBuffer d=\"M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3\"></uncompressedBuffer><line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"17\"></line></svg>");
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
                    Luma.database.writeConnectionBoxes(user.getId(), serverId, sb);
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
