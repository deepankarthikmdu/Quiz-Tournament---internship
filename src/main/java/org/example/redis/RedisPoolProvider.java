package org.example.redis;

import jakarta.servlet.ServletContext;
import redis.clients.jedis.JedisPool;

public final class RedisPoolProvider {
    private static volatile JedisPool pool;

    private RedisPoolProvider() { }

    public static synchronized void initialize(ServletContext context) {
        if (pool != null && !pool.isClosed()) return;
        String host = context.getInitParameter("redisHost");
        String portValue = context.getInitParameter("redisPort");
        String redisHost = host == null || host.isBlank() ? "localhost" : host.trim();
        int redisPort = portValue == null || portValue.isBlank() ? 6379 : Integer.parseInt(portValue.trim());
        pool = new JedisPool(redisHost, redisPort);
    }

    public static JedisPool getPool() {
        JedisPool current = pool;
        if (current == null || current.isClosed()) {
            throw new IllegalStateException("Redis pool has not been initialized");
        }
        return current;
    }

    public static synchronized void close() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }
}
