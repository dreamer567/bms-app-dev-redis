package jp.adsur.db.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScimMeta {
    private String resourceType;
    private String location;
    private String created;
    private String lastModified;
}
