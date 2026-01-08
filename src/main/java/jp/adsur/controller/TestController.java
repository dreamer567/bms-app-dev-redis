package jp.adsur.controller;

import jp.adsur.config.RedisConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@RestController
public class TestController {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping("/test-db")
    public String testDb() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "DB OK, result = " + count;
        } catch (Exception e) {
            return "DB ERROR: " + e.getMessage();
        }
    }

    @GetMapping("/test-redis")
    public String testRedis() {
        // 从连接池获取连接
        try (JedisPool jedisPool = RedisConfig.getJedisPool();
             Jedis jedis = jedisPool.getResource()) {

            System.out.println("=== Azure Cache for Redis の接続検証を開始します ===");

            // 1. 验证连接状态
            String pingResponse = jedis.ping();
            System.out.println("Redis の接続ステータス：" + pingResponse); // 正常返回 PONG

            // 2. 写入数据
            String key = "azure-demo-key";
            String value = "Hello Azure Cache for Redis!";
            jedis.set(key, value);
            System.out.println("データを書き込みます：" + key + " = " + value);

            // 3. 读取数据
            String getValue = jedis.get(key);
            System.out.println("データを読み取ります：" + key + " = " + getValue);

            // 4. 验证数据一致性
            if (value.equals(getValue)) {
                System.out.println("✅ データの読み書き検証に成功しました！");
            } else {
                System.out.println("❌ データの読み書き検証に失敗しました！");
            }

            // 5. 清理测试数据（可选）
            jedis.del(key);
            System.out.println("テストデータをクリーンアップします：" + key + " を削除しました");

            System.out.println("=== Azure Cache for Redis の接続検証が完了しました ===");

        } catch (Exception e) {
            System.err.println("❌ Redis 操作に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }
        return "コンソールログを確認して Redis のテスト結果をご確認ください。";
    }

}
