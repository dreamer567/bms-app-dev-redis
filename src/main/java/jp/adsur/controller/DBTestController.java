package jp.adsur.controller;

import jp.adsur.controller.pojo.Greeting;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@RestController
public class DBTestController {

    private final JdbcTemplate jdbcTemplate;

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    // 构造器注入JdbcTemplate（复用application.yml中的PostgreSQL配置）
    public DBTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        name = queryUserName();
        return "Hello again. It's time " + counter.incrementAndGet() + " for you, " + name;
    }

    @GetMapping("/hello")
    public Greeting hello(@RequestParam(value = "name", defaultValue = "World") String name) {
        name = queryUserName();
        return new Greeting(counter.incrementAndGet(), String.format(template, name));
    }

    /**
     * 【核心修改】使用JdbcTemplate查询PostgreSQL的user_info表（替换原手动JDBC+MySQL逻辑）
     * @return 姓名（字符串），若查询失败返回错误信息
     */
    private String queryUserName() {
        // 初始化返回结果
        String userName = "未查询到用户信息";
        try {
            // PostgreSQL的SQL：注意表名/字段名小写（PostgreSQL默认区分大小写，建议统一小写）
            String sql = "SELECT username FROM user_info WHERE id = ?";

            // 使用JdbcTemplate的queryForObject执行参数化查询（避免SQL注入，适配PostgreSQL）
            // 参数1：SQL语句；参数2：返回值类型；参数3：SQL占位符参数
            userName = jdbcTemplate.queryForObject(
                    sql,
                    String.class,
                    1 // 查询id=1的用户，可根据需求修改
            );

        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // 捕获「查询无结果」异常，保持原有提示
            userName = "unknown";
        } catch (Exception e) {
            // 捕获其他异常（如连接失败、表不存在等），返回错误信息
            userName = "query failed: " + e.getMessage();
        }
        return userName;
    }

    @GetMapping("/test-db")
    public String testDb() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "DB OK, result = " + count;
        } catch (Exception e) {
            return "DB ERROR: " + e.getMessage();
        }
    }
}