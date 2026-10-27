package org.example.redis;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class RedisPoolContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        RedisPoolProvider.initialize(event.getServletContext());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        RedisPoolProvider.close();
    }
}
