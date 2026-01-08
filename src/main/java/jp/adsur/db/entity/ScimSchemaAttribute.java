package jp.adsur.db.entity;

import lombok.Data;

@Data
public class ScimSchemaAttribute {
    private String name;
    private String type;
    private boolean multiValued;
    private String description;
    private boolean required;
    private boolean caseExact;
    private String mutability;
    private String returned;
    private String uniqueness;
}