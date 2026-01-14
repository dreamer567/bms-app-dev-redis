package jp.adsur;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenProvider {
    @Value("${redis.mi.client-id}")
    private String miClientId;

    @Value("${redis.scope}") // 读取配置文件中的scope
    private String redisScope;

    // 获取Redis Entra认证Token
    public String getRedisToken() {
        try {
            DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
                    .managedIdentityClientId(miClientId)
                    .build();
            // 使用配置文件中的scope
            TokenRequestContext context = new TokenRequestContext().addScopes(redisScope);
            AccessToken token = credential.getToken(context).block();
            return token != null ? token.getToken() : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}