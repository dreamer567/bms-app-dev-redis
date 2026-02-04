package jp.adsur.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;

import java.util.concurrent.ExecutionException;

@Configuration
public class RedisConfig {

    private static final String REDIS_HOST = "bms-dev-cache-002.japanwest.redis.azure.net";
    private static final int REDIS_PORT = 10000; // TLS端口

    /**
     * 获取 Azure Redis 的 AccessToken
     */
    private String getRedisAccessToken() throws ExecutionException, InterruptedException {
        var credential = new DefaultAzureCredentialBuilder().build();
        var tokenRequestContext = new TokenRequestContext()
                .addScopes("https://*.cacheinfra.windows.net:10225/appid/.default");
        AccessToken token = credential.getToken(tokenRequestContext).toFuture().get();
        return token.getToken();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() throws ExecutionException, InterruptedException {
        // 1️⃣ Redis 主机配置
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(REDIS_HOST);
        redisConfig.setPort(REDIS_PORT);
        redisConfig.setUsername("default");           // Azure Redis OAuth 用户
        redisConfig.setPassword(getRedisAccessToken()); // OAuth token 作为密码

        // 2️⃣ Lettuce 客户端配置：仅启用 TLS
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl()  // TLS
                .build();

        // 3️⃣ 返回 LettuceConnectionFactory
        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
