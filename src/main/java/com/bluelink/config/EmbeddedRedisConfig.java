package com.bluelink.config;

import com.bluelink.util.AppConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.embedded.RedisServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.springframework.beans.factory.annotation.Autowired;

@Configuration
// Remove conditional property to allow manual control
// @ConditionalOnProperty(name = "app.redis.enabled-embedded", havingValue = "true")
public class EmbeddedRedisConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisConfig.class);

    private RedisServer redisServer;

    @Value("${app.redis.port:26379}")
    private int port;

    @Value("${app.redis.max-heap:128M}")
    private String maxHeap;

    @PostConstruct
    public void init() {
        // Auto-start only if multi-turn is enabled at startup
        if (AppConfig.isAiMultiTurnEnabled()) {
            try {
                startRedis();
            } catch (Throwable t) {
                log.error("⚠️ [Embedded Redis] Init failed: {}", t.getMessage(), t);
            }
        }
    }

    public synchronized void startRedis() {
        if (redisServer != null && redisServer.isActive()) {
            log.info("⚠️ [Embedded Redis] Already running.");
            return;
        }
        
        // Restore log level if we changed it
        try {
             ch.qos.logback.classic.Logger lettuceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.lettuce.core.protocol");
             lettuceLogger.setLevel(ch.qos.logback.classic.Level.INFO);
        } catch (Exception e) {
            // ignore
        }

        try {
            if (isPortOpen("127.0.0.1", port, 200)) {
                log.warn("⚠️ [Embedded Redis] Port {} already in use, skip embedded start.", port);
                return;
            }
            // 使用 AppConfig 定义的全局数据目录，确保打包后路径正确
            File redisDataDir = new File(AppConfig.APP_DATA_DIR, "redis-data");

            if (!redisDataDir.exists()) {
                redisDataDir.mkdirs();
            }

            String safePath = redisDataDir.getAbsolutePath().replace("\\", "/");
            log.info("-----------------------------------------------------");
            log.info("🚀 [Embedded Redis] Starting...");
            log.info("📂 [Data Storage] {}", safePath);
            
            // Build server
            redisServer = RedisServer.builder()
                    .port(port)
                    .setting("maxmemory " + maxHeap)
                    .setting("dir " + safePath)
                    .setting("dbfilename dump.rdb")
                    .setting("save 10 1")
                    .setting("appendonly yes")
                    .setting("appendfilename appendonly.aof")
                    .build();

            redisServer.start();
            log.info("✅ [Embedded Redis] Started successfully");
            
            // Re-initialize Redis Connection Factory if needed
            // LettuceConnectionFactory might need a reset or restart if it was destroyed
            try {
                org.springframework.data.redis.connection.RedisConnectionFactory factory = 
                    context.getBean(org.springframework.data.redis.connection.RedisConnectionFactory.class);
                
                if (factory instanceof org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory) {
                    org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory lettuce = 
                        (org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory) factory;
                    
                    // LettuceConnectionFactory.afterPropertiesSet() or start() might re-init
                    // But 'destroy' is usually final.
                    // However, stopping redis server doesn't destroy the factory bean unless we called destroy().
                    // If we called destroy(), the bean is dead.
                    
                    // Better approach: Don't destroy the bean. Just reset the connection.
                    // But Lettuce auto-reconnects. That's the problem.
                    // If we destroy it, we need to re-create it or refresh the context.
                    // Refreshing context is heavy.
                    
                    // Let's check if we can just toggle 'auto-reconnect' in Lettuce? No easy way.
                    
                    // If we destroyed it in stopRedis(), we need to manually re-initialize it here.
                    // lettuce.afterPropertiesSet(); // might restart it
                    // lettuce.resetConnection(); // if available
                    
                    // Let's try afterPropertiesSet() if it was destroyed.
                    // But checking "isDestroyed" is hard.
                    
                    // Simple fix: Re-call afterPropertiesSet() to revive it.
                    log.info("🔌 [Redis Client] Re-initializing connection pool...");
                    lettuce.afterPropertiesSet();
                    lettuce.resetConnection();
                }
            } catch (Exception e) {
                // ignore
            }
            
            log.info("-----------------------------------------------------");
        } catch (Throwable e) {
            log.error("⚠️ [Embedded Redis] Start failed: {}", e.getMessage(), e);
            redisServer = null;
            // Don't throw exception to avoid crashing main app if Redis fails (e.g. port in use)
        }
    }

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @PreDestroy
    public synchronized void stopRedis() {
        if (redisServer != null && redisServer.isActive()) {
            log.info("🛑 [Embedded Redis] Stopping...");
            
            // NOTE: Do not manually destroy the RedisConnectionFactory bean.
            // Lettuce handles reconnection automatically. If we destroy it, we cannot reuse it
            // when we restart Redis, leading to "LettuceConnectionFactory was destroyed" errors.
            // We just stop the server, and let Lettuce connection fail (and retry) until we start it again.
            
            // To suppress "Connection reset" logs from Lettuce watchdog, we can try to shutdown the client
            // if we had access to it, but since we want to reuse it, we just accept the logs or
            // set the logging level for Lettuce to WARN/ERROR in logback config (if available).
            // Programmatically adjusting log level for io.lettuce:
            try {
                 ch.qos.logback.classic.Logger lettuceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.lettuce.core.protocol");
                 lettuceLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
            } catch (Exception e) {
                // ignore if not using logback
            }
            
            try {
                redisServer.stop();
                log.info("👋 [Embedded Redis] Stopped.");
            } catch (Exception e) {
                log.error("Failed to stop Redis", e);
            }
            redisServer = null;
        }
    }

    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
