//package jp.adsur.controller;
//
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import lombok.extern.slf4j.Slf4j;
//
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@Slf4j
//public class RedisTestController {
//
//    private final StringRedisTemplate stringRedisTemplate;
//
//    // 构造器注入（推荐的Spring依赖注入方式）
//    public RedisTestController(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }
//
//    /**
//     * Redisの接続性をテスト（set/getの基本操作）
//     * 現在のaccesskey認証、6379ポート、非SSLのRedis設定に対応
//     */
//    @GetMapping("/test-redis")
//    public ResponseEntity<Map<String, Object>> testRedis() {
//        Map<String, Object> response = new HashMap<>();
//        try {
//            // 1. 获取当前时间（东京时区，适配对日项目），格式化为「x年x月x日x时x分x秒x毫秒」
//            String timeStr = getFormattedCurrentTime();
//            // 2. 生成唯一key和value（避免重复，同时时间格式可读）
//            String key = "test-key-" + timeStr;
//            String value = "test-value-" + timeStr;
//
//            // Redis set操作を実行
//            stringRedisTemplate.opsForValue().set(key, value);
//            // Redis get操作を実行
//            String result = stringRedisTemplate.opsForValue().get(key);
//
//            log.info("Redisテスト成功：key={}, value={}", key, result);
//            response.put("status", "success");
//            response.put("message", "Redisの接続性テストに成功しました");
//            response.put("data", Map.of("key", key, "value", result));
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            log.error("Redisテストに失敗しました", e); // 問題調査のため完全なログスタックを出力
//            response.put("status", "error");
//            response.put("message", "Redisの接続性テストに失敗しました：" + e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
//        }
//    }
//
//    /**
//     * 現在の時刻を「yyyy年MM月dd日HH時mm分ss秒SSS毫秒」形式にフォーマット（東京タイムゾーン）
//     * @return フォーマット済みの時間文字列
//     */
//    private String getFormattedCurrentTime() {
//        // 1. 定义时间格式：年-月-日 时-分-秒-毫秒（日语习惯格式）
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH時mm分ss秒SSS毫秒");
//        // 2. 获取东京时区的当前时间（对日项目核心适配点）
//        LocalDateTime currentTime = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));
//        // 3. 格式化返回
//        return currentTime.format(formatter);
//    }
//}