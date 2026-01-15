package jp.adsur.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class RedisConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    // 👉 修正1：Redisホスト（保留）
    @Value("${spring.redis.host}")
    private String redisHost;

    // 👉 修正2：ポートのデフォルト値を6379に変更（元は6380）
    @Value("${spring.redis.port:6379}")
    private int redisPort;

    // 👉 修正3：accesskey設定項目を追加（application.yml/propertiesで設定が必要）
    @Value("${spring.redis.accesskey:${REDIS_ACCESS_KEY:}}")
    private String redisAccessKey;

    // 👉 修正4：コンストラクタを簡略化（Token初期化・定期タスクを削除）
    public RedisConfig() {
        // デバッグ用：環境変数の出力のみ保留
        log.info("=== Redis接続の基本環境変数を出力 ===");
        log.info("WEBSITE_SITE_NAME: {}", System.getenv("WEBSITE_SITE_NAME"));
        log.info("Redis設定 - ホスト：{}，ポート：{}", redisHost, redisPort);
    }

    // 👉 削除：Token関連のすべてのメソッド（refreshToken、refreshTokenAndResetConnection、getValidToken）

    // 👉 修正5：SSL設定を削除し、Lettuceクライアント設定を簡略化
    private LettuceClientConfiguration getLettuceConfig() {
        ClientResources clientResources = getClientResources();

        // 👉 SSL関連設定を削除し、ClientOptionsを簡略化
        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2) // オプション：Redis RESP2プロトコルに対応
                .timeoutOptions(io.lettuce.core.TimeoutOptions.builder()
                        .fixedTimeout(Duration.ofSeconds(10))
                        .build())
                .pingBeforeActivateConnection(true)
                .autoReconnect(true)
                .build();

        // 👉 重要：useSsl()とsslOptionsを削除し、SSLを無効にする
        return LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(10)) // オプション：可読性を向上
                .shutdownTimeout(Duration.ofSeconds(5)) // オプション：可読性を向上
                .build();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("=== Redis接続設定：accesskey認証を使用、ポート{} ===", redisPort);

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);

        // 👉 修正6：accesskeyをRedisのパスワードとして使用（核心）
        if (redisAccessKey != null && !redisAccessKey.isEmpty()) {
            redisConfig.setPassword(redisAccessKey);
            log.info("=== Redis接続設定：accesskey認証が有効になりました ===");
        } else {
            log.warn("=== Redis接続設定：accesskeyが空です！設定ファイルを確認してください ===");
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, getLettuceConfig());
        factory.setValidateConnection(true);
        factory.afterPropertiesSet();
        return factory;
    }

    // 👉 ClientResourcesを保留（簡略版：カスタムDNSResolverなし）
    private ClientResources getClientResources() {
        return DefaultClientResources.builder()
                .ioThreadPoolSize(8)
                .computationThreadPoolSize(4)
                .build();
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

}