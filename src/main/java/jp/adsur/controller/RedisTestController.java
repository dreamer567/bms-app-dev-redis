package jp.adsur.controller;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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
        AccessToken accessToken = null;
        JedisPooled redisClient = null;

        // ステップ1：基本情報ログ出力
        log.info("\n===== Redis接続テスト開始（jedis 5.1.2 / Java 17） =====");
        log.info("📌 Redis接続基本情報：host={}, port={}, Javaバージョン={}",
                redisHost, redisPort, System.getProperty("java.version"));

        try {
            // ステップ2：Entra Token取得（修复block方法）
            log.info("\n===== Entra Token取得開始 =====");
            String redisTokenScope = "https://redis.azure.com/.default";
            log.info("🔍 Token取得リクエスト：scope={}, タイムスタンプ={}",
                    redisTokenScope, getFormattedCurrentTime());

            TokenRequestContext tokenRequest = new TokenRequestContext();
            tokenRequest.addScopes(redisTokenScope);

            // 正确的block方法：Duration参数（适配azure-identity 1.12.2）
            accessToken = azureCredential.getToken(tokenRequest)
                    .block(Duration.ofSeconds(30));

            // Token検証
            if (accessToken == null || accessToken.getToken() == null || accessToken.getToken().isEmpty()) {
                log.error("❌ Entra Token取得失敗：Tokenがnullまたは空です");
                throw new RuntimeException("Entra Token取得に失敗しました：Tokenが空です");
            }

            // Token情報ログ（マスク）
            String maskedToken = maskToken(accessToken.getToken());
            log.info("✅ Entra Token取得成功！");
            log.info("   - マスク後Token：{}", maskedToken);
            log.info("   - Token有効期限：{}", accessToken.getExpiresAt());
            log.info("   - Token取得時間：{}", getFormattedCurrentTime());

            // ステップ3：Redisクライアント初期化（终极修复：使用Jedis 5.1.2官方构造器）
            log.info("\n===== Redisクライアント初期化開始（jedis 5.1.2） =====");
            // 1. 客户端配置（包含SSL + Token密码 + 超时，核心！）
            JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                    .ssl(true) // 强制启用TLS（Azure Redis必须）
                    .user("4946c2a3-18ec-42ee-aac6-14d4344bfb5e")
                    .password(accessToken.getToken()) // Entra Token作为密码
                    .connectionTimeoutMillis(5000) // 连接超时5秒
                    .socketTimeoutMillis(3000) // 读写超时3秒
                    .build();

            // 2. Jedis 5.1.2 官方支持的构造器（host + port + clientConfig）
            // 这是唯一100%匹配的重载，无任何多余参数
            redisClient = new JedisPooled(
                    new HostAndPort(redisHost,redisPort),
//                    redisHost,    // Redis主机
//                    redisPort,    // Redis端口
                    clientConfig  // 客户端配置（含SSL/Token/超时）
            );
            log.info("✅ Redisクライアント初期化成功：host={}, port={}, SSL={}",
                    redisHost, redisPort, clientConfig.isSsl());

            // ステップ4：Redis操作（SET/GET）
            log.info("\n===== Redis操作開始（SET/GET） =====");
            String timeStr = getFormattedCurrentTime();
            String key = "テストキー-" + timeStr;
            String value = "テスト値-" + timeStr;
            log.info("🔧 Redis SET操作：key={}, value={}", key, value);

            // Redis SET実行（Jedis 5.1.2兼容）
            String setResult = redisClient.set(key, value);
            log.info("✅ Redis SET操作成功：結果={}", setResult);

            // Redis GET実行
            log.info("🔧 Redis GET操作：key={}", key);
            String getResult = redisClient.get(key);
            log.info("✅ Redis GET操作成功：取得値={}", getResult);

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
                    "tokenExpiresAt", accessToken.getExpiresAt().toString(),
                    "key", key,
                    "value", getResult
            ));
            return ResponseEntity.ok(response);

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
            // リソース解放
            if (redisClient != null) {
                try {
                    redisClient.close();
                    log.info("✅ Redisクライアントを正常にクローズしました");
                } catch (Exception e) {
                    log.error("❌ Redisクライアントクローズ失敗", e);
                }
            }
            log.info("\n===== Redis接続テスト終了 =====\n");
        }
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