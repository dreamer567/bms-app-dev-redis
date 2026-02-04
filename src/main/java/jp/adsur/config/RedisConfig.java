package jp.adsur.config;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private String acquireRedisAccessToken() {
        DefaultAzureCredential credential =
                new DefaultAzureCredentialBuilder().build();

        TokenRequestContext context = new TokenRequestContext()
                .addScopes("https://redis.azure.com/.default");

        AccessToken token = credential.getToken(context).block();

        if (token == null) {
            throw new IllegalStateException("Failed to acquire Redis access token");
        }

        return token.getToken();
    }

    @Bean
    public JedisPooled jedisPooled() {
        String accessToken = acquireRedisAccessToken();

        JedisClientConfig clientConfig = new JedisClientConfig() {

            @Override
            public String getUser() {
                return "default";   // Azure Redis OAuth 固定
            }

            @Override
            public String getPassword() {
                return accessToken;
            }

            @Override
            public boolean isSsl() {
                return true;        // Azure Managed Redis 必须
            }

            @Override
            public int getConnectionTimeoutMillis() {
                return 5000;
            }

            @Override
            public int getSocketTimeoutMillis() {
                return 5000;
            }
        };

        return new JedisPooled(
                new HostAndPort(redisHost, redisPort),
                clientConfig
        );
    }
}
