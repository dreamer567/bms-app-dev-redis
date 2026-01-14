package jp.adsur.config;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
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

@Configuration
public class RedisConfig {
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${redis.mi.client-id}")
    private String miClientId;

    @Value("${redis.scope}")
    private String redisScope;

    /**
     * 新版API：无反射，直接调用getToken(TokenRequestContext)
     */
    private String getRedisAccessToken() {
        try {
            // 1. 构建托管标识凭证（新版API，兼容azure-identity 1.12.2）
            ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder()
                    .clientId(miClientId) // 用户分配的托管标识Client ID
                    .build();

            // 2. 新版必选：封装TokenRequestContext（替代旧版String[]参数）
            TokenRequestContext context = new TokenRequestContext();
            context.addScopes(redisScope);

            // 3. 同步获取Token（设置30秒超时，避免卡死）
            AccessToken token = credential.getToken(context).block(Duration.ofSeconds(30));

            if (token == null || token.getToken().isEmpty()) {
                throw new RuntimeException("Redis认证Token为空");
            }

            System.out.println("✅ Token获取成功，过期时间：" + token.getExpiresAt());
            return token.getToken();
        } catch (Exception e) {
            throw new RuntimeException("获取Redis Token失败", e);
        }
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 1. 获取新版API的Token（无反射）
        String token = getRedisAccessToken();

        // 2. 配置Redis连接（Azure Redis + SSL + Token认证）
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        redisConfig.setPassword(token); // Token作为Redis密码

        // 3. 启用SSL + 超时配置（Azure Redis强制要求）
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl()
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