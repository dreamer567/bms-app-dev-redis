package jp.adsur.db.entity;

import lombok.Data;

import java.util.List;

@Data
public class ScimSchema {
    private List<String> schemas = List.of("urn:ietf:params:scim:schemas:core:2.0:Schema");
    private String id;
    private String name;
    private String description;
    private List<ScimSchemaAttribute> attributes;
    private ScimMeta meta;
}