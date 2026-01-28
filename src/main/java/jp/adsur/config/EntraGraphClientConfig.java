//package jp.adsur.config;
//
//import com.azure.identity.ClientSecretCredential;
//import com.azure.identity.ClientSecretCredentialBuilder;
//import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
//import com.microsoft.graph.requests.GraphServiceClient;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.Collections;
//
//@Configuration
//public class EntraGraphClientConfig {
//
//    @Value("${azure.entra.tenant-id}")
//    private String tenantId;
//
//    @Value("${azure.entra.client-id}")
//    private String clientId;
//
//    @Value("${azure.entra.client-secret}")
//    private String clientSecret;
//
//    /**
//     * 单例ClientSecretCredential Bean（供graphClient和Token获取复用）
//     * 核心：避免重复创建，解决Bean冲突
//     */
//    @Bean
//    public ClientSecretCredential clientSecretCredential() {
//        return new ClientSecretCredentialBuilder()
//                .tenantId(tenantId)
//                .clientId(clientId)
//                .clientSecret(clientSecret)
//                .build();
//    }
//
//    /**
//     * 保留GraphServiceClient Bean（用于查询用户、创建组）
//     */
//    @Bean
//    public GraphServiceClient<?> graphServiceClient() {
//        // 复用上面的ClientSecretCredential Bean，避免重复创建
//        TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(
//                Collections.singletonList("https://graph.microsoft.com/.default"),
//                clientSecretCredential() // 注入上面的单例Bean
//        );
//
//        return GraphServiceClient.builder()
//                .authenticationProvider(authProvider)
//                .buildClient();
//    }
//
//    /**
//     * 添加RestTemplate Bean（用于手动调用/$ref端点）
//     */
//    @Bean
//    public RestTemplate restTemplate() {
//        return new RestTemplate();
//    }
//}