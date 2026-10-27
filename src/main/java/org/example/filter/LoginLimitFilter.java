package org.example.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import org.example.redis.RedisPoolProvider;
import java.io.IOException;

public class LoginLimitFilter implements Filter {

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCK_SECONDS = 300;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String ip = request.getRemoteAddr();
        String lockKey = "login:lock:" + ip;
        String failKey = "login:fail:" + ip;

        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            long ttl = jedis.ttl(lockKey);
            if (ttl >= 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(ttl));
                response.setContentType("application/json; charset=UTF-8");
                response.getWriter().write(
                        "{\"error\":\"Too many failed login attempts. Try again in " + ttl + " seconds.\"}");
                return;
            }

        } catch (JedisException e) {
            getServletContext(req).log("Redis unavailable, skipping login limit", e);
        }

        chain.doFilter(req, res);

        int status = response.getStatus();
        try (Jedis jedis = RedisPoolProvider.getPool().getResource()) {
            if (status == HttpServletResponse.SC_UNAUTHORIZED) {
                long failures = jedis.incr(failKey);

                if (failures >= MAX_FAILED_ATTEMPTS) {
                    jedis.setex(lockKey, LOCK_SECONDS, "1");
                    jedis.del(failKey);
                }

            } else if (status >= 200 && status < 300) {
                jedis.del(failKey);
            }

        } catch (JedisException e) {
            getServletContext(req).log("Redis unavailable, login attempt not recorded", e);
        }
    }

    private ServletContext getServletContext(ServletRequest req) {
        return req.getServletContext();
    }
}
