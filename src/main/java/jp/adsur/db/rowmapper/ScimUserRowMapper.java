package jp.adsur.db.rowmapper;

import jp.adsur.db.entity.*;
import jp.adsur.JsonUtils; // 假设你有个工具类能做 fromJsonArray / fromJsonObject
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ScimUserRowMapper implements RowMapper<ScimUser> {

    @Override
    public ScimUser mapRow(ResultSet rs, int rowNum) throws SQLException {
        ScimUser user = new ScimUser();

        // 基础字段
        user.setId(rs.getString("id"));
        user.setExternalId(rs.getString("external_id"));
        user.setUserName(rs.getString("user_name"));
        user.setActive(rs.getBoolean("active"));

        // name
        ScimName name = new ScimName();
        name.setGivenName(rs.getString("given_name"));
        name.setFamilyName(rs.getString("family_name"));
        user.setName(name);

        // emails, phones, addresses
        String emailsJson = rs.getString("emails_json");
        String phonesJson = rs.getString("phones_json");
        String addressesJson = rs.getString("addresses_json");

        List<ScimEmail> emails = JsonUtils.fromJsonArray(emailsJson, ScimEmail.class);
        List<ScimPhoneNumber> phones = JsonUtils.fromJsonArray(phonesJson, ScimPhoneNumber.class);
        List<ScimAddress> addresses = JsonUtils.fromJsonArray(addressesJson, ScimAddress.class);

        user.setEmails(emails);
        user.setPhoneNumbers(phones);
        user.setAddresses(addresses);

        // enterprise extension
        String enterpriseJson = rs.getString("enterprise_json");
        ScimEnterpriseExtension enterprise = JsonUtils.fromJsonObject(enterpriseJson, ScimEnterpriseExtension.class);
        user.setEnterpriseExtension(enterprise);

        // meta
        ScimMeta meta = new ScimMeta();
        meta.setResourceType("User");
        meta.setLocation("/scim/Users/" + user.getId());
        if (rs.getTimestamp("created") != null) {
            meta.setCreated(rs.getTimestamp("created").toLocalDateTime().format(DateTimeFormatter.ISO_DATE_TIME));
        }
        if (rs.getTimestamp("last_modified") != null) {
            meta.setLastModified(rs.getTimestamp("last_modified").toLocalDateTime().format(DateTimeFormatter.ISO_DATE_TIME));
        }
        user.setMeta(meta);

        return user;
    }
}
