package jp.adsur.controller.pojo;

import lombok.Data;

import java.util.List;

@Data
public class ScimPatchOp {
    // PATCH操作的Schema
    private List<String> schemas = List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp");
    // 操作列表
    private List<ScimPatchOperation> Operations;
}