package jp.adsur.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisTestController {
    private final RedisTemplate<String, String> redisTemplate;

    public RedisTestController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/test-redis")
    public String testRedis() {
        // 测试写入+读取
        redisTemplate.opsForValue().set("test-key", "Hello Azure Redis!");
        String value = redisTemplate.opsForValue().get("test-key");
        return "✅ Redis连接成功！读取值：" + value;
    }
}