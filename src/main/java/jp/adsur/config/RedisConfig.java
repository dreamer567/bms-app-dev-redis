package jp.adsur.config;

import com.azure.core.credential.AccessToken;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * azure-identity 1.5.0 官方原版代码
 * 无任何TokenRequestContext，纯String参数调用getToken
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    // Azure Redis Scope（固定）
    private static final String REDIS_SCOPE = "https://redis.azure.com/.default";

    private String getRedisAccessToken() {
        try {
            // 1. 创建凭证
            ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder().build();

            // 2. 反射调用1.5.0的getToken(String...)方法
            Class<?> credentialClass = credential.getClass();
            java.lang.reflect.Method getTokenMethod = credentialClass.getMethod("getToken", String[].class);
            // 传入String数组参数
            CompletableFuture<AccessToken> tokenFuture = (CompletableFuture<AccessToken>)
                    getTokenMethod.invoke(credential, (Object) new String[]{REDIS_SCOPE});

            // 3. 获取Token
            AccessToken accessToken = tokenFuture.get(30, TimeUnit.SECONDS);
            return accessToken.getToken();
        } catch (Exception e) {
            throw new RuntimeException("反射调用getToken失败", e);
        }
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        redisConfig.setPassword(getRedisAccessToken()); // Token作为密码

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl() // 强制SSL
//                .commandTimeout(Duration.ofSeconds(30))
//                .connectTimeout(Duration.ofSeconds(30))
                .build();

        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        return template;
    }
}