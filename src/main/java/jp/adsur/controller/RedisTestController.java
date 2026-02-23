package jp.adsur.controller;

import com.azure.core.credential.AccessToken;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import redis.clients.authentication.core.TokenAuthConfig;
import redis.clients.authentication.entraid.EntraIDTokenAuthConfigBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@Slf4j
public class RedisTestController {
    // Redis核心配置
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port:6380}") // Azure Redis TLS默认端口6380
    private int redisPort;

    // Azure托管标识凭据（全局初始化）
    private final DefaultAzureCredential azureCredential;

    // 构造函数：初始化Azure托管标识凭据
    public RedisTestController() {
        log.info("===== Azure Managed Identity クレデンシャル初期化開始（azure-identity 1.12.2） =====");
        try {
            this.azureCredential = new DefaultAzureCredentialBuilder()
                    .authorityHost("https://login.microsoftonline.com/")
                    .build();
            log.info("✅ Azure Managed Identity クレデンシャル初期化成功");
        } catch (Exception e) {
            log.error("❌ Azure Managed Identity クレデンシャル初期化失敗", e);
            throw new RuntimeException("Azure Managed Identity クレデンシャル初期化失敗（azure-identity 1.12.2）", e);
        }
    }

    /**
     * Redis接続テスト（终极版：100%适配Jedis 5.1.2构造器）
     */
    @GetMapping("/test-redis")
    public ResponseEntity<Map<String, Object>> testRedis() {
        Map<String, Object> response = new HashMap<>();

        // ステップ1：基本情報ログ出力
        log.info("\n===== Redis接続テスト開始（jedis 7.2.0 / Java 17） =====");
        log.info("📌 Redis接続基本情報：host={}, port={}, Javaバージョン={}",
                redisHost, redisPort, System.getProperty("java.version"));


        Set<String> scopes = new HashSet<>();
        scopes.add("https://redis.azure.com/.default");

        TokenAuthConfig authConfig = EntraIDTokenAuthConfigBuilder.builder()
                .secret("ERP8Q~L8tV5rrQbDTmHFaFqtxuKVRlozf58bibN_")
                .authority("https://login.microsoftonline.com/c8047302-6c6e-43d6-97cd-ac845e5082fe/")
                .scopes(scopes)
                // Other options...
                .build();

        try {
            // 2. 获取 Entra ID 访问令牌
            String accessToken = getEntraIDToken(authConfig);
            log.info("成功获取 Entra ID 令牌: " + accessToken.substring(0, 20) + "...");

            // 3. 创建 Redis 连接
            try (StatefulRedisConnection<String, String> connection = createRedisConnection(redisHost, redisPort, accessToken)) {
                // 4. 验证连接（执行简单命令）
                RedisCommands<String, String> syncCommands = connection.sync();
                String pingResponse = syncCommands.ping();
                log.info("Redis 连接验证成功，PING 响应: ", pingResponse);

                // 测试读写操作
                String key = "test-key";
                String value = "azure-redis-entra-auth-test";
                syncCommands.set(key, value);
                String valueGot = syncCommands.get(key);
                log.info("从 Redis 获取值: ", valueGot);

                // ステップ5：正常レスポンス
                log.info("\n===== Redis接続テスト全流程成功 =====");
                response.put("status", "成功");
                response.put("message", "Redis接続テストに成功しました（Azure Managed Identity + TLS / jedis 5.1.2）");
                response.put("data", Map.of(
                        "redisHost", redisHost,
                        "redisPort", redisPort,
                        "javaVersion", System.getProperty("java.version"),
                        "jedisVersion", "5.1.2",
                        "azureIdentityVersion", "1.12.2",
                        "key", key,
                        "value", valueGot
                ));
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Redis 连接或操作失败: ", e);
                throw new RuntimeException("Redis 连接或操作失败", e);
            }

        } catch (Exception e) {
            // 異常処理：詳細ログ（包含所有排查信息）
            log.error("\n❌ Redis接続テスト全流程失敗 =====", e);
            log.error("   - エラータイプ：{}", e.getClass().getName());
            log.error("   - エラーメッセージ：{}", e.getMessage());
            log.error("   - 発生時間：{}", getFormattedCurrentTime());
            log.error("   - Redis接続情報：host={}, port={}", redisHost, redisPort);
            log.error("   - 依存バージョン：jedis=5.1.2, azure-identity=1.12.2");

            // エラーレスポンス
            response.put("status", "エラー");
            response.put("message", String.format("Redis接続テストに失敗しました：%s", e.getMessage()));
            response.put("errorDetail", Map.of(
                    "errorType", e.getClass().getName(),
                    "redisHost", redisHost,
                    "redisPort", redisPort,
                    "jedisVersion", "5.1.2",
                    "azureIdentityVersion", "1.12.2",
                    "timestamp", getFormattedCurrentTime()
            ));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            log.info("\n===== Redis接続テスト終了 =====\n");
        }
    }
    /**
     * 基于 TokenAuthConfig 获取 Entra ID 访问令牌
     */
    private static String getEntraIDToken(TokenAuthConfig authConfig) {
        // 构建 ClientSecretCredential（服务主体认证）
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId("c8047302-6c6e-43d6-97cd-ac845e5082fe") // 租户ID
                .clientId("04e43e6e-7cf3-41b8-81be-4a1cfb4c57ff") // 服务主体客户端ID（需要你补充）
                .clientSecret("ERP8Q~L8tV5rrQbDTmHFaFqtxuKVRlozf58bibN_") // 服务主体密钥
                .build();

        // 获取访问令牌
        AccessToken accessToken = credential.getToken(
                new com.azure.core.credential.TokenRequestContext()
                        .addScopes("https://redis.azure.com/.default")
        ).block();

        if (accessToken == null || accessToken.getToken() == null) {
            throw new RuntimeException("获取 Entra ID 令牌失败，令牌为空");
        }

        return accessToken.getToken();
    }

    /**
     * 创建带 Entra ID 认证的 Redis 连接
     */
    private static StatefulRedisConnection<String, String> createRedisConnection(String host, int port, String accessToken) {
        // 配置 ClientResources（资源复用）
        ClientResources clientResources = DefaultClientResources.create();

        // 构建 RedisURI（启用 SSL，使用令牌作为密码）
        RedisURI redisURI = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withSsl(true) // Azure Redis 必须启用 SSL
                .withPassword(accessToken) // 核心：将 Entra ID 令牌作为密码
                .withTimeout(Duration.ofSeconds(30))
                .build();

        // 创建 RedisClient 并建立连接
        RedisClient redisClient = RedisClient.create(clientResources, redisURI);
        return redisClient.connect();
    }

    /**
     * Tokenマスク（安全対策：避免日志泄露敏感信息）
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 16) {
            return "******";
        }
        return token.substring(0, 8) + "********************" + token.substring(token.length() - 8);
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