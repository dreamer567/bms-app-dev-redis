package jp.adsur.controller.pojo;

import jp.adsur.db.entity.ScimUser;
import lombok.Data;
import java.util.List;

/**
 * SCIM 2.0 标准的列表响应格式（ListResponse）
 */
@Data
public class ScimListResponse {
    // SCIM规范要求的schema标识
    private List<String> schemas = List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    // 匹配的总结果数（0表示无结果）
    private int totalResults;

    // 结果列表（首字母大写，SCIM规范要求）
    private List<Object> Resources;

    // 起始索引（默认1）
    private int startIndex = 1;

    // 每页条数（默认20）
    private int itemsPerPage = 20;
}