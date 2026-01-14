package jp.adsur.config;

import jp.adsur.RedisTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisConfig {
    @Value("${spring.redis.host}")
    private String redisHost;
    @Value("${spring.redis.port}")
    private int redisPort;
    @Value("${spring.redis.username}")
    private String redisUsername;
    @Value("${spring.redis.ssl}")
    private boolean ssl;

    private final RedisTokenProvider tokenProvider;

    // 注入Token工具类
    public RedisConfig(RedisTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    // 核心：用Token替换Redis密码，Spring Boot自动配置Lettuce
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        String redisToken = tokenProvider.getRedisToken();
        if (redisToken == null) {
            throw new RuntimeException("Redis Token获取失败");
        }

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        config.setUsername(redisUsername);
        config.setPassword(RedisPassword.of(redisToken)); // 关键：密码=Token

        // Spring Boot自动配置Lettuce，无需手动写任何Lettuce代码！
        return new LettuceConnectionFactory(config);
    }
}