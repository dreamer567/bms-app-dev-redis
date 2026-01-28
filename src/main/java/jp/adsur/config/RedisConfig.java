//package jp.adsur.config;
//
//import com.azure.identity.DefaultAzureCredential;
//import com.azure.identity.DefaultAzureCredentialBuilder;
//import com.azure.core.credential.AccessToken;
//import com.azure.core.credential.TokenRequestContext;
//import io.lettuce.core.RedisClient;
//import io.lettuce.core.RedisURI;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class RedisConfig {
//
//    private static final String REDIS_SCOPE =
//            "https://redis.azure.com/.default";
//
//    @Bean
//    public RedisClient redisClient() {
//
//        DefaultAzureCredential credential =
//                new DefaultAzureCredentialBuilder().build();
//
//        // ① 只在这里获取 token
//        AccessToken token = credential.getToken(
//                new TokenRequestContext().addScopes(REDIS_SCOPE)
//        ).block();
//
//        RedisURI redisURI = RedisURI.builder()
//                .withHost("bms-dev-cache-002.japanwest.redis.azure.net")
//                .withPort(10000)
//                .withPassword(token.getToken().toCharArray())
//                .build();
//
//        return RedisClient.create(redisURI);
//    }
//}
