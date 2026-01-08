package jp.adsur.db.entity;

import lombok.Data;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ScimGroup {
    // SCIM核心Schema
    private List<String> schemas = List.of("urn:ietf:params:scim:schemas:core:2.0:Group");
    // 系统生成的唯一ID
    private String id;
    // 外部ID
    private String externalId;
    // 组名称（必选）
    private String displayName;
    // 组成员列表（默认空列表）
    private List<ScimGroupMember> members = new ArrayList<>();
    // 元数据
    private ScimMeta meta = new ScimMeta();

    public ScimGroup() {
        meta.setResourceType("Group");
        Instant now = Instant.now();
        meta.setCreated(now.toString());
        meta.setLastModified(now.toString());
    }
}