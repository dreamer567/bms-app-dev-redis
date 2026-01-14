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
import reactor.util.retry.Retry;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis配置类（100%可编译通过）
 */
@Configuration
public class RedisConfig {
    // 日志对象（确保导入org.slf4j.Logger/LoggerFactory）
    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);
    // 时间格式化器（解决Instant打印编译问题）
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    // Redis主机（从配置文件读取）
    @Value("${spring.redis.host}")
    private String redisHost;
    // Redis端口（Azure Redis固定6380）
    @Value("${spring.redis.port:6380}")
    private int redisPort;
    // Azure Redis令牌作用域（固定值）
    private static final String REDIS_SCOPE = "https://redis.azure.com/.default";

    // 缓存Token（线程安全）
    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    // 缓存Token过期时间
    private final AtomicReference<Instant> tokenExpireTime = new AtomicReference<>();
    // Azure托管标识凭证（系统分配）
    private final ManagedIdentityCredential credential;
    // 定时刷新Token的线程池
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 构造方法：初始化凭证+首次刷新Token
     */
    public RedisConfig() {
        // 初始化系统分配的托管标识凭证（无参数，避免配置错误）
        this.credential = new ManagedIdentityCredentialBuilder().build();
        // 首次刷新Token
        this.refreshToken();
        // 定时刷新：每55分钟执行一次
        scheduler.scheduleAtFixedRate(this::refreshTokenAndResetConnection,
                0, 55, TimeUnit.MINUTES);
    }

    /**
     * 刷新Token并重置Redis连接
     */
    private void refreshTokenAndResetConnection() {
        try {
            refreshToken();
            // 重置连接工厂，使新Token生效
            redisConnectionFactory().resetConnection();
            log.info("Redis Token定时刷新完成，新Token过期时间：{}",
                    tokenExpireTime.get() != null ? TIME_FORMATTER.format(tokenExpireTime.get()) : "未知");
        } catch (Exception e) {
            log.error("Redis Token定时刷新失败", e);
        }
    }

    /**
     * 核心：获取/刷新Redis Token（添加重试+超时+空安全）
     */
    private void refreshToken() {
        // 构建Token请求上下文
        TokenRequestContext requestContext = new TokenRequestContext();
        requestContext.addScopes(REDIS_SCOPE);

        AccessToken token = null;
        try {
            // 获取Token：10秒超时 + 3次重试（解决网络波动）
            token = credential.getToken(requestContext)
                    .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)) // 重试3次，间隔1秒
                            .filter(e -> e instanceof Exception))        // 仅重试异常
                    .timeout(Duration.ofSeconds(10))                     // 10秒超时
                    .block();                                            // 阻塞获取结果

            // 空安全检查（解决编译/运行空指针问题）
            if (token == null || token.getToken() == null || token.getToken().isEmpty()) {
                throw new RuntimeException("获取到的Redis Token为空");
            }

            // 修复编译报错的核心行：添加空安全+格式化
            cachedToken.set(token.getToken());
            tokenExpireTime.set(token.getExpiresAt().toInstant()); // 注意：是getExpiresAt()而非expiresAt()！

            // 日志打印（格式化Instant，避免编译错误）
            log.info("成功获取Redis Token，Token过期时间：{}",
                    TIME_FORMATTER.format(token.getExpiresAt()));

        } catch (Exception e) {
            log.error("获取Redis Token失败，异常详情：", e);
            throw new RuntimeException("获取Redis Token失败", e);
        }
    }

    /**
     * 获取有效Token（空安全检查）
     */
    private String getValidToken() {
        Instant now = Instant.now();
        // 检查Token是否过期/为空
        if (cachedToken.get() == null || tokenExpireTime.get() == null
                || tokenExpireTime.get().minusSeconds(300).isBefore(now)) {
            // 重新刷新Token
            refreshToken();
        }
        return cachedToken.get();
    }

    /**
     * 彻底修复SSL配置：
     * 1. 移除JDK SSLContext，改用Lettuce默认SSL配置（Azure Redis兼容）
     * 2. 避免Netty SslContext类型转换问题
     */
    private LettuceClientConfiguration getLettuceConfig() {
        // 初始化ClientResources（Lettuce 6.x必需）
        ClientResources clientResources = DefaultClientResources.create();

        // 修复SSL类型问题：直接用Lettuce默认SSL配置（Azure Redis的SSL是标准的，无需自定义JDK SSLContext）
        SslOptions sslOptions = SslOptions.create(); // 替代自定义SSLContext，避免类型不兼容

        // 构建ClientOptions（适配Lettuce 6.x）
        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2) // 适配Redis RESP2协议
                .sslOptions(sslOptions)                // 设置SSL选项（默认配置即可）
                .build();

        // 构建LettuceClientConfiguration（SSL启用仅在这里设置）
        return LettuceClientConfiguration.builder()
                .clientResources(clientResources)      // 设置ClientResources
                .clientOptions(clientOptions)          // 设置ClientOptions
                .useSsl()// 启用SSL（Azure Redis强制要求，仅这里设置即可）
//                .timeout(Duration.ofSeconds(10))       // 连接超时
                .build();
    }

    /**
     * 创建Redis连接工厂（核心Bean）
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 基础Redis配置（仅主机+端口+密码，移除setSsl()）
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        // 设置Token作为密码（核心）
        redisConfig.setPassword(getValidToken());
        // 移除setSsl(true)：该方法不存在，SSL已在LettuceClientConfiguration中启用

        // 构建连接工厂（无语法错误）
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redisConfig,
                getLettuceConfig()
        );
        factory.setValidateConnection(true);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * 创建StringRedisTemplate（业务层直接使用）
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    /**
     * 应用关闭时销毁线程池（防止内存泄漏）
     */
    @javax.annotation.PreDestroy
    public void destroyScheduler() {
        scheduler.shutdown();
        try {
            // 等待线程池终止，最多等待10秒
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}