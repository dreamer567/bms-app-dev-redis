package jp.adsur.config;

import org.springframework.beans.factory.annotation.Value;
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

    // 从 application.yml 注入 host 和 port
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * 获取 Azure Redis 的 AccessToken（Managed Identity）
     */
    private String getRedisAccessToken() throws ExecutionException, InterruptedException {
        var credential = new DefaultAzureCredentialBuilder().build();

        // 拼接成合法的 scope
        String scope = String.format("https://%s:10225/appid/.default", redisHost);

        var tokenRequestContext = new TokenRequestContext()
                .addScopes(scope);

        AccessToken token = credential.getToken(tokenRequestContext).toFuture().get();
        return token.getToken();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() throws ExecutionException, InterruptedException {
        // Redis 主机配置
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);             // 从配置文件读取
        redisConfig.setPort(redisPort);                // 从配置文件读取
        redisConfig.setUsername("default");            // Azure Redis OAuth 用户
        redisConfig.setPassword(getRedisAccessToken()); // Token 作为密码

        // Lettuce 客户端配置：启用 TLS
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl() // TLS
                .build();

        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
