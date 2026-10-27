package org.example.service;

import com.google.gson.Gson;
import org.example.dto.LeaderboardEntry;
import org.example.redis.RedisPoolProvider;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class LeaderboardCache {
    private static final Logger LOGGER = Logger.getLogger(LeaderboardCache.class.getName());
    private static final int TTL_SECONDS = 3600;
    private static final Gson GSON = new Gson();

    public List<LeaderboardEntry> getTournamentLeaderboard(String tournamentId) {
        String orderKey = tournamentOrderKey(tournamentId);
        String readyKey = orderKey + ":ready";
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            if (!jedis.exists(readyKey)) return null;
            List<String> attemptIds = jedis.zrange(orderKey, 0, -1);
            if (attemptIds.isEmpty()) return List.of();

            List<String> jsonEntries = jedis.mget(attemptEntryKeys(attemptIds));
            if (jsonEntries.size() != attemptIds.size() || jsonEntries.stream().anyMatch(value -> value == null)) {
                return null;
            }
            return deserialize(jsonEntries);
        } catch (JedisException | IllegalStateException e) {
            LOGGER.warning("Redis leaderboard read failed; falling back to MySQL: " + e.getMessage());
            return null;
        }
    }

    public List<LeaderboardEntry> getUserScores(String userId) {
        String scoresKey = userScoresKey(userId);
        String readyKey = scoresKey + ":ready";
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            if (!jedis.exists(readyKey)) return null;
            List<String> attemptIds = jedis.zrevrange(scoresKey, 0, -1);
            if (attemptIds.isEmpty()) return List.of();

            List<String> jsonEntries = jedis.mget(attemptEntryKeys(attemptIds));
            if (jsonEntries.size() != attemptIds.size() || jsonEntries.stream().anyMatch(value -> value == null)) {
                return null;
            }
            return deserialize(jsonEntries);
        } catch (JedisException | IllegalStateException e) {
            LOGGER.warning("Redis user-score read failed; falling back to MySQL: " + e.getMessage());
            return null;
        }
    }

    public void replaceTournamentLeaderboard(String tournamentId, List<LeaderboardEntry> entries) {
        String orderKey = tournamentOrderKey(tournamentId);
        String readyKey = orderKey + ":ready";
        try (Jedis jedis = RedisPoolProvider.getPool().getResource();
             Transaction transaction = jedis.multi()) {
            transaction.del(orderKey, readyKey);
            long position = 0;
            for (LeaderboardEntry entry : entries) {
                position++;
                transaction.zadd(orderKey, position, entry.attemptId());
                transaction.setex(attemptEntryKey(entry.attemptId()), TTL_SECONDS, GSON.toJson(entry));

                String scoresKey = userScoresKey(entry.userId());
                transaction.zadd(scoresKey, completedAtEpochMillis(entry.completedAt()), entry.attemptId());
                transaction.expire(scoresKey, TTL_SECONDS);
                transaction.expire(scoresKey + ":ready", TTL_SECONDS);
            }
            transaction.expire(orderKey, TTL_SECONDS);
            transaction.setex(readyKey, TTL_SECONDS, "1");
            transaction.exec();
        } catch (JedisException | IllegalStateException e) {
            LOGGER.warning("Redis leaderboard write failed; MySQL results remain authoritative: " + e.getMessage());
        }
    }

    public void replaceUserScores(String userId, List<LeaderboardEntry> entries) {
        String scoresKey = userScoresKey(userId);
        String readyKey = scoresKey + ":ready";
        try (Jedis jedis = RedisPoolProvider.getPool().getResource();
             Transaction transaction = jedis.multi()) {
            transaction.del(scoresKey, readyKey);
            for (LeaderboardEntry entry : entries) {
                transaction.zadd(scoresKey, completedAtEpochMillis(entry.completedAt()), entry.attemptId());
                transaction.setex(attemptEntryKey(entry.attemptId()), TTL_SECONDS, GSON.toJson(entry));
            }
            transaction.expire(scoresKey, TTL_SECONDS);
            transaction.setex(readyKey, TTL_SECONDS, "1");
            transaction.exec();
        } catch (JedisException | IllegalStateException e) {
            LOGGER.warning("Redis user-score write failed; MySQL results remain authoritative: " + e.getMessage());
        }
    }

    public void invalidateAttempt(String tournamentId, String userId, String attemptId) {
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            jedis.del(tournamentOrderKey(tournamentId), tournamentOrderKey(tournamentId) + ":ready",
                    userScoresKey(userId), userScoresKey(userId) + ":ready", attemptEntryKey(attemptId));
        } catch (JedisException | IllegalStateException e) {
            LOGGER.warning("Redis cache invalidation failed; cached values will expire: " + e.getMessage());
        }
    }

    private static List<LeaderboardEntry> deserialize(List<String> jsonEntries) {
        List<LeaderboardEntry> entries = new ArrayList<>(jsonEntries.size());
        for (String json : jsonEntries) {
            LeaderboardEntry entry = GSON.fromJson(json, LeaderboardEntry.class);
            if (entry == null) return null;
            entries.add(entry);
        }
        return entries;
    }

    private static String[] attemptEntryKeys(List<String> attemptIds) {
        return attemptIds.stream().map(LeaderboardCache::attemptEntryKey).toArray(String[]::new);
    }

    private static long completedAtEpochMillis(String value) {
        if (value == null) return 0L;
        return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static String tournamentOrderKey(String tournamentId) {
        return "leaderboard:tournament:" + tournamentId;
    }

    private static String userScoresKey(String userId) {
        return "scores:user:" + userId;
    }

    private static String attemptEntryKey(String attemptId) {
        return "score:attempt:" + attemptId;
    }
}
