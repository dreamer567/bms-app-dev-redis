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
import redis.clients.jedis.exceptions.JedisAccessControlException;
import redis.clients.jedis.exceptions.JedisConnectionException;
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
    private JedisPool jedisPool;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port:10000}")
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl:true}")
    private boolean redisSsl;

    private static final int REDIS_TIMEOUT = 5000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RedisAccessKeyTestController() {
    }

    @PostConstruct
    public void initRedis() {
        log.info("=== Redis 客户端初始化开始 ===");
        // 🔥 关键：这里绝对不能再打印 username 了！
        log.debug("Redis配置：host={}, port={}, ssl={}", redisHost, redisPort, redisSsl);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setMaxWaitMillis(REDIS_TIMEOUT);

        try {
            // ==============================================
            // 🔥🔥🔥 最重要：这里 不 传 用 户 名！！！
            // ==============================================
            jedisPool = new JedisPool(
                    poolConfig,
                    redisHost,
                    redisPort,
                    REDIS_TIMEOUT,
                    redisPassword,  // 只传密码！
                    redisSsl
            );

            try (Jedis jedis = jedisPool.getResource()) {
                String pingResult = jedis.ping();
                log.info("Redis 初始化成功！PING响应: {}", pingResult);
                jedis.set("redis_init_time", LocalDateTime.now(ZoneId.of("Asia/Tokyo")).format(DATE_FORMATTER));
            }

        } catch (JedisAccessControlException e) {
            log.error("Redis 认证失败：密码错误", e);
            throw new RuntimeException("Redis 认证失败：请检查密码", e);
        } catch (JedisConnectionException e) {
            log.error("Redis 连接失败", e);
            throw new RuntimeException("Redis 连接失败", e);
        } catch (Exception e) {
            log.error("Redis 初始化失败", e);
            throw new RuntimeException("Redis 初始化失败", e);
        }
    }

    @GetMapping("/test-redis-access-key")
    public ResponseEntity<Map<String, Object>> testRedis() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now(ZoneId.of("Asia/Tokyo")).format(DATE_FORMATTER));

        if (jedisPool == null) {
            result.put("status", "ERROR");
            result.put("message", "Redis连接池未初始化");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String testKey = "azure_redis_test_key";
            String testValue = "test_value_" + System.currentTimeMillis();
            jedis.set(testKey, testValue);
            String getValue = jedis.get(testKey);

            result.put("status", "SUCCESS");
            result.put("ping", jedis.ping());
            result.put("test_key_get", getValue);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}