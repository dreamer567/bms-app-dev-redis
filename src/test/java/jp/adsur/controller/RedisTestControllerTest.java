//package jp.adsur.controller;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.ValueOperations;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.test.web.servlet.ResultActions;
//
//import static org.hamcrest.Matchers.containsString;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
///**
// * RedisTestController的单元测试（基于MockMvc）
// * 核心：模拟RedisTemplate，不依赖真实Redis服务
// */
//// 仅加载RedisTestController，轻量级测试控制器层
//@WebMvcTest(RedisTestController.class)
//public class RedisTestControllerTest {
//
//    // 注入MockMvc，用于模拟HTTP请求
//    @Autowired
//    private MockMvc mockMvc;
//
//    // 模拟RedisTemplate，避免调用真实Redis服务
//    @MockBean
//    private RedisTemplate<String, String> redisTemplate;
//
//    // 模拟RedisTemplate的ValueOperations（字符串操作）
//    @MockBean
//    private ValueOperations<String, String> valueOperations;
//
//    // 测试用常量
//    private static final String TEST_KEY = "testKey123";
//    private static final String TEST_VALUE = "uuid-123456789";
//
//    /**
//     * 测试前置准备：模拟RedisTemplate.opsForValue()返回mock的ValueOperations
//     */
//    @BeforeEach
//    void setUp() {
//        // 当调用redisTemplate.opsForValue()时，返回模拟的valueOperations
//        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
//    }
//
//    /**
//     * 测试场景1：Redis写入接口 - 成功写入
//     */
//    @Test
//    void setRedisValue_Success() throws Exception {
//        // 1. 模拟行为：当调用valueOperations.set()时，无异常（成功写入）
//        doNothing().when(valueOperations).set(anyString(), anyString());
//
//        // 2. 模拟HTTP GET请求：/redis/set/{key}
//        ResultActions result = mockMvc.perform(get("/redis/set/" + TEST_KEY));
//
//        // 3. 验证响应结果
//        result.andExpect(status().isOk()) // HTTP状态码200
//                .andExpect(content().string(containsString("成功写入Redis：key=" + TEST_KEY))) // 响应内容包含成功信息
//                .andExpect(content().string(containsString("value="))); // 包含生成的value
//
//        // 4. 验证RedisTemplate的方法被调用（确保逻辑走到了写Redis步骤）
//        verify(valueOperations, times(1)).set(eq(TEST_KEY), anyString());
//    }
//
//    /**
//     * 测试场景2：Redis写入接口 - 写入失败（模拟异常）
//     */
//    @Test
//    void setRedisValue_Failure() throws Exception {
//        // 1. 模拟行为：当调用valueOperations.set()时抛出异常
//        doThrow(new RuntimeException("Redis连接失败")).when(valueOperations).set(anyString(), anyString());
//
//        // 2. 模拟HTTP GET请求
//        ResultActions result = mockMvc.perform(get("/redis/set/" + TEST_KEY));
//
//        // 3. 验证响应结果
//        result.andExpect(status().isOk()) // 接口仍返回200（业务层捕获异常）
//                .andExpect(content().string(containsString("写入失败：Redis连接失败"))); // 响应包含失败信息
//
//        // 4. 验证方法被调用
//        verify(valueOperations, times(1)).set(eq(TEST_KEY), anyString());
//    }
//
//    /**
//     * 测试场景3：Redis读取接口 - 成功读取（key存在）
//     */
//    @Test
//    void getRedisValue_KeyExists() throws Exception {
//        // 1. 模拟行为：当调用valueOperations.get()时返回指定值
//        when(valueOperations.get(TEST_KEY)).thenReturn(TEST_VALUE);
//
//        // 2. 模拟HTTP GET请求
//        ResultActions result = mockMvc.perform(get("/redis/get/" + TEST_KEY));
//
//        // 3. 验证响应结果
//        result.andExpect(status().isOk())
//                .andExpect(content().string(String.format("读取Redis：key=%s, value=%s", TEST_KEY, TEST_VALUE)));
//
//        // 4. 验证方法被调用
//        verify(valueOperations, times(1)).get(TEST_KEY);
//    }
//
//    /**
//     * 测试场景4：Redis读取接口 - 读取失败（key不存在）
//     */
//    @Test
//    void getRedisValue_KeyNotExists() throws Exception {
//        // 1. 模拟行为：当调用valueOperations.get()时返回null（key不存在）
//        when(valueOperations.get(TEST_KEY)).thenReturn(null);
//
//        // 2. 模拟HTTP GET请求
//        ResultActions result = mockMvc.perform(get("/redis/get/" + TEST_KEY));
//
//        // 3. 验证响应结果
//        result.andExpect(status().isOk())
//                .andExpect(content().string(String.format("读取Redis：key=%s, value=不存在", TEST_KEY)));
//
//        // 4. 验证方法被调用
//        verify(valueOperations, times(1)).get(TEST_KEY);
//    }
//
//    /**
//     * 测试场景5：Redis读取接口 - 读取异常（模拟Redis报错）
//     */
//    @Test
//    void getRedisValue_Failure() throws Exception {
//        // 1. 模拟行为：当调用valueOperations.get()时抛出异常
//        when(valueOperations.get(TEST_KEY)).thenThrow(new RuntimeException("Redis读取超时"));
//
//        // 2. 模拟HTTP GET请求
//        ResultActions result = mockMvc.perform(get("/redis/get/" + TEST_KEY));
//
//        // 3. 验证响应结果
//        result.andExpect(status().isOk())
//                .andExpect(content().string(containsString("读取失败：Redis读取超时")));
//
//        // 4. 验证方法被调用
//        verify(valueOperations, times(1)).get(TEST_KEY);
//    }
//}