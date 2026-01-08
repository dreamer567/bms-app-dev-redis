package jp.adsur.db.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.adsur.db.entity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class ScimUserRepositoryImpl implements ScimUserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScimUserRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ScimUser findById(String id) {
        String sql = "SELECT * FROM scim_user WHERE id = ?";
        List<ScimUser> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            ScimUser user = new ScimUser();
            user.setId(rs.getString("id"));
            user.setExternalId(rs.getString("external_id"));
            user.setUserName(rs.getString("user_name"));

            // name
            ScimName name = new ScimName();
            name.setGivenName(rs.getString("given_name"));
            name.setFamilyName(rs.getString("family_name"));
            user.setName(name);

            // emails
            String emailsJson = rs.getString("emails");
            if (emailsJson != null) {
                try {
                    List<ScimEmail> emails = objectMapper.readValue(emailsJson, new TypeReference<List<ScimEmail>>() {});
                    user.setEmails(emails);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing emails JSON", e);
                }
            }

            // phoneNumbers
            String phonesJson = rs.getString("phone_numbers");
            if (phonesJson != null) {
                try {
                    List<ScimPhoneNumber> phones = objectMapper.readValue(phonesJson, new TypeReference<List<ScimPhoneNumber>>() {});
                    user.setPhoneNumbers(phones);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing phoneNumbers JSON", e);
                }
            }

            // addresses
            String addressesJson = rs.getString("addresses");
            if (addressesJson != null) {
                try {
                    List<ScimAddress> addresses = objectMapper.readValue(addressesJson, new TypeReference<List<ScimAddress>>() {});
                    user.setAddresses(addresses);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing addresses JSON", e);
                }
            }

            // enterpriseExtension
            String enterpriseJson = rs.getString("enterprise_extension");
            if (enterpriseJson != null) {
                try {
                    ScimEnterpriseExtension ee = objectMapper.readValue(enterpriseJson, ScimEnterpriseExtension.class);
                    user.setEnterpriseExtension(ee);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing enterpriseExtension JSON", e);
                }
            }

            // active
            user.setActive(rs.getBoolean("active"));

            // meta
            ScimMeta meta = new ScimMeta();
            meta.setCreated(rs.getTimestamp("created") != null ? rs.getTimestamp("created").toInstant().toString() : null);
            meta.setLastModified(rs.getTimestamp("last_modified") != null ? rs.getTimestamp("last_modified").toInstant().toString() : null);
            meta.setResourceType("User");
            meta.setLocation("/scim/Users/" + user.getId());
            user.setMeta(meta);

            return user;
        }, id);

        return list.isEmpty() ? null : list.get(0);
    }
    @Override
    public ScimUser save(ScimUser user) {
        try {
            String sql = "INSERT INTO scim_user (" +
                    "external_id, user_name, given_name, family_name, emails, phone_numbers, addresses, " +
                    "enterprise_extension, active, created, last_modified) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql,
                        PreparedStatement.RETURN_GENERATED_KEYS); // 强制指定返回生成的键（关键！）
                ps.setString(1, user.getExternalId());
                ps.setString(2, user.getUserName());
                ps.setString(3, user.getName() != null ? user.getName().getGivenName() : null);
                ps.setString(4, user.getName() != null ? user.getName().getFamilyName() : null);

                // JSON序列化字段（保留原有逻辑）
                try {
                    ps.setString(5, objectMapper.writeValueAsString(user.getEmails()));
                    ps.setString(6, objectMapper.writeValueAsString(user.getPhoneNumbers()));
                    ps.setString(7, objectMapper.writeValueAsString(user.getAddresses()));
                    ps.setString(8, objectMapper.writeValueAsString(user.getEnterpriseExtension()));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }

                ps.setBoolean(9, user.isActive());
                ps.setTimestamp(10, Timestamp.valueOf(java.time.LocalDateTime.now()));
                ps.setTimestamp(11, Timestamp.valueOf(java.time.LocalDateTime.now()));
                return ps;
            }, keyHolder);

            // 关键修复：兼容不同数据库的ID类型（Long/Integer/String），并校验非空
            if (keyHolder.getKey() != null) {
                // 数据库自增ID通常是数字类型（Long/Integer），转为String赋值
                String generatedId = keyHolder.getKey().toString();
                user.setId(generatedId); // 必须将获取的ID设置到user对象中
                System.out.println("Created SCIM user with ID: "+ generatedId); // 调试日志，确认ID获取成功
            } else {
                throw new RuntimeException("Failed to get generated ID for SCIM user");
            }

        } catch (Exception e) {
            System.out.println("Error saving SCIM user");
            e.printStackTrace();
            throw new RuntimeException("Error saving SCIM user", e);
        }
        return user; // 返回带ID的user对象
    }

    @Override
    public void update(ScimUser user) {
        try {
            String sql = "UPDATE scim_user SET " +
                    "external_id=?, user_name=?, given_name=?, family_name=?, emails=?, phone_numbers=?, addresses=?, " +
                    "enterprise_extension=?, active=?, last_modified=? " +
                    "WHERE id=?";
            jdbcTemplate.update(sql,
                    user.getExternalId(),
                    user.getUserName(),
                    user.getName() != null ? user.getName().getGivenName() : null,
                    user.getName() != null ? user.getName().getFamilyName() : null,
                    objectMapper.writeValueAsString(user.getEmails()),
                    objectMapper.writeValueAsString(user.getPhoneNumbers()),
                    objectMapper.writeValueAsString(user.getAddresses()),
                    objectMapper.writeValueAsString(user.getEnterpriseExtension()),
                    user.isActive(),
                    Timestamp.valueOf(java.time.LocalDateTime.now()),
                    user.getId()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error updating SCIM user, ID: " + user.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM scim_user WHERE id=?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public ScimUser findByUserName(String userName) {
        String sql = "SELECT * FROM scim_user WHERE user_name = ?";
        List<ScimUser> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            ScimUser user = new ScimUser();
            user.setId(rs.getString("id"));
            user.setExternalId(rs.getString("external_id"));
            user.setUserName(rs.getString("user_name"));

            // name
            ScimName name = new ScimName();
            name.setGivenName(rs.getString("given_name"));
            name.setFamilyName(rs.getString("family_name"));
            user.setName(name);

            // emails
            String emailsJson = rs.getString("emails");
            if (emailsJson != null) {
                try {
                    List<ScimEmail> emails = objectMapper.readValue(emailsJson, new TypeReference<List<ScimEmail>>() {});
                    user.setEmails(emails);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing emails JSON", e);
                }
            }

            // phoneNumbers
            String phonesJson = rs.getString("phone_numbers");
            if (phonesJson != null) {
                try {
                    List<ScimPhoneNumber> phones = objectMapper.readValue(phonesJson, new TypeReference<List<ScimPhoneNumber>>() {});
                    user.setPhoneNumbers(phones);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing phoneNumbers JSON", e);
                }
            }

            // addresses
            String addressesJson = rs.getString("addresses");
            if (addressesJson != null) {
                try {
                    List<ScimAddress> addresses = objectMapper.readValue(addressesJson, new TypeReference<List<ScimAddress>>() {});
                    user.setAddresses(addresses);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing addresses JSON", e);
                }
            }

            // enterpriseExtension
            String enterpriseJson = rs.getString("enterprise_extension");
            if (enterpriseJson != null) {
                try {
                    ScimEnterpriseExtension ee = objectMapper.readValue(enterpriseJson, ScimEnterpriseExtension.class);
                    user.setEnterpriseExtension(ee);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing enterpriseExtension JSON", e);
                }
            }

            // active
            user.setActive(rs.getBoolean("active"));

            // meta
            ScimMeta meta = new ScimMeta();
            meta.setCreated(rs.getTimestamp("created") != null ? rs.getTimestamp("created").toInstant().toString() : null);
            meta.setLastModified(rs.getTimestamp("last_modified") != null ? rs.getTimestamp("last_modified").toInstant().toString() : null);
            meta.setResourceType("User");
            meta.setLocation("/scim/Users/" + user.getId());
            user.setMeta(meta);

            return user;
        }, userName);

        return list.isEmpty() ? null : list.get(0);
    }

    // ====================== 实现findAll() ======================
    @Override
    public List<ScimUser> findAll() {
        String sql = "SELECT * FROM scim_user ORDER BY created DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            // 完全复用findById/findByUserName的字段映射逻辑
            ScimUser user = new ScimUser();
            user.setId(rs.getString("id"));
            user.setExternalId(rs.getString("external_id"));
            user.setUserName(rs.getString("user_name"));

            // name
            ScimName name = new ScimName();
            name.setGivenName(rs.getString("given_name"));
            name.setFamilyName(rs.getString("family_name"));
            user.setName(name);

            // emails
            String emailsJson = rs.getString("emails");
            if (emailsJson != null) {
                try {
                    List<ScimEmail> emails = objectMapper.readValue(emailsJson, new TypeReference<List<ScimEmail>>() {});
                    user.setEmails(emails);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing emails JSON", e);
                }
            }

            // phoneNumbers
            String phonesJson = rs.getString("phone_numbers");
            if (phonesJson != null) {
                try {
                    List<ScimPhoneNumber> phones = objectMapper.readValue(phonesJson, new TypeReference<List<ScimPhoneNumber>>() {});
                    user.setPhoneNumbers(phones);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing phoneNumbers JSON", e);
                }
            }

            // addresses
            String addressesJson = rs.getString("addresses");
            if (addressesJson != null) {
                try {
                    List<ScimAddress> addresses = objectMapper.readValue(addressesJson, new TypeReference<List<ScimAddress>>() {});
                    user.setAddresses(addresses);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing addresses JSON", e);
                }
            }

            // enterpriseExtension
            String enterpriseJson = rs.getString("enterprise_extension");
            if (enterpriseJson != null) {
                try {
                    ScimEnterpriseExtension ee = objectMapper.readValue(enterpriseJson, ScimEnterpriseExtension.class);
                    user.setEnterpriseExtension(ee);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing enterpriseExtension JSON", e);
                }
            }

            // active
            user.setActive(rs.getBoolean("active"));

            // meta
            ScimMeta meta = new ScimMeta();
            meta.setCreated(rs.getTimestamp("created") != null ? rs.getTimestamp("created").toInstant().toString() : null);
            meta.setLastModified(rs.getTimestamp("last_modified") != null ? rs.getTimestamp("last_modified").toInstant().toString() : null);
            meta.setResourceType("User");
            meta.setLocation("/scim/Users/" + user.getId());
            user.setMeta(meta);

            return user;
        });
    }

    // ====================== 实现findByActive(boolean active) ======================
    @Override
    public List<ScimUser> findByActive(boolean active) {
        String sql = "SELECT * FROM scim_user WHERE active = ? ORDER BY created DESC";
        return jdbcTemplate.query(sql, new Object[]{active}, (rs, rowNum) -> {
            // 完全复用findById/findByUserName的字段映射逻辑
            ScimUser user = new ScimUser();
            user.setId(rs.getString("id"));
            user.setExternalId(rs.getString("external_id"));
            user.setUserName(rs.getString("user_name"));

            // name
            ScimName name = new ScimName();
            name.setGivenName(rs.getString("given_name"));
            name.setFamilyName(rs.getString("family_name"));
            user.setName(name);

            // emails
            String emailsJson = rs.getString("emails");
            if (emailsJson != null) {
                try {
                    List<ScimEmail> emails = objectMapper.readValue(emailsJson, new TypeReference<List<ScimEmail>>() {});
                    user.setEmails(emails);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing emails JSON", e);
                }
            }

            // phoneNumbers
            String phonesJson = rs.getString("phone_numbers");
            if (phonesJson != null) {
                try {
                    List<ScimPhoneNumber> phones = objectMapper.readValue(phonesJson, new TypeReference<List<ScimPhoneNumber>>() {});
                    user.setPhoneNumbers(phones);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing phoneNumbers JSON", e);
                }
            }

            // addresses
            String addressesJson = rs.getString("addresses");
            if (addressesJson != null) {
                try {
                    List<ScimAddress> addresses = objectMapper.readValue(addressesJson, new TypeReference<List<ScimAddress>>() {});
                    user.setAddresses(addresses);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing addresses JSON", e);
                }
            }

            // enterpriseExtension
            String enterpriseJson = rs.getString("enterprise_extension");
            if (enterpriseJson != null) {
                try {
                    ScimEnterpriseExtension ee = objectMapper.readValue(enterpriseJson, ScimEnterpriseExtension.class);
                    user.setEnterpriseExtension(ee);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing enterpriseExtension JSON", e);
                }
            }

            // active
            user.setActive(rs.getBoolean("active"));

            // meta
            ScimMeta meta = new ScimMeta();
            meta.setCreated(rs.getTimestamp("created") != null ? rs.getTimestamp("created").toInstant().toString() : null);
            meta.setLastModified(rs.getTimestamp("last_modified") != null ? rs.getTimestamp("last_modified").toInstant().toString() : null);
            meta.setResourceType("User");
            meta.setLocation("/scim/Users/" + user.getId());
            user.setMeta(meta);

            return user;
        });
    }
    // ====================== 新增：findByUserNameLike（模糊查询userName） ======================
    @Override
    public List<ScimUser> findByUserNameLike(String userName) {
        // 拼接模糊查询的通配符（%），支持包含匹配（如传入"test"则匹配"%test%"）
        String likePattern = "%" + userName + "%";
        String sql = "SELECT * FROM scim_user WHERE user_name LIKE ? ORDER BY created DESC";

        return jdbcTemplate.query(sql, new Object[]{likePattern}, (rs, rowNum) -> {
            // 完全复用现有字段映射逻辑，和findById/findByUserName一致
            ScimUser user = new ScimUser();
            user.setId(rs.getString("id"));
            user.setExternalId(rs.getString("external_id"));
            user.setUserName(rs.getString("user_name"));

            // name
            ScimName name = new ScimName();
            name.setGivenName(rs.getString("given_name"));
            name.setFamilyName(rs.getString("family_name"));
            user.setName(name);

            // emails
            String emailsJson = rs.getString("emails");
            if (emailsJson != null) {
                try {
                    List<ScimEmail> emails = objectMapper.readValue(emailsJson, new TypeReference<List<ScimEmail>>() {});
                    user.setEmails(emails);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing emails JSON", e);
                }
            }

            // phoneNumbers
            String phonesJson = rs.getString("phone_numbers");
            if (phonesJson != null) {
                try {
                    List<ScimPhoneNumber> phones = objectMapper.readValue(phonesJson, new TypeReference<List<ScimPhoneNumber>>() {});
                    user.setPhoneNumbers(phones);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing phoneNumbers JSON", e);
                }
            }

            // addresses
            String addressesJson = rs.getString("addresses");
            if (addressesJson != null) {
                try {
                    List<ScimAddress> addresses = objectMapper.readValue(addressesJson, new TypeReference<List<ScimAddress>>() {});
                    user.setAddresses(addresses);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing addresses JSON", e);
                }
            }

            // enterpriseExtension
            String enterpriseJson = rs.getString("enterprise_extension");
            if (enterpriseJson != null) {
                try {
                    ScimEnterpriseExtension ee = objectMapper.readValue(enterpriseJson, ScimEnterpriseExtension.class);
                    user.setEnterpriseExtension(ee);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing enterpriseExtension JSON", e);
                }
            }

            // active
            user.setActive(rs.getBoolean("active"));

            // meta
            ScimMeta meta = new ScimMeta();
            meta.setCreated(rs.getTimestamp("created") != null ? rs.getTimestamp("created").toInstant().toString() : null);
            meta.setLastModified(rs.getTimestamp("last_modified") != null ? rs.getTimestamp("last_modified").toInstant().toString() : null);
            meta.setResourceType("User");
            meta.setLocation("/scim/Users/" + user.getId());
            user.setMeta(meta);

            return user;
        });
    }

    // ====================== 新增：findByExternalId（精准查询externalId） ======================
    @Override
    public List<ScimUser> findByExternalId(String externalId) {
        String sql = "SELECT * FROM scim_user WHERE external_id = ? ORDER BY created DESC";

        return jdbcTemplate.query(sql, new Object[]{externalId}, (rs, rowNum) -> {
            // 完全复用现有字段映射逻辑，和findById/findByUserName一致
            ScimUser user = new ScimUser();
            user.setId(rs.getString("id"));
            user.setExternalId(rs.getString("external_id"));
            user.setUserName(rs.getString("user_name"));

            // name
            ScimName name = new ScimName();
            name.setGivenName(rs.getString("given_name"));
            name.setFamilyName(rs.getString("family_name"));
            user.setName(name);

            // emails
            String emailsJson = rs.getString("emails");
            if (emailsJson != null) {
                try {
                    List<ScimEmail> emails = objectMapper.readValue(emailsJson, new TypeReference<List<ScimEmail>>() {});
                    user.setEmails(emails);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing emails JSON", e);
                }
            }

            // phoneNumbers
            String phonesJson = rs.getString("phone_numbers");
            if (phonesJson != null) {
                try {
                    List<ScimPhoneNumber> phones = objectMapper.readValue(phonesJson, new TypeReference<List<ScimPhoneNumber>>() {});
                    user.setPhoneNumbers(phones);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing phoneNumbers JSON", e);
                }
            }

            // addresses
            String addressesJson = rs.getString("addresses");
            if (addressesJson != null) {
                try {
                    List<ScimAddress> addresses = objectMapper.readValue(addressesJson, new TypeReference<List<ScimAddress>>() {});
                    user.setAddresses(addresses);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing addresses JSON", e);
                }
            }

            // enterpriseExtension
            String enterpriseJson = rs.getString("enterprise_extension");
            if (enterpriseJson != null) {
                try {
                    ScimEnterpriseExtension ee = objectMapper.readValue(enterpriseJson, ScimEnterpriseExtension.class);
                    user.setEnterpriseExtension(ee);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing enterpriseExtension JSON", e);
                }
            }

            // active
            user.setActive(rs.getBoolean("active"));

            // meta
            ScimMeta meta = new ScimMeta();
            meta.setCreated(rs.getTimestamp("created") != null ? rs.getTimestamp("created").toInstant().toString() : null);
            meta.setLastModified(rs.getTimestamp("last_modified") != null ? rs.getTimestamp("last_modified").toInstant().toString() : null);
            meta.setResourceType("User");
            meta.setLocation("/scim/Users/" + user.getId());
            user.setMeta(meta);

            return user;
        });
    }
}