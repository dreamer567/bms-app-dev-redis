package jp.adsur;

import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenProvider {
    @Value("${redis.mi.client-id}")
    private String miClientId;

    @Value("${redis.scope}")
    private String redisScope;

    // 获取Redis Entra认证Token（强制使用托管标识，跳过EnvironmentCredential）
    public String getRedisToken() {
        try {
            // 关键：直接使用ManagedIdentityCredential，而非DefaultAzureCredential
            ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder()
                    .clientId(miClientId) // 指定你的托管标识Client ID
                    .build();

            TokenRequestContext context = new TokenRequestContext().addScopes(redisScope);
            // 同步获取Token（App Service中MSI端点可用，无需异步）
            AccessToken token = credential.getToken(context).block();

            if (token == null) {
                System.err.println("❌ 托管标识Token获取结果为空");
                return null;
            }

            System.out.println("✅ 托管标识Token获取成功，过期时间：" + token.getExpiresAt());
            return token.getToken();
        } catch (Exception e) {
            System.err.println("❌ 托管标识Token获取失败：" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}