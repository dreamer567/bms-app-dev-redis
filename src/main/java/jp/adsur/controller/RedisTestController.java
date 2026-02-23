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
    // Redisコア設定
    @Value("${spring.data.redis.host}")
    private String redisHost;

    // 修正：Azure Redis TLSポート6380を強制使用（Entra ID認証にはTLS必須）
    @Value("${spring.data.redis.port:6380}")
    private int redisPort;

    // Redisインスタンス名（Entra ID認証のユーザー名に必要）
    @Value("${azure.redis.instance-name}")
    private String redisInstanceName;

    // Entra ID設定
    @Value("${azure.entra.client-secret}")
    private String clientSecret;

    @Value("${azure.entra.tenant-id:c8047302-6c6e-43d6-97cd-ac845e5082fe}")
    private String tenantId;

    @Value("${azure.entra.client-id:04e43e6e-7cf3-41b8-81be-4a1cfb4c57ff}")
    private String clientId;

    /**
     * Redis接続テスト（Entra ID認証+ポート問題の修正版）
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
            // 1. Entra IDトークンの取得（この部分は成功しているため、修正不要）
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
                throw new RuntimeException("Entra IDトークンの取得に失敗しました。トークンが空です");
            }
            log.info("✅ Entra IDトークンの取得に成功しました: {}...", accessToken.getToken().substring(0, 20));

            // 2. Redis接続の作成（コア修正：ユーザー名の追加+SSLの強制+正しいポート）
            clientResources = DefaultClientResources.create();

            // 重要修正：RedisURI構築時にユーザー名を指定する必要があります（2択）
            RedisURI redisURI = RedisURI.builder()
                    .withHost(redisHost)
                    .withPort(redisPort)
                    .withSsl(true) // Entra ID認証にはSSLの有効化が必須
                    .withAuthentication("$"+redisInstanceName, accessToken.getToken())
                    .withTimeout(Duration.ofSeconds(30))
                    .build();

            redisClient = RedisClient.create(clientResources, redisURI);
            connection = redisClient.connect();

            // 3. 接続の検証
            RedisCommands<String, String> syncCommands = connection.sync();
            String pingResponse = syncCommands.ping();
            log.info("✅ Redis接続の検証に成功しました。PINGレスポンス: {}", pingResponse);

            // 読み書きテスト
            String key = "test-key-" + System.currentTimeMillis();
            String value = "azure-redis-entra-auth-test-" + getFormattedCurrentTime();
            syncCommands.set(key, value);
            String valueGot = syncCommands.get(key);
            log.info("✅ Redisから値を取得しました: {}", valueGot);

            // 成功レスポンス
            log.info("\n===== Redis接続テストの全プロセスが成功しました =====");
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
            log.error("\n❌ Redis接続テストに失敗しました =====", e);
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
            // リソースの解放（保持）
            if (connection != null) {
                connection.close();
                log.info("🔌 Redis接続を閉じました");
            }
            if (redisClient != null) {
                redisClient.shutdown();
                log.info("🔌 RedisClientを閉じました");
            }
            if (clientResources != null) {
                clientResources.shutdown();
                log.info("🔌 ClientResourcesを閉じました");
            }
            log.info("\n===== Redis接続テスト終了 =====\n");
        }
    }

    /**
     * 現在時刻のフォーマット（日本式フォーマット）
     */
    private String getFormattedCurrentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH時mm分ss秒SSSミリ秒");
        LocalDateTime currentTime = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));
        return currentTime.format(formatter);
    }
}