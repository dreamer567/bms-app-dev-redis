package jp.adsur.db.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完全符合 SCIM 2.0 规范的 Group 成员定义
 * 参考：https://datatracker.ietf.org/doc/html/rfc7643#section-4.1
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScimGroupMember {
    // 核心必填字段：成员唯一标识符（如 User ID）
    private String value;

    // 可选字段：成员资源的URI引用（$开头的字段需用@JsonProperty指定序列化名称）
    @JsonProperty("$ref") // 关键：确保序列化后字段名为"$ref"（而非"ref"）
    private String ref; // Java中不能用$开头命名变量，所以用ref，序列化时映射为$ref

    // 可选字段：成员资源类型（User/Group）
    private String type;

    // 可选字段：成员显示名称
    private String display;

    // 可选字段：是否为主成员
    private Boolean primary = false; // 默认false

    public ScimGroupMember(String display, String type, String path) {
        this.ref = path;
        this.type = type;
        this.display = display;
    }
}