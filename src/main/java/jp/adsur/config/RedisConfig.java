package jp.adsur.config;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * 使用 Managed Identity 获取 Redis 的 Access Token
     */
    private String getRedisAccessToken() throws ExecutionException, InterruptedException {
        // 只使用 ManagedIdentityCredential
        ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder().build();

        // Redis 的 scope 格式：使用主机名 + 10225 端口 + /.default
        String scope = String.format("https://%s:%s/.default", redisHost, redisPort);

        TokenRequestContext request = new TokenRequestContext().addScopes(scope);

        AccessToken token = credential.getToken(request).toFuture().get();
        return token.getToken();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() throws ExecutionException, InterruptedException {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        redisConfig.setUsername("default");               // OAuth 用户固定为 default
        redisConfig.setPassword(getRedisAccessToken());

        // 这里直接给 LettuceConnectionFactory 传超时
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.setTimeout(5000); // 毫秒
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
