package jp.adsur.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RedisTestController {
    private static final Logger log = LoggerFactory.getLogger(RedisTestController.class);
    private final StringRedisTemplate stringRedisTemplate;

    // コンストラクタインジェクション（Spring推奨方式）
    public RedisTestController(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Redisの接続性をテスト（set/getの基本操作）
     * 現在のaccesskey認証、6379ポート、非SSLのRedis設定に対応
     */
    @GetMapping("/test-redis")
    public ResponseEntity<Map<String, Object>> testRedis() {
        Map<String, Object> response = new HashMap<>();
        try {
            String key = "test-key-" + System.currentTimeMillis(); // キーの重複を回避
            String value = "test-value-" + System.currentTimeMillis();

            // Redis set操作を実行
            stringRedisTemplate.opsForValue().set(key, value);
            // Redis get操作を実行
            String result = stringRedisTemplate.opsForValue().get(key);

            log.info("Redisテスト成功：key={}, value={}", key, result);
            response.put("status", "success");
            response.put("message", "Redisの接続性テストに成功しました");
            response.put("data", Map.of("key", key, "value", result));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Redisテストに失敗しました", e); // 問題調査のため完全なログスタックを出力
            response.put("status", "error");
            response.put("message", "Redisの接続性テストに失敗しました：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}