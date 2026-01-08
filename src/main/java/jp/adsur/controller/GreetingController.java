package jp.adsur.controller;

import jp.adsur.db.entity.Greeting;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class GreetingController {

	private static final String template = "Hello, %s!";
	private final AtomicLong counter = new AtomicLong();

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

	// 数据库连接参数（请替换为你的实际配置）
	private static final String URL = "jdbc:mysql://bmssql.privatelink.mysql.database.azure.com:3306/demo_schema";
	private static final String USER = "sqladmin";
	private static final String PASSWORD = "Adsur@@tokyo";

	/**
	 * 私有方法：查询user表中的姓名字段（假设查询id=1的用户，可根据需求修改条件）
	 * @return 姓名（字符串），若查询失败返回错误信息
	 */
	private String queryUserName() {
		// 初始化返回结果
		String userName = "未查询到用户信息";

		// 使用try-with-resources自动关闭连接、语句、结果集
		try (
				Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
				// SQL：查询user表的name字段（条件可根据实际需求修改，这里以id=1为例）
				PreparedStatement ps = connection.prepareStatement("SELECT username FROM user_info WHERE id = ?")
		) {
			// 设置查询条件（例如查询id=1的用户，可修改参数值）
			ps.setInt(1, 1);

			// 执行查询
			try (ResultSet rs = ps.executeQuery()) {
				// 若有查询结果，获取姓名字段
				if (rs.next()) {
					userName = rs.getString("username"); // 注意：字段名需与user表中的姓名字段一致（如name/username等）
				}
			}

		} catch (SQLException e) {
			// 捕获异常，返回错误信息
			userName = "query failed: " + e.getMessage();
		}

		return userName;
	}
}
