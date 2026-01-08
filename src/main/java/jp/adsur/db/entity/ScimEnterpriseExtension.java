package jp.adsur.db.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScimEnterpriseExtension {

    private String employeeNumber;
    private String costCenter;
    private String organization;
    private String division;
    private String department;

    private ScimManager manager;
}
