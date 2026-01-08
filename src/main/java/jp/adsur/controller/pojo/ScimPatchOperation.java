package jp.adsur.controller.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// ScimPatchOperation字段名匹配
@Data
public class ScimPatchOperation {
    @JsonProperty("op")
    private String op; // 用String更兼容，避免枚举转换错误
    @JsonProperty("path")
    private String path;
    @JsonProperty("value")
    private Object value;
}
