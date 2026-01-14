package jp.adsur.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jp.adsur.RedisTokenProvider;

@Configuration
public class RedisConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Autowired
    private RedisTokenProvider redisTokenProvider;

    // 核心修改：通过@Value读取配置/环境变量（优先级：环境变量 > application.yml > 默认值）
    // 1. Redis主机名：优先读环境变量SPRING_REDIS_HOST，再读application.yml的spring.redis.host，默认值兜底
    @Value("${spring.redis.host:bms-dev-cache-001.privatelink.redis.cache.windows.net}")
    private String redisHost;

    // 2. Redis端口：同理，可通过配置/环境变量调整
    @Value("${spring.redis.port:6380}")
    private int redisPort;

    // Redis连接工厂（核心）
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        long startTime = System.currentTimeMillis();
        try {
            log.info("开始创建Redis Lettuce连接工厂...");

            // 1. 获取Token（关键步骤）
            String token = redisTokenProvider.getRedisAccessToken();
            log.debug("Redis Token已获取，准备配置连接参数");

            // 2. 配置Redis连接（不再硬编码，使用@Value读取的配置）
            log.info("Redis连接配置：host={}, port={}（SSL启用）", redisHost, redisPort);

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
            config.setPassword(token); // 使用Token作为密码
            config.setUsername("$redisCacheName"); // Azure Redis固定用户名

            // 3. 启用SSL（Azure Redis Standard/Premium必须，之前代码漏了这一步！）
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.setUseSsl(true); // 核心补充：启用SSL，否则连接会被拒绝
            factory.afterPropertiesSet(); // 初始化

            long costTime = System.currentTimeMillis() - startTime;
            log.info("Redis Lettuce连接工厂创建成功，耗时{}ms，工厂状态：{}",
                    costTime, factory.isRunning() ? "运行中" : "未运行");
            return factory;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("Redis Lettuce连接工厂创建失败，耗时{}ms", costTime, e);
            throw new RuntimeException("创建Redis连接工厂失败", e);
        }
    }

    // RedisTemplate配置
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        log.info("开始配置RedisTemplate...");
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(lettuceConnectionFactory);

        // 设置序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        log.info("RedisTemplate配置完成，序列化器：StringRedisSerializer");
        return template;
    }
}