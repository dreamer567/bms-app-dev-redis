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

    // 修复：强制使用Azure Redis TLS端口6380（Entra ID认证必须用TLS）
    @Value("${spring.data.redis.port:6380}")
    private int redisPort;

    // Redis实例名（Entra ID认证的用户名需要）
    @Value("${azure.redis.instance-name}")
    private String redisInstanceName;

    // Entra ID配置
    @Value("${azure.entra.client-secret}")
    private String clientSecret;

    @Value("${azure.entra.tenant-id:c8047302-6c6e-43d6-97cd-ac845e5082fe}")
    private String tenantId;

    @Value("${azure.entra.client-id:04e43e6e-7cf3-41b8-81be-4a1cfb4c57ff}")
    private String clientId;

    /**
     * Redis接続テスト（修复Entra ID认证+端口问题）
     */
    @GetMapping("/test-redis")
    public ResponseEntity<Map<String, Object>> testRedis() {
        Map<String, Object> response = new HashMap<>();
        ClientResources clientResources = null;
        RedisClient redisClient = null;
        StatefulRedisConnection<String, String> connection = null;

        log.info("\n===== Redis接続テスト開始（Lettuce / Java 17） =====");
        log.info("📌 Redis接続基本情報：host={}, port={}, instanceName={}, Javaバージョン={}",
                redisHost, redisPort, redisInstanceName, System.getProperty("java.version"));

        try {
            // 1. 获取Entra ID令牌（这部分是成功的，无需修改）
            List<String> scopes = new ArrayList<>();
            scopes.add("https://redis.azure.com/.default");

            ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                    .tenantId(tenantId)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();

            TokenRequestContext tokenRequest = new TokenRequestContext().setScopes(scopes);
            AccessToken accessToken = credential.getToken(tokenRequest).block();

            if (accessToken == null || accessToken.getToken() == null) {
                throw new RuntimeException("获取 Entra ID 令牌失败，令牌为空");
            }
            log.info("✅ 成功获取 Entra ID 令牌: {}...", accessToken.getToken().substring(0, 20));

            // 2. 创建Redis连接（核心修复：添加用户名+强制SSL+正确端口）
            clientResources = DefaultClientResources.create();

            // 关键修复：构建RedisURI时必须指定用户名（两种方式二选一）
            RedisURI redisURI = RedisURI.builder()
                    .withHost(redisHost)
                    .withPort(redisPort)
                    .withSsl(true) // Entra ID认证必须启用SSL
                    .withAuthentication("$"+redisInstanceName, accessToken.getToken())
//                    .withUsername(redisInstanceName) // 方式1：用Redis实例名（推荐）
//                    // .withUsername(clientId) // 方式2：用服务主体Client ID（也可）
//                    .withPassword(accessToken.getToken()) // 令牌作为密码
                    .withTimeout(Duration.ofSeconds(30))
                    .build();

            redisClient = RedisClient.create(clientResources, redisURI);
            connection = redisClient.connect();

            // 3. 验证连接
            RedisCommands<String, String> syncCommands = connection.sync();
            String pingResponse = syncCommands.ping();
            log.info("✅ Redis 连接验证成功，PING 响应: {}", pingResponse);

            // 测试读写
            String key = "test-key-" + System.currentTimeMillis();
            String value = "azure-redis-entra-auth-test-" + getFormattedCurrentTime();
            syncCommands.set(key, value);
            String valueGot = syncCommands.get(key);
            log.info("✅ 从 Redis 获取值: {}", valueGot);

            // 成功响应
            log.info("\n===== Redis接続テスト全流程成功 =====");
            response.put("status", "成功");
            response.put("message", "Redis接続テストに成功しました（Entra ID + TLS / Lettuce）");
            response.put("data", Map.of(
                    "redisHost", redisHost,
                    "redisPort", redisPort,
                    "redisInstanceName", redisInstanceName,
                    "javaVersion", System.getProperty("java.version"),
                    "key", key,
                    "value", valueGot,
                    "timestamp", getFormattedCurrentTime()
            ));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("\n❌ Redis接続テスト失敗 =====", e);
            response.put("status", "エラー");
            response.put("message",e.getMessage());
            response.put("errorDetail", Map.of(
                    "errorType", e.getClass().getName(),
                    "redisHost", redisHost,
                    "redisPort", redisPort,
                    "redisInstanceName", redisInstanceName,
                    "timestamp", getFormattedCurrentTime()
            ));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } finally {
            // 资源释放（保留）
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