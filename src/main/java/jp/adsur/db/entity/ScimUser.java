package jp.adsur.db.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScimUser {

    // SCIM required
    private List<String> schemas = List.of(
            "urn:ietf:params:scim:schemas:core:2.0:User",
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
    );

    private String id;
    private String externalId;
    private String userName;

    // name
    private ScimName name;

    // arrays
    private List<ScimEmail> emails;
    private List<ScimPhoneNumber> phoneNumbers;
    private List<ScimAddress> addresses;

    // enterprise extension
    private ScimEnterpriseExtension enterpriseExtension;

    private boolean active = true;

    // meta section
    private ScimMeta meta;
    // 数据库相关字段（非SCIM规范，仅存储用）
    private LocalDateTime createdTime;
    private LocalDateTime lastModifiedTime;
}
