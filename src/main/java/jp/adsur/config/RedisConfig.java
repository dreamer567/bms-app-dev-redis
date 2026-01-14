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
import io.lettuce.core.resource.DnsResolver;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        // 1. 打印Azure环境变量
        log.info("=== 打印Azure App Service环境变量 ===");
        Map<String, String> env = System.getenv();
        log.info("IDENTITY_ENDPOINT: {}", env.get("IDENTITY_ENDPOINT"));
        log.info("IDENTITY_HEADER: {}", env.get("IDENTITY_HEADER"));
        log.info("WEBSITE_SITE_NAME: {}", env.get("WEBSITE_SITE_NAME"));
        log.info("WEBSITE_INSTANCE_ID: {}", env.get("WEBSITE_INSTANCE_ID"));

        // 2. 初始化托管标识凭证
        log.info("=== 开始初始化ManagedIdentityCredential ===");
        ManagedIdentityCredential tempCredential = null;
        try {
            tempCredential = new ManagedIdentityCredentialBuilder().build();
            log.info("=== ManagedIdentityCredential初始化成功 ===");
        } catch (Exception e) {
            log.error("=== ManagedIdentityCredential初始化失败 ===", e);
        }
        this.credential = tempCredential;

        // 3. 延迟5秒刷新Token
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

    private void refreshToken() {
        if (credential == null) {
            log.error("=== 托管标识凭证未初始化 ===");
            return;
        }

        TokenRequestContext requestContext = new TokenRequestContext();
        requestContext.addScopes(REDIS_SCOPE);
        log.info("=== 开始请求Redis Token ===");
        log.info("请求作用域：{}", REDIS_SCOPE);
        log.info("App Service标识对象ID：{}", System.getenv("IDENTITY_HEADER"));

        try {
            Mono<AccessToken> tokenMono = credential.getToken(requestContext)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .jitter(0.5)
                            .filter(e -> e instanceof Exception))
                    .timeout(Duration.ofSeconds(15))
                    .doOnError(e -> log.error("=== Token请求错误详情 ===", e))
                    .doOnNext(t -> {
                        cachedToken.set(t.getToken());
                        tokenExpireTime.set(t.getExpiresAt().toInstant());
                        log.info("=== Token获取成功 ===");
                        log.info("Token长度：{}", t.getToken().length());
                        log.info("过期时间：{}", TIME_FORMATTER.format(t.getExpiresAt()));
                    })
                    .onErrorResume(e -> {
                        log.error("=== Token请求降级，错误原因：{} ===", e.getMessage(), e);
                        return Mono.empty();
                    });

            tokenMono.subscribe();
        } catch (Exception e) {
            log.error("=== Token请求异常，完整堆栈：{} ===", e.getMessage(), e);
        }
    }

    private String getValidToken() {
        if (cachedToken.get() == null || tokenExpireTime.get() == null
                || tokenExpireTime.get().minusSeconds(300).isBefore(Instant.now())) {
            refreshToken();
        }
        return cachedToken.get() == null ? "" : cachedToken.get();
    }

    /**
     * 核心修改：自定义DNS解析 + 优化Netty连接配置
     */
    private ClientResources getClientResources() {
        return DefaultClientResources.builder()
                // 自定义DNS解析（解决Netty DNS解析失败问题）
                .dnsResolver(new DnsResolver() {
                    @Override
                    public InetAddress[] resolve(String host) throws UnknownHostException {
                        log.info("=== 手动解析Redis域名：{} ===", host);
                        InetAddress[] addresses = InetAddress.getAllByName(host);
                        for (InetAddress addr : addresses) {
                            log.info("=== 解析结果：{} ===", addr.getHostAddress());
                        }
                        return addresses;
                    }
                })
                // 连接超时配置
                .ioThreadPoolSize(8)
                .computationThreadPoolSize(4)
                .build();
    }

    private LettuceClientConfiguration getLettuceConfig() {
        ClientResources clientResources = getClientResources();
        SslOptions sslOptions = SslOptions.create();

        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .sslOptions(sslOptions)
                .timeoutOptions(io.lettuce.core.TimeoutOptions.builder()
                        .fixedTimeout(Duration.ofSeconds(10))
                        .build())
                .pingBeforeActivateConnection(true)
                .autoReconnect(true)
                .build();

        return LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .clientOptions(clientOptions)
                .useSsl()
//                .commandTimeout(Duration.ofSeconds(10))
//                .shutdownTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 手动解析Redis域名（避免Netty自动解析失败）
        String resolvedHost = redisHost;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(redisHost);
            if (addresses.length > 0) {
                resolvedHost = addresses[0].getHostAddress();
                log.info("=== Redis域名解析完成：{} → {} ===", redisHost, resolvedHost);
            }
        } catch (UnknownHostException e) {
            log.error("=== Redis域名解析失败 ===", e);
        }

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(resolvedHost, redisPort);
        String token = getValidToken();
        if (!token.isEmpty()) {
            redisConfig.setPassword(token);
            log.info("=== Redis连接配置：使用Token认证 ===");
        } else {
            log.warn("=== Redis连接配置：Token为空，使用空密码（仅调试）===");
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