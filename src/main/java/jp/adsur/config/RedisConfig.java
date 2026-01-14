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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis配置类（修复版本兼容+Token超时降级）
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
        // 初始化托管标识凭证（添加详细日志）
        log.info("开始初始化ManagedIdentityCredential（系统分配）");
        try {
            this.credential = new ManagedIdentityCredentialBuilder().build();
            log.info("ManagedIdentityCredential初始化成功");
        } catch (Exception e) {
            log.error("ManagedIdentityCredential初始化失败", e);
            throw new RuntimeException("托管标识凭证初始化失败", e);
        }
        // 首次刷新Token（添加降级）
        this.refreshToken();
        // 定时刷新：每55分钟执行一次
        scheduler.scheduleAtFixedRate(this::refreshTokenAndResetConnection, 0, 55, TimeUnit.MINUTES);
    }

    private void refreshTokenAndResetConnection() {
        try {
            refreshToken();
            redisConnectionFactory().resetConnection();
            log.info("Redis Token定时刷新完成，过期时间：{}",
                    tokenExpireTime.get() != null ? TIME_FORMATTER.format(tokenExpireTime.get()) : "未知");
        } catch (Exception e) {
            log.error("Redis Token定时刷新失败", e);
        }
    }

    /**
     * 核心：获取/刷新Redis Token（延长超时+指数退避重试+详细日志）
     */
    private void refreshToken() {
        TokenRequestContext requestContext = new TokenRequestContext();
        requestContext.addScopes(REDIS_SCOPE);
        log.info("开始请求Redis Token，作用域：{}", REDIS_SCOPE);

        AccessToken token = null;
        try {
            // 获取Token：15秒超时 + 5次指数退避重试（解决网络波动）
            token = credential.getToken(requestContext)
                    .retryWhen(Retry.backoff(5, Duration.ofSeconds(1)) // 5次重试，初始间隔1秒，指数退避
                            .jitter(0.5) // 随机抖动，避免并发重试冲突
                            .filter(e -> e instanceof Exception) // 仅重试异常
                            .onRetryExhaustedThrow((spec, sig) -> new TimeoutException("Token请求重试5次仍失败")))
                    .timeout(Duration.ofSeconds(15)) // 延长超时到15秒
                    .onErrorResume(e -> { // 降级：打印详细错误+返回空
                        log.error("Token请求失败，触发降级，错误：{}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .block();

            // 空安全检查+明确提示
            if (token == null || token.getToken() == null || token.getToken().isEmpty()) {
                log.error("获取的Redis Token为空！请检查Azure配置：\n" +
                        "1. App Service → 标识 → 系统分配 → 状态=开启\n" +
                        "2. Redis实例 → 访问控制(IAM) → 给App Service标识授予Redis Cache Contributor权限\n" +
                        "3. App Service网络能访问https://login.microsoftonline.com");
                throw new RuntimeException("获取的Redis Token为空，Azure托管标识配置错误");
            }

            // 适配azure-core 1.28.0：expiresAt() 无get前缀
            cachedToken.set(token.getToken());
            tokenExpireTime.set(token.getExpiresAt().toInstant());

            // 日志打印（脱敏Token）
            log.info("成功获取Redis Token，过期时间：{}，Token长度：{}",
                    TIME_FORMATTER.format(token.getExpiresAt()), token.getToken().length());

        } catch (Exception e) {
            log.error("获取Redis Token失败，根因：{}", e.getMessage(), e);
            throw new RuntimeException("获取Redis Token失败，请优先检查Azure配置：\n" +
                    "1. App Service系统分配标识是否启用\n" +
                    "2. Redis实例IAM是否给标识分配Redis权限\n" +
                    "3. App Service网络能否访问Azure AD（login.microsoftonline.com）", e);
        }
    }

    private String getValidToken() {
        Instant now = Instant.now();
        if (cachedToken.get() == null || tokenExpireTime.get() == null
                || tokenExpireTime.get().minusSeconds(300).isBefore(now)) {
            refreshToken();
        }
        return cachedToken.get();
    }

    /**
     * 修复SSL配置（适配Lettuce 6.x）
     */
    private LettuceClientConfiguration getLettuceConfig() {
        ClientResources clientResources = DefaultClientResources.create();
        // 默认SSL配置（Azure Redis兼容）
        SslOptions sslOptions = SslOptions.create();

        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .sslOptions(sslOptions)
                .build();

        return LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .clientOptions(clientOptions)
                .useSsl() // 启用SSL（Azure Redis强制要求）
//                .timeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        redisConfig.setPassword(getValidToken());

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

    // 自定义超时异常
    static class TimeoutException extends RuntimeException {
        public TimeoutException(String message) {
            super(message);
        }
    }
}