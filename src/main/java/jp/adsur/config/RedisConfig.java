package jp.adsur.config;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
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
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 容错版Redis配置：Token获取失败不崩溃，应用能启动，仅打日志
 */
@Configuration
public class RedisConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String REDIS_SCOPE = "https://redis.azure.com/.default";

    @Value("${spring.redis.host}")
    private String redisHost;
    @Value("${spring.redis.port:6380}")
    private int redisPort;

    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpireTime = new AtomicReference<>();
    private final ManagedIdentityCredential credential;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public RedisConfig() {
        // 1. 打印Azure环境变量（关键：验证是否注入托管标识环境变量）
        log.info("=== 打印Azure App Service环境变量 ===");
        Map<String, String> env = System.getenv();
        log.info("IDENTITY_ENDPOINT: {}", env.get("IDENTITY_ENDPOINT")); // 托管标识核心变量，启用后必有值
        log.info("IDENTITY_HEADER: {}", env.get("IDENTITY_HEADER"));     // 托管标识核心变量，启用后必有值
        log.info("WEBSITE_SITE_NAME: {}", env.get("WEBSITE_SITE_NAME"));
        log.info("WEBSITE_INSTANCE_ID: {}", env.get("WEBSITE_INSTANCE_ID"));

        // 2. 初始化托管标识凭证（失败仅打日志，不抛异常）
        log.info("=== 开始初始化ManagedIdentityCredential ===");
        ManagedIdentityCredential tempCredential = null;
        try {
            tempCredential = new ManagedIdentityCredentialBuilder().build();
            log.info("=== ManagedIdentityCredential初始化成功 ===");
        } catch (Exception e) {
            log.error("=== ManagedIdentityCredential初始化失败 ===", e);
            // 初始化失败也不抛异常，保证Bean能创建
        }
        this.credential = tempCredential;

        // 3. 延迟5秒刷新Token（核心：不在构造函数同步执行，避免启动时崩溃）
        scheduler.schedule(this::refreshToken, 5, TimeUnit.SECONDS);
        // 4. 定时刷新：每55分钟执行一次
        scheduler.scheduleAtFixedRate(this::refreshTokenAndResetConnection, 5, 55, TimeUnit.MINUTES);
    }

    private void refreshTokenAndResetConnection() {
        try {
            refreshToken();
            LettuceConnectionFactory factory = redisConnectionFactory();
            if (factory != null) {
                factory.resetConnection();
                log.info("Redis Token定时刷新完成，过期时间：{}",
                        tokenExpireTime.get() != null ? TIME_FORMATTER.format(tokenExpireTime.get()) : "未知");
            }
        } catch (Exception e) {
            log.error("Redis Token定时刷新失败", e);
        }
    }

    /**
     * 核心修改：Token获取失败仅打日志，不抛任何异常
     */
    private void refreshToken() {
        // 凭证未初始化，直接返回
        if (credential == null) {
            log.error("=== 托管标识凭证未初始化，跳过Token获取 ===");
            return;
        }

        TokenRequestContext requestContext = new TokenRequestContext();
        requestContext.addScopes(REDIS_SCOPE);
        log.info("=== 开始请求Redis Token，作用域：{} ===", REDIS_SCOPE);

        try {
            Mono<AccessToken> tokenMono = credential.getToken(requestContext)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)) // 减少重试次数，加快启动
                            .jitter(0.5)
                            .filter(e -> e instanceof Exception))
                    .timeout(Duration.ofSeconds(10)) // 缩短超时时间
                    .doOnNext(t -> {
                        cachedToken.set(t.getToken());
                        tokenExpireTime.set(t.getExpiresAt().toInstant());
                        log.info("=== 成功获取Redis Token，长度：{}，过期时间：{} ===",
                                t.getToken().length(), TIME_FORMATTER.format(t.getExpiresAt()));
                    })
                    .onErrorResume(e -> {
                        log.error("=== Token请求失败（非致命错误，应用继续运行）===", e);
                        return Mono.empty();
                    });

            // 非阻塞获取，失败不抛异常
            tokenMono.subscribe();

        } catch (Exception e) {
            log.error("=== 获取Redis Token异常（非致命错误，应用继续运行）===", e);
            // 绝对不抛RuntimeException！！！保证应用能启动
        }
    }

    private String getValidToken() {
        // Token为空时返回空字符串，不抛异常
        if (cachedToken.get() == null || tokenExpireTime.get() == null
                || tokenExpireTime.get().minusSeconds(300).isBefore(Instant.now())) {
            refreshToken();
        }
        return cachedToken.get() == null ? "" : cachedToken.get();
    }

    private LettuceClientConfiguration getLettuceConfig() {
        ClientResources clientResources = DefaultClientResources.create();
        SslOptions sslOptions = SslOptions.create();

        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .sslOptions(sslOptions)
                .build();

        return LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .clientOptions(clientOptions)
                .useSsl()
//                .timeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        String token = getValidToken();
        if (!token.isEmpty()) {
            redisConfig.setPassword(token);
        } else {
            log.warn("=== Token为空，Redis连接工厂使用空密码（仅调试）===");
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, getLettuceConfig());
        factory.setValidateConnection(true);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    @javax.annotation.PreDestroy
    public void destroyScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}