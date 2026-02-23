package jp.adsur.controller;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@Slf4j
public class RedisTestController {
    // Redis核心配置
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port:6380}") // Azure Redis TLS默认端口6380
    private int redisPort;

    @Value("${azure.entra.client-secret}") // 单独配置client-secret，避免与redis.password混淆
    private String clientSecret;

    // 从配置文件读取，避免硬编码（推荐在Azure App Service的应用设置中配置）
    @Value("${azure.entra.tenant-id:c8047302-6c6e-43d6-97cd-ac845e5082fe}")
    private String tenantId;

    @Value("${azure.entra.client-id:04e43e6e-7cf3-41b8-81be-4a1cfb4c57ff}")
    private String clientId;

    /**
     * Redis接続テスト（最终版：简洁+高性能+资源安全）
     */
    @GetMapping("/test-redis")
    public ResponseEntity<Map<String, Object>> testRedis() {
        Map<String, Object> response = new HashMap<>();
        ClientResources clientResources = null;
        RedisClient redisClient = null;
        StatefulRedisConnection<String, String> connection = null;

        // ステップ1：基本情報ログ出力
        log.info("\n===== Redis接続テスト開始（Lettuce / Java 17） =====");
        log.info("📌 Redis接続基本情報：host={}, port={}, Javaバージョン={}",
                redisHost, redisPort, System.getProperty("java.version"));

        try {
            // 1. 配置Entra ID令牌的Scopes（用Set符合OAuth2规范）
            List<String> scopes = new ArrayList<>();
            scopes.add("https://redis.azure.com/.default");

            // 2. 获取Entra ID访问令牌（简化嵌套，无需额外try-catch）
            ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                    .tenantId(tenantId) // SDK自动拼接成 https://login.microsoftonline.com/{tenantId}/
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    // 如果是非公有云（如中国云），才需要加这行：
                    // .authorityHost("https://login.chinacloudapi.cn/")
                    .build();

            TokenRequestContext tokenRequest = new TokenRequestContext().setScopes(scopes);
            AccessToken accessToken = credential.getToken(tokenRequest).block();

            if (accessToken == null || accessToken.getToken() == null) {
                throw new RuntimeException("获取 Entra ID 令牌失败，令牌为空");
            }
            log.info("✅ 成功获取 Entra ID 令牌: {}...", accessToken.getToken().substring(0, 20));

            // 3. 创建Redis连接（资源安全管理）
            clientResources = DefaultClientResources.create();
            RedisURI redisURI = RedisURI.builder()
                    .withHost(redisHost)
                    .withPort(redisPort)
                    .withSsl(true) // Azure Redis必须启用SSL
                    .withPassword(accessToken.getToken()) // 核心：令牌作为密码
                    .withTimeout(Duration.ofSeconds(30))
                    .build();

            redisClient = RedisClient.create(clientResources, redisURI);
            connection = redisClient.connect();

            // 4. 验证连接并测试读写
            RedisCommands<String, String> syncCommands = connection.sync();
            String pingResponse = syncCommands.ping();
            log.info("✅ Redis 连接验证成功，PING 响应: {}", pingResponse);

            // 测试Key加入时间戳，避免重复
            String key = "test-key-" + System.currentTimeMillis();
            String value = "azure-redis-entra-auth-test-" + getFormattedCurrentTime();
            syncCommands.set(key, value);
            String valueGot = syncCommands.get(key);
            log.info("✅ 从 Redis 获取值: {}", valueGot);

            // 5. 构建成功响应
            log.info("\n===== Redis接続テスト全流程成功 =====");
            response.put("status", "成功");
            response.put("message", "Redis接続テストに成功しました（Entra ID + TLS / Lettuce）");
            response.put("data", Map.of(
                    "redisHost", redisHost,
                    "redisPort", redisPort,
                    "javaVersion", System.getProperty("java.version"),
                    "key", key,
                    "value", valueGot,
                    "timestamp", getFormattedCurrentTime()
            ));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // 异常处理：详细日志+标准化响应
            log.error("\n❌ Redis接続テスト失敗 =====", e);
            response.put("status", "エラー");
            response.put("message", String.format("Redis接続テストに失敗しました：%s", e.getMessage()));
            response.put("errorDetail", Map.of(
                    "errorType", e.getClass().getName(),
                    "redisHost", redisHost,
                    "redisPort", redisPort,
                    "timestamp", getFormattedCurrentTime()
            ));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } finally {
            // 关键：确保所有资源都被释放，避免泄漏
            if (connection != null) {
                connection.close();
                log.info("🔌 Redis连接已关闭");
            }
            if (redisClient != null) {
                redisClient.shutdown();
                log.info("🔌 RedisClient已关闭");
            }
            if (clientResources != null) {
                clientResources.shutdown();
                log.info("🔌 ClientResources已关闭");
            }
            log.info("\n===== Redis接続テスト終了 =====\n");
        }
    }

    /**
     * 現在時刻フォーマット（日式格式）
     */
    private String getFormattedCurrentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH時mm分ss秒SSSミリ秒");
        LocalDateTime currentTime = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));
        return currentTime.format(formatter);
    }
}