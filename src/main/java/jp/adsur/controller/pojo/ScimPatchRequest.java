package jp.adsur.controller.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// 确保ScimPatchRequest的字段名正确（SCIM规范是"Operations"首字母大写）
@Data
public class ScimPatchRequest {
    @JsonProperty("Operations") // 必须匹配JSON中的首字母大写
    private List<ScimPatchOperation> operations;
}