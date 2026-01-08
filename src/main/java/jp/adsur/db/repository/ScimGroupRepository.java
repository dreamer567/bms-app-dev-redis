package jp.adsur.db.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import jp.adsur.db.entity.ScimGroup;
import jp.adsur.db.entity.ScimGroupMember;
import jp.adsur.db.entity.ScimMeta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class ScimGroupRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ScimGroupRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // 创建Group（生成唯一ID，初始化空members）
    public ScimGroup save(ScimGroup group) {
        // 生成唯一ID（示例：UUID）
        String groupId = UUID.randomUUID().toString().replace("-", "");
        group.setId(groupId);

        // 初始化空members（确保创建时members为空列表）
        if (group.getMembers() == null) {
            group.setMembers(List.of());
        }

        // 插入数据库（示例SQL，需根据表结构调整）
        String sql = "INSERT INTO scim_group (id, external_id, display_name, members, meta_created, meta_last_modified) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql,
                    groupId,
                    group.getExternalId(),
                    group.getDisplayName(),
                    objectMapper.writeValueAsString(group.getMembers()),
                    group.getMeta().getCreated(),
                    group.getMeta().getLastModified());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return group;
    }

    // 按ID查询Group
    public ScimGroup findById(String id) {
        String sql = "SELECT * FROM scim_group WHERE id = ?";
        List<ScimGroup> groups = jdbcTemplate.query(sql, new ScimGroupRowMapper(), id);
        return groups.isEmpty() ? null : groups.get(0);
    }

    // 按displayName过滤查询
    public List<ScimGroup> findByDisplayName(String displayName) {
        String sql = "SELECT * FROM scim_group WHERE display_name = ?";
        return jdbcTemplate.query(sql, new ScimGroupRowMapper(), displayName);
    }

    // 修正后的existsByDisplayName（适配多租户+软删除）
    public boolean existsByDisplayName(String displayName) {
        String sql = "SELECT COUNT(*) FROM scim_group WHERE display_name = ? AND is_deleted = 0";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, displayName);
        return count != null && count > 0;
    }

    // 更新Group（支持displayName/members修改）
    public void update(ScimGroup group) {
        String sql = "UPDATE scim_group SET " +
                "external_id=?, display_name=?, members=?, meta_last_modified=? " +
                "WHERE id=?";
        try {
            jdbcTemplate.update(sql,
                    group.getExternalId(),
                    group.getDisplayName(),
                    objectMapper.writeValueAsString(group.getMembers()),
                    group.getMeta().getLastModified(),
                    group.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // 删除Group
    public void delete(String id) {
        String sql = "DELETE FROM scim_group WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    // Group行映射器
    private class ScimGroupRowMapper implements RowMapper<ScimGroup> {
        @Override
        public ScimGroup mapRow(ResultSet rs, int rowNum) throws SQLException {
            ScimGroup group = new ScimGroup();
            group.setId(rs.getString("id"));
            group.setExternalId(rs.getString("external_id"));
            group.setDisplayName(rs.getString("display_name"));

            // 反序列化members
            try {
                List<ScimGroupMember> members = objectMapper.readValue(
                        rs.getString("members"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ScimGroupMember.class));
                group.setMembers(members);
            } catch (Exception e) {
                group.setMembers(List.of());
            }

            // 构建meta
            ScimMeta meta = new ScimMeta();
            meta.setResourceType("Group");
            meta.setCreated(rs.getString("meta_created"));
            meta.setLastModified(rs.getString("meta_last_modified"));
            meta.setLocation("/scim/Groups/" + group.getId());
            group.setMeta(meta);
            return group;
        }
    }
}