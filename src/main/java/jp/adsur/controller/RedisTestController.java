package jp.adsur.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisTestController {
    private static final Logger log = LoggerFactory.getLogger(RedisTestController.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 写入Redis
    @PostMapping("/redis/set/{key}")
    public String setRedis(@PathVariable String key, @RequestParam String value) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("接收Redis写入请求：key={}, value长度={}", key, value.length());

            redisTemplate.opsForValue().set(key, value);
            long costTime = System.currentTimeMillis() - startTime;

            log.info("Redis写入成功：key={}，耗时{}ms", key, costTime);
            return "成功写入Redis：key=" + key;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("Redis写入失败：key={}，耗时{}ms", key, costTime, e);
            return "写入失败：" + e.getMessage();
        }
    }

    // 读取Redis
    @GetMapping("/redis/get/{key}")
    public String getRedis(@PathVariable String key) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("接收Redis读取请求：key={}", key);

            Object value = redisTemplate.opsForValue().get(key);
            long costTime = System.currentTimeMillis() - startTime;

            log.info("Redis读取成功：key={}，value={}，耗时{}ms", key, value, costTime);
            return "读取Redis：key=" + key + "，value=" + value;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("Redis读取失败：key={}，耗时{}ms", key, costTime, e);
            return "读取失败：" + e.getMessage();
        }
    }
}