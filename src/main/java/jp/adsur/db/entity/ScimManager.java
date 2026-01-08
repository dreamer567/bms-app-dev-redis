package jp.adsur.db.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScimManager {
    private String managerId;      // SCIM 表示为 "value"
    private String displayName;    // 可选
}
