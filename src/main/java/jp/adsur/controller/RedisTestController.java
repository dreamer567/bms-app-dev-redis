package jp.adsur.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPooled;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class RedisTestController {
    private final JedisPooled redisClient;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    public RedisTestController(JedisPooled redisClient) {
        this.redisClient = redisClient;
    }

    /**
     * Redis接続テスト（キー/値のセットと取得）
     */
    @GetMapping("/test-redis")
    public ResponseEntity<Map<String, Object>> testRedis() {
        Map<String, Object> response = new HashMap<>();
        try {
            String timeStr = getFormattedCurrentTime();
            String key = "テストキー-" + timeStr;
            String value = "テスト値-" + timeStr;

            // Redisにセット
            redisClient.set(key, value);
            // Redisから取得
            String result = redisClient.get(key);

            log.info("Redis接続テスト成功：host={}, port={}, key={}, value={}", redisHost, redisPort, key, result);
            response.put("status", "成功");
            response.put("message", "Redis接続テストに成功しました（Azure Managed Identity + TLS）");
            response.put("data", Map.of(
                    "redisHost", redisHost,
                    "redisPort", redisPort,
                    "key", key,
                    "value", result
            ));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Redis接続テスト失敗：host={}, port={}", redisHost, redisPort, e);
            response.put("status", "エラー");
            response.put("message", "Redis接続テストに失敗しました：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 現在時刻を「yyyy年MM月dd日HH時mm分ss秒SSSミリ秒」形式で取得
     */
    private String getFormattedCurrentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH時mm分ss秒SSSミリ秒");
        LocalDateTime currentTime = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));
        return currentTime.format(formatter);
    }
}
