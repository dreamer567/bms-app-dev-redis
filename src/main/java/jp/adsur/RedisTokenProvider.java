package jp.adsur;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono; // 补充Mono导入（避免编译警告）

@Component
public class RedisTokenProvider {
    @Value("${redis.mi.client-id}")
    private String miClientId;

    @Value("${redis.scope}")
    private String redisScope;

    // 获取Redis托管标识Token（新版API，稳定运行）
    public String getRedisToken() {
        try {
            // 1. 构建托管标识凭证（用户分配的标识，指定clientId）
            ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder()
                    .clientId(miClientId)
                    .build();

            // 2. 新版API：封装TokenRequestContext（必选）
            TokenRequestContext context = new TokenRequestContext();
            context.addScopes(redisScope); // 替代旧版的String参数

            // 3. 同步获取Token（设置30秒超时，避免无限等待）
            Mono<AccessToken> tokenMono = credential.getToken(context);
            AccessToken token = tokenMono.block(java.time.Duration.ofSeconds(30)); // 显式超时

            if (token == null || token.getToken() == null || token.getToken().isEmpty()) {
                System.err.println("❌ 托管标识Token为空");
                return null;
            }

            System.out.println("✅ Token获取成功，过期时间：" + token.getExpiresAt());
            return token.getToken();
        } catch (Exception e) {
            System.err.println("❌ Token获取失败：" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}