package org.example.service;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.example.model.Tournament;
import org.example.redis.RedisPoolProvider;
import org.example.util.JsonUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import java.lang.reflect.Type;
import java.util.List;
import java.util.logging.Logger;

public class TournamentCache {
    private static final Logger LOGGER = Logger.getLogger(TournamentCache.class.getName());
    private static final int TTL_SECONDS = 60;
    private static final String ALL_TOURNAMENTS_KEY = "tournaments:all";
    private static final Type TOURNAMENT_LIST_TYPE = new TypeToken<List<Tournament>>() { }.getType();

    public List<Tournament> getTournamentList() {
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            String json = jedis.get(ALL_TOURNAMENTS_KEY);
            return json == null ? null : JsonUtil.fromJson(json, TOURNAMENT_LIST_TYPE);
        } catch (JedisException | IllegalStateException | JsonParseException e) {
            LOGGER.warning("Tournament-list cache read failed: " + e.getMessage());
            return null;
        }
    }

    public void replaceTournamentList(List<Tournament> tournaments) {
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            jedis.setex(ALL_TOURNAMENTS_KEY, TTL_SECONDS, JsonUtil.toJson(tournaments));
        } catch (JedisException | IllegalStateException e) {
            LOGGER.warning("Tournament-list cache write failed: " + e.getMessage());
        }
    }

    public Tournament getTournament(String tournamentId) {
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            String json = jedis.get(tournamentKey(tournamentId));
            return json == null ? null : JsonUtil.fromJson(json, Tournament.class);
        } catch (JedisException | IllegalStateException | JsonParseException e) {
            LOGGER.warning("Tournament cache read failed: " + e.getMessage());
            return null;
        }
    }

    public void replaceTournament(Tournament tournament) {
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            jedis.setex(tournamentKey(tournament.getTournamentId()), TTL_SECONDS, JsonUtil.toJson(tournament));
        } catch (JedisException | IllegalStateException e) {
            LOGGER.warning("Tournament cache write failed: " + e.getMessage());
        }
    }

    public void invalidateTournament(String tournamentId) {
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            if (tournamentId == null || tournamentId.isBlank()) {
                jedis.del(ALL_TOURNAMENTS_KEY);
            } else {
                jedis.del(ALL_TOURNAMENTS_KEY, tournamentKey(tournamentId));
            }
        } catch (JedisException | IllegalStateException e) {
            LOGGER.warning("Tournament cache invalidation failed: " + e.getMessage());
        }
    }

    private String tournamentKey(String tournamentId) {
        return "tournament:" + tournamentId;
    }
}
