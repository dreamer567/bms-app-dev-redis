package jp.adsur;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.credential.TokenRequestContext;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenProvider {
    // 你的用户分配托管标识的Client ID（必ず置き換えてください）
    private static final String USER_ASSIGNED_MI_CLIENT_ID = "fcd3d5e6-21dd-492a-a4ff-e738c64d8e39";

    public String getRedisAccessToken() {
        // 修正：指定用户分配标识的Client ID
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
                .managedIdentityClientId(USER_ASSIGNED_MI_CLIENT_ID) // 核心修正点
                .build();

        TokenRequestContext requestContext = new TokenRequestContext();
        // RedisのEntra Auth用スコープ（固定値）
        requestContext.addScopes("https://redis.azure.com/.default");

        // トークンを取得（blockは同期処理、非同期の場合はsubscribeを使用）
        return credential.getToken(requestContext).block().getToken();
    }
}