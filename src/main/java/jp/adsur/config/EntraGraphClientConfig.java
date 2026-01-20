package jp.adsur.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class EntraGraphClientConfig {

    @Value("${azure.entra.tenant-id}")
    private String tenantId;

    @Value("${azure.entra.client-id}")
    private String clientId;

    @Value("${azure.entra.client-secret}")
    private String clientSecret;

    /**
     * 恢复ClientSecret认证（禁用MSI后专用）
     */
    @Bean
    public GraphServiceClient<?> graphServiceClient() {
        // 1. 构建客户端密钥认证（核心：恢复ClientSecretCredential）
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        // 2. 配置Graph API权限
        TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(
                Collections.singletonList("https://graph.microsoft.com/.default"),
                credential
        );

        // 3. 创建Graph客户端
        return GraphServiceClient.builder()
                .authenticationProvider(authProvider)
                .buildClient();
    }
}