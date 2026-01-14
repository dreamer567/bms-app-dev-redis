package jp.adsur.config;// 1. JDK核心类导入（解决Duration找不到符号）
import java.time.Duration;

// 2. Spring Redis核心导入（仅保留最基础、最稳定的）
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

// 3. 自定义Token工具类导入（替换为你的实际包路径）
import jp.adsur.RedisTokenProvider;

@Configuration
public class RedisConfig {
    private final RedisTokenProvider tokenProvider;

    // Redis私有网络配置（替换为你的实际信息）
    private static final String REDIS_HOST = "bms-dev-cache-001.redis.cache.windows.net";
    private static final int REDIS_PORT = 6380;
    private static final String REDIS_USER = "$bms-dev-cache-001";

    // 构造器注入Token工具类（确保TokenProvider已加@Component注解）
    public RedisConfig(RedisTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // 1. 获取托管标识Token
        String redisToken = tokenProvider.getRedisToken();
        if (redisToken == null || redisToken.isEmpty()) {
            throw new RuntimeException("托管标识Token获取失败");
        }

        // 2. Redis基础配置
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT);
        redisConfig.setUsername(REDIS_USER);
        redisConfig.setPassword(RedisPassword.of(redisToken));

        // 3. Lettuce配置（仅用基础方法，无任何易出错的API）
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(60))  // 已导入Duration，无错误
//                .connectionTimeout(Duration.ofSeconds(30)) // 已导入Duration，无错误
                .build();

        // 4. 强制启用SSL（绕开ssl(true)方法，核心替代方案）
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
        factory.setUseSsl(true); // 等价于ssl(true)，无编译错误
        factory.afterPropertiesSet(); // 强制生效配置

        return factory;
    }
}