package jp.adsur.db.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScimAddress {
    private String streetAddress;
    private String locality;
    private String region;
    private String postalCode;
    private String country;
    private String type;
}
