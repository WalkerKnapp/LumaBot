package gq.luma.bot.services;

import com.fasterxml.jackson.core.JsonFactory;
import com.walker.jspeedrun.api.JSpeedrun;
import com.walker.jspeedrun.api.leaderboards.Leaderboard;
import com.walker.jspeedrun.api.run.Run;
import gq.luma.bot.Luma;
import gq.luma.bot.services.apis.IVerbApi;
import okhttp3.OkHttpClient;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SkillRoleService implements Service {

    private static Logger logger = LoggerFactory.getLogger(SkillRoleService.class);

    private static final int FIRST_SP_CHAPTER = 7;
    private static final int FIRST_COOP_CHAPTER = 1;

    private long milliRefreshTime = 1000 * 60 * 15; // 15 minutes

    private ScheduledFuture<?> refreshFuture;
    private Instant latestProccessedScore;

    private JSpeedrun jSpeedrun;

    private static final int FIRST_COOP_MAP_INDEX = 60;
    private static final int[] IVERB_MAP_IDS = {
            62761, // Container Ride
            62758, // Portal Carousel
            47458, // Portal Gun
            47455, // Smooth Jazz
            47452, // Cube Momentum
            47106, // Future Starter
            62763, // Secret Panel
            62759, // Wakeup
            47735, // Incinerator

            62765, // Laser Intro
            47736, // Laser Stairs
            47738, // Dual Lasers
            47742, // Laser Over Goo
            62767, // Catapult Intro
            47744, // Trust Fling
            47465, // Pit Flings
            47746, // Fizzler Intro

            47748, // Ceiling Catapult
            47751, // Ricochet
            47752, // Bridge Intro
            47755, // Bridge the Gap
            47756, // Turret Intro
            47759, // Laser Relays
            47760, // Turret Blocker
            47763, // Laser vs Turret
            47764, // Pull the Rug

            47766, // Column Blocker
            47768, // Laser Chaining
            47770, // Triple Laser
            47773, // Jail Break
            47774, // Escape

            47776, // Turret Factory
            47779, // Turret Sabotage
            47780, // Neurotoxin Sabotage
            62771, // Core

            47783, // Underground
            47784, // Cave Johnson
            47787, // Repulsion Intro
            47468, // Bomb Flings
            47469, // Crazy Box
            47472, // PotatOS

            47791, // Propulsion Intro
            47793, // Propulsion Flings
            47795, // Conversion Intro
            47798, // Three Gels

            88350, // Test
            47800, // Funnel Intro
            47802, // Ceiling Button
            47804, // Wall Button
            47806, // Polarity
            47808, // Funnel Catch
            47811, // Stop the Box
            47813, // Laser Catapult
            47815, // Laser Platform
            47817, // Propulsion Catch
            47819, // Repulsion Polarity

            62776, // Finale 1
            47821, // Finale 2
            47824, // Finale 3
            47456, // Finale 4

            47741, // Doors
            47825, // Buttons
            47828, // Lasers
            47829, // Rat Maze
            45467, // Laser Crusher
            46362, // Behind the Scenes

            47831, // Flings
            47833, // Infinifling
            47835, // Team Retrieval
            47837, // Vertical Flings
            47840, // Catapults
            47841, // Multifling
            47844, // Fling Crushers
            47845, // Industrial Fan

            47848, // Cooperative Bridges
            47849, // Bridge Swap
            47854, // Fling Block
            47856, // Catapult Block
            47858, // Bridge Fling
            47861, // Turret Walls
            52642, // Turret Assassin
            52660, // Bridge Testing

            52662, // Cooperative Funnels
            52663, // Funnel Drill
            52665, // Funnel Catch
            52667, // Funnel Laser
            52671, // Cooperative Polarity
            52687, // Funnel Hop
            52689, // Advanced Polarity
            52691, // Funnel Maze
            52777, // Turret Warehouse

            52694, // Repulsion Jumps
            52711, // Double Bounce
            52714, // Bridge Repulsion
            52715, // Wall Repulsion
            52717, // Propulsion Crushers
            52735, // Turret Ninja
            52738, // Propulsion Retrieval
            52740, // Vault Entrance

            49341, // Separation
            49343, // Triple Axis
            49345, // Catapult Catch
            49347, // Bridge Gels
            49349, // Maintenance
            49351, // Bridge Catch
            52757, // Double Lift
            52759, // Gel Maze
            48287 // Crazier Box
    };

    private static final long ELITE_ROLE = 574794615971119135L;
    private static final long ADVANCED_ROLE = 608364011028742162L;
    private static final long PROFESSIONALS_ROLE = 313760989180985344L;
    private static final long INTERMEDIATE_ROLE = 574794742462677008L;
    private static final long BEGINNER_ROLE = 146432621071564800L;

    // This should be a full mirror of the Iverb scores database.
    // <Map id, <Rank, List<Scores>>>
    private HashMap<Integer, ConcurrentSkipListMap<Integer, ArrayList<IVerbApi.ScoreMetadata>>> scores;
    // Updated every cycle, a list of the people in the top 3 ranks
    // <Rank, List<User ids>>
    private ArrayList<Long> top3Users;

    private Leaderboard spLeaderboard;
    private Leaderboard coopAMCLeaderboard;

    public SkillRoleService() {
        this.jSpeedrun = new JSpeedrun();

        this.scores = new HashMap<>();
        this.top3Users = new ArrayList<>();
    }

    @Override
    public void startService() throws IOException {
        latestProccessedScore = null;

        long totalSize = 0;
        AtomicInteger scoreCount = new AtomicInteger();

        for (int id : IVERB_MAP_IDS) {
            ConcurrentSkipListMap<Integer, ArrayList<IVerbApi.ScoreMetadata>> mapScores = new ConcurrentSkipListMap<>();

            totalSize += IVerbApi.fetchMapScores(id, (score, metadata) -> {
                        scoreCount.incrementAndGet();
                        // Add score to the scores map
                        mapScores.computeIfAbsent(score, s -> new ArrayList<>())
                                .add(metadata);
                        // Set the latest processed timestamp to the earliest timestamp found.
                        if(metadata.timeGained != null &&
                                (latestProccessedScore == null || metadata.timeGained.isAfter(latestProccessedScore))) {
                            latestProccessedScore = metadata.timeGained;
                        }
                    });

            this.scores.put(id, mapScores);
        }

        logger.info("Fetched all map scores on board.iverb.me. Fetched " + scoreCount.get() + " scores and used " + (totalSize/1000f) + "kb of data.");

        refreshFuture = Luma.schedulerService
                .scheduleAtFixedRate(this::runChangelogFetch, milliRefreshTime, milliRefreshTime, TimeUnit.MILLISECONDS);

        // Update all users on bot start.
        Bot.api.getServerById(146404426746167296L)
                .ifPresent(server -> server.getMembers().forEach(this::onScoreUpdate));
    }

    private void runChangelogFetch() {
        logger.debug("Fetching score updates on board.iverb.me...");

        Set<Long> updatedSteamUsers = new HashSet<>();
        Set<Long> updatedSrcomUsers = new HashSet<>();

        AtomicReference<Instant> newLatestProcessedScore = new AtomicReference<>();

        long dataFetched = 0;
        try {
            dataFetched = IVerbApi.fetchScoreUpdates(1,
                    scoreUpdate -> scoreUpdate.metadata.timeGained.isAfter(latestProccessedScore), // Only fetch scores that are after the latest processed score.
                    scoreUpdate -> {
                        var mapScores = scores.get(scoreUpdate.mapId);

                        // If the player's rank changed, add them to the update queue
                        if(scoreUpdate.postRank != scoreUpdate.preRank) {
                            updatedSteamUsers.add(scoreUpdate.metadata.steamId);
                        }

                        // Clean out obsolete scores from the Player
                        mapScores.forEach((score, metadatas) -> {
                            if(score > scoreUpdate.score) {
                                metadatas.removeIf(m -> m.steamId == scoreUpdate.metadata.steamId);
                                if(metadatas.isEmpty()) {
                                    mapScores.remove(score);
                                }
                            }
                        });

                        // Add new score
                        mapScores.computeIfAbsent(scoreUpdate.score, s -> new ArrayList<>()).add(scoreUpdate.metadata);

                        // Clean out other scores that are now not top 200 or otherwise need to be updated
                        AtomicInteger rank = new AtomicInteger(1);
                        mapScores.forEach((score, metadatas) -> {
                            if(rank.get() <= 200) {
                                // If the rank is below (greater than) the new score, it was likely updated
                                if(rank.get() > scoreUpdate.postRank) {
                                    metadatas.forEach(m -> updatedSteamUsers.add(m.steamId));
                                }
                                rank.addAndGet(metadatas.size());
                            } else {
                                mapScores.remove(score);
                                metadatas.forEach(m -> updatedSteamUsers.add(m.steamId));
                            }
                        });

                        if(newLatestProcessedScore.get() == null
                                || scoreUpdate.metadata.timeGained.isAfter(newLatestProcessedScore.get())) {
                            newLatestProcessedScore.set(scoreUpdate.metadata.timeGained);
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to fetch score updates", e);
        }

        // With the new elite qualification,
        //updatedSteamUsers.addAll(updateTop3());

        logger.info("Fetched iVerb score updates. Fetched " + (dataFetched/1000f) +  "kb of data and updating " + updatedSteamUsers.size() + " players.");

        if(newLatestProcessedScore.get() != null) {
            latestProccessedScore = newLatestProcessedScore.get();
        }

        logger.debug("Fetching leaderboard from speedrun.com...");

        CompletableFuture.allOf(
                jSpeedrun.getCategoryLeaderboard("", "").thenAccept(response -> {
                    spLeaderboard = response.getData().get(0);
                }),
                jSpeedrun.getCategoryLeaderboard("", "").thenAccept(response -> {
                    coopAMCLeaderboard = response.getData().get(0);
                })).join();


        updatedSteamUsers.forEach(this::onScoreUpdate);
    }

    /**
     *
     * @return The users that need to be updated.
     */
    private Set<Long> updateTop3() {
        // <Steam id>
        ArrayList<Long> allUsers = new ArrayList<>();

        this.scores.forEach((mapId, mapScores) -> mapScores.forEach((rank, rankScores) -> {
            rankScores.forEach(metadata -> {
                if(!allUsers.contains(metadata.steamId)) {
                    allUsers.add(metadata.steamId);
                }
            });
        }));

        // <Score, List<User id>>
        ConcurrentSkipListMap<Integer, List<Long>> allOveralls = new ConcurrentSkipListMap<>();
        for(long steamId : allUsers) {
            int overallScore = calculateRoundedTotalPoints(steamId);
            allOveralls.computeIfAbsent(overallScore, s -> new ArrayList<>())
                    .add(steamId);
        }

        Set<Long> updatesNeeded = new HashSet<>();
        ArrayList<Long> newTop3 = new ArrayList<>();

        // Pop the 3 highest scores
        for(int i = 0; i < 3; i++) {
            var lastEntry = allOveralls.pollLastEntry();
            for(long steamId : lastEntry.getValue()) {
                newTop3.add(steamId);
                if(!this.top3Users.contains(steamId)) {
                    updatesNeeded.add(steamId);
                }
            }
        }

        // Iterate through the old and see if anyone lost their spot
        for(long steamId : this.top3Users) {
            if(!newTop3.contains(steamId)) {
                updatesNeeded.add(steamId);
            }
        }

        this.top3Users = newTop3;

        return updatesNeeded;
    }

    private void onScoreUpdate(long steamId) {
        logger.debug("Score updated for steam account: " + steamId);

        for(User user : Luma.database.getVerifiedConnectionsById(steamId, "steam")) {
            onScoreUpdate(user);
        }
    }

    private void onScoreUpdate(User discordUser) {

        AtomicBoolean elite = new AtomicBoolean(false);
        AtomicBoolean professionals = new AtomicBoolean(false);
        AtomicBoolean advanced = new AtomicBoolean(false);
        AtomicBoolean intermediate = new AtomicBoolean(false);
        AtomicBoolean beginner = new AtomicBoolean(false);

        final Instant oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        Luma.database.getVerifiedConnectionsByUser(discordUser.getId(), 146404426746167296L)
                .forEach((type, ids) -> {
                    switch (type) {
                        case "steam":
                            // Check a user's iverb account(s).
                            for(String steamIdString : ids) {
                                long steamId = Long.parseLong(steamIdString);
                                float spPoints = calculateSpPoints(steamId);
                                float coopPoints = calculateCoopPoints(steamId);
                                int totalPointsRounded = Math.round(spPoints) + Math.round(coopPoints);
                                Instant latestScore = getLatestScore(steamId);

                                // Elite Qualifications
                                elite.compareAndSet(false, totalPointsRounded >= 20000);
                                //elite.compareAndSet(false, top3Users.contains(steamId));

                                // Professionals Qualifications
                                professionals.compareAndSet(false, totalPointsRounded >= 16500);
                                professionals.compareAndSet(false, spPoints >= 9500);
                                professionals.compareAndSet(false, coopPoints >= 8000);

                                // Advanced Qualifications
                                advanced.compareAndSet(false, spPoints >= 7000);
                                advanced.compareAndSet(false, coopPoints >= 6000);

                                // Intermediate Qualifications
                                intermediate.compareAndSet(false, spPoints >= 2000);
                                intermediate.compareAndSet(false, coopPoints >= 2500);

                                // Beginner Qualifications
                                beginner.compareAndSet(false, latestScore.isAfter(oneMonthAgo));
                            }
                            break;
                        case "srcom":
                            // Check a user's speedrun.com account(s).
                            for(String srcomId : ids) {
                                double singlePlayerTime = Double.MAX_VALUE;
                                double amcTimePlayerBelowRank = Double.MAX_VALUE;
                                double amcTimePlayerAboveRank = Double.MAX_VALUE;

                                OffsetDateTime latestRun = null;

                                // Check single player leaderboards
                                for(Leaderboard.LeaderboardPlace place : spLeaderboard.runs) {
                                    // Check that the run is verified
                                    if(!place.run.status.status.equalsIgnoreCase("verified")) {
                                        break;
                                    }

                                    boolean containsPlayer = false;
                                    for(Run.Player player : place.run.players) {
                                        containsPlayer = containsPlayer || (player.id.equals(srcomId));
                                    }

                                    if(containsPlayer) {
                                        // Check if this run is the latest
                                        if(place.run.submitted != null) {
                                            if(latestRun == null) {
                                                latestRun = place.run.submitted;
                                            } else {
                                                if(place.run.submitted.isAfter(latestRun)) {
                                                    latestRun = place.run.submitted;
                                                }
                                            }
                                        }

                                        if(singlePlayerTime > place.run.times.primaryTime) {
                                            singlePlayerTime = place.run.times.primaryTime;
                                        }
                                    }
                                }

                                // Check coop leaderboards
                                for(Leaderboard.LeaderboardPlace place : coopAMCLeaderboard.runs) {
                                    // Check that the run is verified
                                    if(!place.run.status.status.equalsIgnoreCase("verified")) {
                                        break;
                                    }

                                    boolean containsPlayer = false;
                                    Run.Player coopPartner = null;

                                    for(Run.Player player : place.run.players) {
                                        if(player.id.equals(srcomId)) {
                                            containsPlayer = true;
                                        } else {
                                            coopPartner = player;
                                        }
                                    }

                                    if(containsPlayer) {
                                        // Check if this run is the latest
                                        if(place.run.submitted != null) {
                                            if(latestRun == null) {
                                                latestRun = place.run.submitted;
                                            } else {
                                                if(place.run.submitted.isAfter(latestRun)) {
                                                    latestRun = place.run.submitted;
                                                }
                                            }
                                        }

                                        // Get the coop partner's highest rank
                                        int partnerHighestRank = Integer.MAX_VALUE;

                                        if(coopPartner != null) {
                                            for (Leaderboard.LeaderboardPlace partnerPlace : coopAMCLeaderboard.runs) {
                                                // Check that the run is verified
                                                if(!partnerPlace.run.status.status.equalsIgnoreCase("verified")) {
                                                    break;
                                                }

                                                boolean containsPartner = false;
                                                for (Run.Player player : place.run.players) {
                                                    containsPartner = containsPartner || (player.id.equals(coopPartner.id));
                                                }

                                                if(containsPartner && partnerHighestRank > partnerPlace.place) {
                                                    partnerHighestRank = partnerPlace.place;
                                                }
                                            }
                                        }

                                        if (amcTimePlayerBelowRank > place.run.times.primaryTime && place.place <= partnerHighestRank) {
                                            amcTimePlayerBelowRank = place.run.times.primaryTime;
                                        }

                                        if (amcTimePlayerAboveRank > place.run.times.primaryTime && place.place > partnerHighestRank) {
                                            amcTimePlayerAboveRank = place.run.times.primaryTime;
                                        }
                                    }
                                }

                                // Elite Qualifications
                                elite.compareAndSet(false, singlePlayerTime < (1 * 60 * 60) + (1 * 60)); // Sub 1:01
                                elite.compareAndSet(false, amcTimePlayerAboveRank < (28 * 60)); // Sub 28:00
                                elite.compareAndSet(false, amcTimePlayerBelowRank < (28 * 60)); // Sub 28:00

                                // Professionals Qualifications
                                professionals.compareAndSet(false, singlePlayerTime < (1 * 60 * 60) + (4 * 60) + 30); // Sub 1:04:30
                                professionals.compareAndSet(false, amcTimePlayerAboveRank < (29 * 60)); // Sub 29:00
                                professionals.compareAndSet(false, amcTimePlayerBelowRank < (29 * 60)); // Sub 29:00

                                // Advanced Qualifications
                                advanced.compareAndSet(false, singlePlayerTime < (1 * 60 * 60) + (9 * 60)); // Sub 1:09
                                advanced.compareAndSet(false, amcTimePlayerAboveRank < (30 * 60)); // Sub 30:00
                                advanced.compareAndSet(false, amcTimePlayerBelowRank < (32 * 60)); // Sub 32:00

                                // Intermediate Qualifications
                                intermediate.compareAndSet(false, singlePlayerTime < (1 * 60 * 60) + (20 * 60)); // Sub 1:20
                                intermediate.compareAndSet(false, amcTimePlayerAboveRank < (40 * 60)); // Sub 40:00
                                intermediate.compareAndSet(false, amcTimePlayerBelowRank < (40 * 60)); // Sub 40:00

                                // Beginner Qualifications
                                if(latestRun != null) {
                                    beginner.compareAndSet(false, latestRun.isAfter(OffsetDateTime.now().minus(6, ChronoUnit.MONTHS))); // Run in the last 6 months
                                }
                            }
                            break;
                        default:
                            break;
                    }
                });

        Bot.api.getRoleById(ELITE_ROLE)
                .ifPresentOrElse(role -> giveOrTakeRole(role, discordUser, elite),
                        () -> logger.error("Failed to find elite role."));

        Bot.api.getRoleById(PROFESSIONALS_ROLE)
                .ifPresentOrElse(role -> giveOrTakeRole(role, discordUser, professionals),
                        () -> logger.error("Failed to find professionals role."));

        Bot.api.getRoleById(ADVANCED_ROLE)
                .ifPresentOrElse(role -> giveOrTakeRole(role, discordUser, advanced),
                        () -> logger.error("Failed to find advanced role."));

        Bot.api.getRoleById(INTERMEDIATE_ROLE)
                .ifPresentOrElse(role -> giveOrTakeRole(role, discordUser, intermediate),
                        () -> logger.error("Failed to find intermediate role."));

        Bot.api.getRoleById(BEGINNER_ROLE)
                .ifPresentOrElse(role -> giveOrTakeRole(role, discordUser, beginner),
                        () -> logger.error("Failed to find beginner role."));
    }

    private void giveOrTakeRole(Role role, User discordUser, AtomicBoolean state) {
        if(!role.getUsers().contains(discordUser)) {
            if(state.get()) {
                logger.debug("Giving {} {} role", discordUser.getDiscriminatedName(), role.getName());
                discordUser.addRole(role);
            }
        } else {
            if(!state.get()) {
                logger.debug("Removing {} {} role", discordUser.getDiscriminatedName(), role.getName());
                // TODO: Remove role
            }
        }
    }

    public float calculateSpPoints(long steamId) {
        float[] totalSpPoints = { 0f };

        for(int i = 0; i < FIRST_COOP_MAP_INDEX; i++) {
            sumMapPoints(steamId, totalSpPoints, i);
        }

        return totalSpPoints[0];
    }

    public float calculateCoopPoints(long steamId) {
        float[] totalCoopPoints = { 0f };

        for(int i = FIRST_COOP_MAP_INDEX; i < IVERB_MAP_IDS.length; i++) {
            sumMapPoints(steamId, totalCoopPoints, i);
        }

        return totalCoopPoints[0];
    }

    public int calculateRoundedTotalPoints(long steamId) {
        return Math.round(calculateSpPoints(steamId)) + Math.round(calculateCoopPoints(steamId));
    }

    private void sumMapPoints(long steamId, float[] totalCoopPoints, int mapId) {
        var mapScores = scores.get(IVERB_MAP_IDS[mapId]);

        AtomicInteger rank = new AtomicInteger(1);
        mapScores.forEach((score, metadatas) -> {
            metadatas.forEach(metadata -> {
                if(metadata.steamId == steamId) {
                    totalCoopPoints[0] += calculatePoints(rank.get());
                }
            });
            rank.addAndGet(metadatas.size());
        });
    }

    private Instant getLatestScore(long steamId) {
        AtomicReference<Instant> latestScore = new AtomicReference<>(Instant.MIN);
        this.scores.forEach((mapId, mapScores) ->
                mapScores.forEach((rank, rankScores) ->
                        rankScores.forEach(meta -> {
            if(meta.steamId == steamId && meta.timeGained != null) {
                if(meta.timeGained.isAfter(latestScore.get())) {
                    latestScore.set(meta.timeGained);
                }
            }
        })));
        return latestScore.get();
    }

    private float calculatePoints(int rank) {
        float num = (200f - (rank - 1f));
        return Math.max(1f, (num * num)/200f);
    }

    @Override
    public void reload() throws IOException {
        refreshFuture.cancel(false);

        startService();
    }

    public static void main(String[] args) throws IOException {
        Luma.okHttpClient = new OkHttpClient();
        Luma.jsonFactory = new JsonFactory();
        SkillRoleService service = new SkillRoleService();

        service.startService();

        Scanner scanner = new Scanner(System.in);
        String line;
        while(!(line = scanner.nextLine()).equals("stop")) {
            long steamId = Long.parseLong(line);

            float spPoints = service.calculateSpPoints(steamId);
            float coopPoints = service.calculateCoopPoints(steamId);

            System.out.println("Points for SteamId: " + steamId);
            System.out.println("Sp: " + spPoints);
            System.out.println("Coop: " + coopPoints);
            //TODO: Remember that Sp points and coop points are rounded separately before being added for overall points
            System.out.println("Overall: " + (spPoints + coopPoints));
        }
    }
}
