package jp.adsur.config;// 仅保留必要导入，无任何Lettuce SSL相关代码
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import jp.adsur.RedisTokenProvider;

@Configuration
public class RedisConfig {
    private final RedisTokenProvider tokenProvider;

    public RedisConfig(RedisTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    // 核心：仅替换Redis密码为托管标识Token，SSL靠配置文件
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // 1. 获取Token（已验证成功）
        String token = tokenProvider.getRedisToken();
        if (token == null) throw new RuntimeException("Token获取失败");

        // 2. 从Spring自动配置中获取默认的Lettuce连接工厂
        // （自动读取application.yml的SSL/host/port配置）
        LettuceConnectionFactory factory = new LettuceConnectionFactory();
        // 仅设置密码（Token），其他配置靠yml
        factory.setPassword(String.valueOf(RedisPassword.of(token)));
        factory.afterPropertiesSet();

        return factory;
    }

    // 可选：配置StringRedisTemplate（如果需要）
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        return template;
    }
}