package jp.adsur;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 JSON 数组字符串解析为对象列表
     *
     * @param jsonArray JSON 字符串，例如 "[{...}, {...}]"
     * @param clazz     List 元素类型
     * @param <T>       泛型类型
     * @return List<T> 对象列表，如果 jsonArray 为 null 或空字符串，返回空列表
     */
    public static <T> List<T> fromJsonArray(String jsonArray, Class<T> clazz) {
        if (jsonArray == null || jsonArray.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(jsonArray, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON array: " + jsonArray, e);
        }
    }

    /**
     * 将 JSON 对象字符串解析为单个对象
     *
     * @param jsonObject JSON 对象字符串，例如 "{...}"
     * @param clazz      对象类型
     * @param <T>        泛型类型
     * @return 解析后的对象，如果 jsonObject 为 null 或空字符串，返回 null
     */
    public static <T> T fromJsonObject(String jsonObject, Class<T> clazz) {
        if (jsonObject == null || jsonObject.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonObject, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON object: " + jsonObject, e);
        }
    }

    /**
     * 将对象序列化为 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON: " + obj, e);
        }
    }
}
