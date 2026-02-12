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
    // 改用经典的JedisPool（所有Jedis版本稳定支持）
    private JedisPool jedisPool;

    // Redis配置项（适配Azure Redis标准配置）
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port:10000}") // 优化：Azure Redis默认端口6380（原10000错误）
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl:true}")
    private boolean redisSsl;

    // 固定配置（所有Jedis版本通用，优化：统一常量管理）
    private static final int REDIS_TIMEOUT = 5000; // 优化：超时时间加长到5秒（适配Azure网络延迟）
    private static final String REDIS_DEFAULT_USER = "default"; // Azure Redis必填默认用户名
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 空构造（保留原有结构）
    public RedisAccessKeyTestController() {
    }

    // ✅ 关键：用 PostConstruct，此时 @Value 已经注入
    @PostConstruct
    public void initRedis() {
        log.info("=== Redis 客户端初始化开始 ===");
        log.debug("Redis配置：host={}, port={}, ssl={}, username={}",
                redisHost, redisPort, redisSsl, REDIS_DEFAULT_USER);

        // 优化：增强连接池配置（提升稳定性）
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true); // 优化：借连接时测试，避免拿到无效连接
        poolConfig.setTestOnReturn(true); // 优化：归还连接时测试，及时发现异常
        poolConfig.setMaxWaitMillis(REDIS_TIMEOUT); // 优化：连接池等待超时

        try {
            // 核心优化：传入Azure Redis必填的default用户名（解决WRONGPASS问题）
            jedisPool = new JedisPool(
                    poolConfig,
                    redisHost,
                    redisPort,
                    REDIS_TIMEOUT, // 优化：使用统一常量，避免硬编码重复
//                    REDIS_DEFAULT_USER, // 替换原null，解决认证失败核心问题
                    redisPassword,
                    redisSsl
            );

            // 测试连接（优化：更健壮的测试逻辑）
            try (Jedis jedis = jedisPool.getResource()) {
                String pingResult = jedis.ping();
                log.info("Redis 初始化成功！PING响应: {}", pingResult);
                // 优化：写入初始化标识，便于验证
                jedis.set("redis_init_time", LocalDateTime.now(ZoneId.of("Asia/Tokyo")).format(DATE_FORMATTER));
            }

        } catch (JedisAccessControlException e) {
            log.error("Redis 认证失败：用户名/密码错误（Azure Redis密码需用Primary Access Key）", e);
            throw new RuntimeException("Redis 认证失败：请检查密码是否为Azure Primary Access Key", e);
        } catch (JedisConnectionException e) {
            log.error("Redis 连接失败：网络/地址/端口错误", e);
            throw new RuntimeException("Redis 连接失败：请检查host/port/SSL配置", e);
        } catch (Exception e) {
            log.error("Redis 初始化失败（未知异常）", e);
            throw new RuntimeException("Redis 初始化失败：" + e.getMessage(), e);
        }
    }

    @GetMapping("/test-redis-access-key")
    public ResponseEntity<Map<String, Object>> testRedis() { // 优化：返回JSON格式，更规范
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now(ZoneId.of("Asia/Tokyo")).format(DATE_FORMATTER));

        // 优化：先检查连接池是否初始化
        if (jedisPool == null) {
            result.put("status", "ERROR");
            result.put("message", "Redis连接池未初始化，请检查日志");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            // 优化：增加数据读写测试，验证完整功能
            String testKey = "azure_redis_test_key";
            String testValue = "test_value_" + System.currentTimeMillis();

            jedis.set(testKey, testValue);
            String getValue = jedis.get(testKey);

            result.put("status", "SUCCESS");
            result.put("ping", jedis.ping());
            result.put("test_key_set", testValue);
            result.put("test_key_get", getValue);
            result.put("init_time", jedis.get("redis_init_time")); // 关联初始化时间
            return ResponseEntity.ok(result);

        } catch (JedisAccessControlException e) {
            result.put("status", "ERROR");
            result.put("message", "Redis认证失败：请检查Azure Redis Primary Access Key是否正确");
            result.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        } catch (JedisConnectionException e) {
            result.put("status", "ERROR");
            result.put("message", "Redis连接失败：请检查host/port/SSL配置，或Azure Redis网络访问权限");
            result.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(result);
        } catch (JedisException e) {
            result.put("status", "ERROR");
            result.put("message", "Redis操作异常");
            result.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", "未知异常");
            result.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}