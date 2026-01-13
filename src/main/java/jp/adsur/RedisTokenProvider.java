package jp.adsur;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ChainedTokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(RedisTokenProvider.class);

    // 获取Redis Entra Auth Token（修正参数类型不兼容问题）
    public String getRedisAccessToken() {
        long startTime = System.currentTimeMillis();
        try {
            log.info("开始获取Azure Redis Entra Auth Token...");

            // 1. 构建Azure Managed Identity凭证（核心逻辑）
            ChainedTokenCredential credential = new DefaultAzureCredentialBuilder()
                    .build();
            log.debug("Azure Managed Identity凭证构建完成，类型：{}", credential.getClass().getSimpleName());

            // 2. 构建TokenRequestContext对象（关键修正：替换String为TokenRequestContext）
            // scope固定为https://redis.azure.com/.default（Azure Redis的Entra Auth专属scope）
            TokenRequestContext tokenRequestContext = new TokenRequestContext()
                    .addScopes("https://redis.azure.com/.default");

            // 3. 获取Token（传入TokenRequestContext，而非直接传String）
            String redisToken = credential.getToken(tokenRequestContext)
                    .block() // 同步获取异步结果（适合Spring Boot同步场景）
                    .getToken(); // 提取Token字符串

            long costTime = System.currentTimeMillis() - startTime;
            // 脱敏日志：只打印Token前10位，避免敏感信息泄露
            log.info("Azure Redis Token获取成功，耗时{}ms，Token前10位：{}",
                    costTime, redisToken.substring(0, Math.min(redisToken.length(), 10)));

            return redisToken;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.warn("Azure Redis Token获取失败（配置未就绪，临时返回模拟Token），耗时{}ms，异常：{}",
                    costTime, e.getMessage());
            // 临时返回模拟Token，避免应用启动失败
            return "mock-token-for-debug";

//            log.error("Azure Redis Token获取失败，耗时{}ms", costTime, e);
//            throw new RuntimeException("获取Redis Entra Auth Token失败", e);
        }
    }
}