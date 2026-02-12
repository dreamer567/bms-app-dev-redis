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
    private final JedisPool jedisPool;

    // Redis配置项
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port:6380}")
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl:true}")
    private boolean redisSsl;

    // 固定配置（所有Jedis版本通用）
    private static final int REDIS_TIMEOUT = 2000; // 超时时间（毫秒）
    private static final String REDIS_DEFAULT_USER = "default"; // Azure Redis默认用户名

    /**
     * 构造器：初始化JedisPool（经典连接池，无版本兼容问题）
     */
    public RedisAccessKeyTestController() {
        // 1. 打印配置（密码脱敏）
        log.info("=== 初始化Redis Access Key认证客户端（JedisPool）===");
        log.debug("Redis配置：host={}, port={}, ssl={}, password={}",
                redisHost, redisPort, redisSsl, maskPassword(redisPassword));

        // 2. 配置连接池（基础参数）
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        log.debug("连接池参数：maxTotal={}, maxIdle={}, minIdle={}",
                poolConfig.getMaxTotal(), poolConfig.getMaxIdle(), poolConfig.getMinIdle());

        // 3. 初始化JedisPool（所有Jedis版本通用的构造器，无编译错误）
        try {
            // 构造器参数：poolConfig + host + port + timeout + user + password + ssl
            this.jedisPool = new JedisPool(
                    poolConfig,          // 连接池配置
                    redisHost,           // Redis主机
                    redisPort,           // Redis端口
                    REDIS_TIMEOUT,       // 超时时间
                    REDIS_DEFAULT_USER,  // Azure Redis默认用户名
                    redisPassword,       // Access Key（密码）
                    redisSsl             // SSL（Azure Redis强制）
            );

            // 验证连接（获取Jedis实例执行PING）
            try (Jedis jedis = jedisPool.getResource()) {
                String pingResult = jedis.ping();
                log.info("=== Redis Access Key认证成功！PING结果：{} ===", pingResult);
            }
        } catch (Exception e) {
            log.error("=== Redis Access Key认证初始化失败 ===", e);
            throw new RuntimeException("Redis连接池创建失败", e);
        }
    }

    /**
     * Redis Access Key认证测试接口
     * 访问路径：/test-redis-access-key
     */
    @GetMapping("/test-redis-access-key")
    public ResponseEntity<Map<String, Object>> testRedisWithAccessKey() {
        Map<String, Object> response = new HashMap<>();
        String requestId = generateRequestId();
        log.info("=== 请求{}：开始处理Redis Access Key测试 ===", requestId);

        // 使用try-with-resources自动关闭Jedis实例（避免连接泄漏）
        try (Jedis jedis = jedisPool.getResource()) {
            // 生成带时间戳的测试Key/Value
            String timeStr = getFormattedCurrentTime();
            String key = "AccessKey-テストキー-" + timeStr;
            String value = "AccessKey-テスト値-" + timeStr;
            log.debug("请求{}：生成测试数据 - key={}, value={}", requestId, key, value);

            // 执行Redis SET/GET命令
            String setResult = jedis.set(key, value);
            log.info("请求{}：SET命令执行结果：{}", requestId, setResult);

            String getResult = jedis.get(key);
            log.info("请求{}：GET命令执行结果：{}", requestId, getResult);

            // 组装成功响应
            response.put("status", "成功");
            response.put("requestId", requestId);
            response.put("message", "Redis Access Key认证テストに成功しました");
            response.put("data", Map.of(
                    "redisHost", redisHost,
                    "redisPort", redisPort,
                    "authType", "Access Key",
                    "key", key,
                    "value", getResult
            ));
            return ResponseEntity.ok(response);

        } catch (JedisException e) {
            // Redis命令执行异常（如连接断开、认证失败）
            log.error("请求{}：Redis命令执行失败", requestId, e);
            response.put("status", "エラー");
            response.put("requestId", requestId);
            response.put("message", "Redisコマンド実行失敗：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (Exception e) {
            // 通用异常（如参数错误、空指针）
            log.error("请求{}：请求处理异常", requestId, e);
            response.put("status", "エラー");
            response.put("requestId", requestId);
            response.put("message", "リクエスト処理失敗：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ========== 工具方法 ==========
    /**
     * 密码脱敏：仅保留前4位和后4位，避免日志泄露敏感信息
     */
    private String maskPassword(String password) {
        if (password == null || password.length() <= 8) {
            return "******";
        }
        return password.substring(0, 4) + "******" + password.substring(password.length() - 4);
    }

    /**
     * 生成唯一请求ID：便于关联单次请求的所有日志
     */
    private String generateRequestId() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + "-" + (int) (Math.random() * 10000);
    }

    /**
     * 格式化当前时间：用于生成唯一测试Key
     */
    private String getFormattedCurrentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH時mm分ss秒SSSミリ秒");
        return LocalDateTime.now(ZoneId.of("Asia/Tokyo")).format(formatter);
    }
}