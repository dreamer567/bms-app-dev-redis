package jp.adsur.config;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import java.util.HashMap;
import java.util.Map;

public class RedisConfig {
    // 環境変数からAzureの完整なRedis接続文字列を取得
    private static final String REDIS_CONNECTION_STRING = System.getenv("REDIS_CONNECTION_STRING")
            // ローカルテスト用に完整な文字列で置き換え（実際のRedis名とAccess Keyに置き換え）
            != null ? System.getenv("REDIS_CONNECTION_STRING")
            : "bms-dev-cache-001.redis.cache.windows.net:6379,password=jI7B2eul6bHnuDatwwdFvqW088x5lwK9MAzCaDsb00c=,abortConnect=false";

    // 接続池設定
    private static final JedisPoolConfig poolConfig = new JedisPoolConfig();
    static {
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
    }

    // Azure接続文字列を解析するメソッド（核心）
    private static Map<String, String> parseAzureRedisConnStr(String connStr) {
        Map<String, String> result = new HashMap<>();
        try {
            // 1. 最初のカンマでhost:portとその他パラメータに分割
            String[] mainParts = connStr.split(",", 2);
            String hostPortPart = mainParts[0].trim();
            String paramsPart = mainParts.length > 1 ? mainParts[1].trim() : "";

            // 2. host:portを分割
            String[] hostPort = hostPortPart.split(":");
            result.put("host", hostPort[0]);
            result.put("port", hostPort.length > 1 ? hostPort[1] : String.valueOf(Protocol.DEFAULT_PORT));

            // 3. passwordなどのパラメータを解析
            if (!paramsPart.isEmpty()) {
                String[] params = paramsPart.split(",");
                for (String param : params) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        result.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Redis接続文字列の解析に失敗しました：" + connStr, e);
        }
        return result;
    }

    // JedisPoolを作成（解析したパラメータを個別に渡す）
    public static JedisPool getJedisPool() {
        Map<String, String> connParams = parseAzureRedisConnStr(REDIS_CONNECTION_STRING);

        // パラメータ抽出
        String host = connParams.get("host");
        int port = Integer.parseInt(connParams.get("port"));
        String password = connParams.get("password");
        int timeout = Protocol.DEFAULT_TIMEOUT; // タイムアウト（必要に応じて調整）

        // パラメータを個別に渡す（URI解析を回避）
        return new JedisPool(poolConfig, host, port, timeout, password);
    }
}