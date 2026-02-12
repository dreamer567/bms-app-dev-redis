package jp.adsur.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Access Key认证测试控制器（改用JedisPool，所有Jedis版本通用，无编译错误）
 */
@RestController
@Slf4j
public class RedisAccessKeyTestController {
    // 改用经典的JedisPool（所有Jedis版本稳定支持）
    private JedisPool jedisPool;

    // Redis配置项
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port:10000}")
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl:true}")
    private boolean redisSsl;

    // 固定配置（所有Jedis版本通用）
    private static final int REDIS_TIMEOUT = 2000; // 超时时间（毫秒）
    private static final String REDIS_DEFAULT_USER = "default"; // Azure Redis默认用户名
    // 空构造
    public RedisAccessKeyTestController() {
    }

    // ✅ 关键：用 PostConstruct，此时 @Value 已经注入
    @PostConstruct
    public void initRedis() {
        log.info("=== Redis 客户端初始化 ===");
        log.debug("Redis配置：host={}, port={}, ssl={}", redisHost, redisPort, redisSsl);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);

        try {
            jedisPool = new JedisPool(
                    poolConfig,
                    redisHost,
                    redisPort,
                    2000,
                    null,
                    redisPassword,
                    redisSsl
            );

            try (Jedis jedis = jedisPool.getResource()) {
                log.info("Redis PING 成功: {}", jedis.ping());
            }

        } catch (Exception e) {
            log.error("Redis 初始化失败", e);
            throw new RuntimeException("Redis 连接失败", e);
        }
    }

    @GetMapping("/test-redis-access-key")
    public String testRedis() {
        try (Jedis jedis = jedisPool.getResource()) {
            return "Redis 连接正常！PING: " + jedis.ping();
        } catch (Exception e) {
            return "Redis 异常: " + e.getMessage();
        }
    }
}