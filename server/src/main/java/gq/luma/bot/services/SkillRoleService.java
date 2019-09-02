package gq.luma.bot.services;

import gq.luma.bot.Luma;
import gq.luma.bot.services.apis.IVerbApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SkillRoleService implements Service {

    private static Logger logger = LoggerFactory.getLogger(SkillRoleService.class);

    private static final int FIRST_SP_CHAPTER = 7;
    private static final int FIRST_COOP_CHAPTER = 1;

    private long milliRefreshTime = 1000 * 60 * 15; // 15 minutes

    private ScheduledFuture<?> refreshFuture;
    private Instant latestProccessedScore;

    private static final int FIRST_COOP_MAP_INDEX = 59;
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
            45476, // Laser Crusher
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

    // This should be a full mirror of the Iverb scores database.
    private HashMap<Integer, ConcurrentSkipListMap<Integer, ArrayList<IVerbApi.ScoreMetadata>>> scores;

    public SkillRoleService() {
        this.scores = new HashMap<>();
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
                        if(latestProccessedScore == null || metadata.timeGained.isAfter(latestProccessedScore)) {
                            latestProccessedScore = metadata.timeGained;
                        }
                    });

            this.scores.put(id, mapScores);
        }

        logger.info("Fetched all map scores on board.iverb.me. Fetched " + scoreCount.get() + " scores and used " + (totalSize/1000f) + "kb of data.");

        refreshFuture = Luma.schedulerService
                .scheduleAtFixedRate(this::runChangelogFetch, milliRefreshTime, milliRefreshTime, TimeUnit.MILLISECONDS);
    }

    private void runChangelogFetch() {
        logger.debug("Fetching score updates on board.iverb.me...");

        Set<Long> updatedUsers = new HashSet<>();

        AtomicReference<Instant> newLatestProcessedScore = new AtomicReference<>();

        long bytesRead = IVerbApi.fetchScoreUpdates(1,
                scoreUpdate -> scoreUpdate.metadata.timeGained.isAfter(latestProccessedScore), // Only fetch scores that are after the latest processed score.
                scoreUpdate -> {
                    var mapScores = scores.get(scoreUpdate.mapId);

                    // If the player's rank changed, add them to the update queue
                    if(scoreUpdate.postRank != scoreUpdate.preRank) {
                        updatedUsers.add(scoreUpdate.metadata.steamId);
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
                                metadatas.forEach(m -> updatedUsers.add(m.steamId));
                            }
                            rank.addAndGet(metadatas.size());
                        } else {
                            mapScores.remove(score);
                            metadatas.forEach(m -> updatedUsers.add(m.steamId));
                        }
                    });

                    if(newLatestProcessedScore.get() == null
                            || scoreUpdate.metadata.timeGained.isAfter(newLatestProcessedScore.get())) {
                        newLatestProcessedScore.set(scoreUpdate.metadata.timeGained);
                    }
                });

        logger.info("Fetched score updates. Fetched " + (bytesRead/1000f) + "kb of data and updating " + updatedUsers.size() + " players.");

        latestProccessedScore = newLatestProcessedScore.get();
        updatedUsers.forEach(this::onScoreUpdate);
    }

    private void onScoreUpdate(long steamId) {
        logger.debug("Score updated for steam account: " + steamId);

        // TODO: Update discord roles here.
    }

    private float calculateSpPoints(long steamId) {
        float[] totalSpPoints = { 0f };

        for(int i = 0; i < FIRST_COOP_MAP_INDEX; i++) {
            sumMapPoints(steamId, totalSpPoints, i);
        }

        return totalSpPoints[0];
    }

    private float calculateCoopPoints(long steamId) {
        float[] totalCoopPoints = { 0f };

        for(int i = FIRST_COOP_MAP_INDEX; i < IVERB_MAP_IDS.length; i++) {
            sumMapPoints(steamId, totalCoopPoints, i);
        }

        return totalCoopPoints[0];
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

    private float calculatePoints(int rank) {
        float num = (200f - (rank - 1f));
        return Math.max(1f, (num * num)/200f);
    }

    @Override
    public void reload() throws IOException {
        refreshFuture.cancel(false);

        startService();
    }
}
