package jp.adsur.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RedisTestController {
    private final StringRedisTemplate stringRedisTemplate;

    // 构造器注入（Spring推荐方式）
    public RedisTestController(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/test-redis")
    public String testRedis() {
        try {
            // 简单的set/get操作，验证Redis连通性
            String key = "test-key";
            String value = "test-value-" + System.currentTimeMillis();

            // 执行set操作（避免使用复杂重载，用最基础的set方法）
            stringRedisTemplate.opsForValue().set(key, value);

            // 执行get操作
            String result = stringRedisTemplate.opsForValue().get(key);

            return "✅ Redis测试成功！key=" + key + ", value=" + result;
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Redis测试失败：" + e.getMessage();
        }
    }

    @GetMapping("/check-azure-env")
    public Map<String, String> checkAzureEnv() {
        Map<String, String> env = new HashMap<>();
        // 托管标识的核心环境变量（App Service启用标识后自动注入）
        env.put("IDENTITY_ENDPOINT", System.getenv("IDENTITY_ENDPOINT"));
        env.put("IDENTITY_HEADER", System.getenv("IDENTITY_HEADER"));
        env.put("WEBSITE_SITE_NAME", System.getenv("WEBSITE_SITE_NAME"));
        env.put("WEBSITE_INSTANCE_ID", System.getenv("WEBSITE_INSTANCE_ID"));
        return env;
    }
}