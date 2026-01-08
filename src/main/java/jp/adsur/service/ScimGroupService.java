package jp.adsur.service;

import jp.adsur.controller.pojo.ScimListResponse;
import jp.adsur.controller.pojo.ScimPatchOp;
import jp.adsur.controller.pojo.ScimPatchOperation;
import jp.adsur.db.entity.*;
import jp.adsur.db.repository.ScimGroupRepository;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ScimGroupService {
    private final ScimGroupRepository groupRepository;
    private final ObjectMapper objectMapper;

    public ScimGroupService(ScimGroupRepository groupRepository, ObjectMapper objectMapper) {
        this.groupRepository = groupRepository;
        this.objectMapper = objectMapper;
    }

    // 创建Group（校验重复displayName）
    public ScimGroup createGroup(ScimGroup group) {
        // 重复displayName返回409（根据示例要求）
        if (groupRepository.existsByDisplayName(group.getDisplayName())) {
            throw new RuntimeException("Group with displayName " + group.getDisplayName() + " already exists");
        }
        return groupRepository.save(group);
    }

    // 按ID查询Group
    public ScimGroup getGroupById(String id) {
        return groupRepository.findById(id);
    }

    // 按displayName过滤查询
    public List<ScimGroup> getGroupsByDisplayName(String displayName) {
        return groupRepository.findByDisplayName(displayName);
    }

    // PATCH更新Group（处理replace/add/remove）
    public void patchGroup(String id, ScimPatchOp patchOp) {
        ScimGroup group = groupRepository.findById(id);
        if (group == null) {
            throw new RuntimeException("Group not found: " + id);
        }

        // 处理PATCH操作
        for (ScimPatchOperation operation : patchOp.getOperations()) {
            String op = operation.getOp().toLowerCase();
            String path = operation.getPath().trim().replaceFirst("^/", "");
            Object value = operation.getValue();

            switch (op) {
                case "replace" -> handleReplace(group, path, value);
                case "add" -> handleAdd(group, path, value);
                case "remove" -> handleRemove(group, path, value);
                default -> throw new RuntimeException("Unsupported PATCH op: " + op);
            }
        }

        // 更新lastModified时间
        group.getMeta().setLastModified(Instant.now().toString());
        groupRepository.update(group);
    }

    // 处理replace操作（如displayName）
    private void handleReplace(ScimGroup group, String path, Object value) {
        switch (path) {
            case "displayName" -> group.setDisplayName(value.toString());
            default -> throw new RuntimeException("Unsupported replace path: " + path);
        }
    }

    // 处理add操作（如members）
    private void handleAdd(ScimGroup group, String path, Object value) {
        if ("members".equals(path)) {
            List<ScimGroupMember> newMembers = objectMapper.convertValue(
                    value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ScimGroupMember.class));
            group.getMembers().addAll(newMembers);
        } else {
            throw new RuntimeException("Unsupported add path: " + path);
        }
    }

    // 处理remove操作（如members）
    private void handleRemove(ScimGroup group, String path, Object value) {
        if ("members".equals(path)) {
            List<ScimGroupMember> removeMembers = objectMapper.convertValue(
                    value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ScimGroupMember.class));
            // 移除匹配value的成员
            group.getMembers().removeIf(member ->
                    removeMembers.stream().anyMatch(rm -> rm.getValue().equals(member.getValue())));
        } else {
            throw new RuntimeException("Unsupported remove path: " + path);
        }
    }

    // 删除Group
    public void deleteGroup(String id) {
        groupRepository.delete(id);
    }

    // 获取Schema列表（固定返回User/Group/EnterpriseUser）
    public ScimListResponse getSchemas() {
        ScimListResponse response = new ScimListResponse();
        response.setTotalResults(3);
        response.setItemsPerPage(50);

        // 1. User Schema
        ScimSchema userSchema = buildUserSchema();
        // 2. Group Schema
        ScimSchema groupSchema = buildGroupSchema();
        // 3. EnterpriseUser Schema
        ScimSchema enterpriseUserSchema = buildEnterpriseUserSchema();

        response.setResources(List.of(userSchema, groupSchema, enterpriseUserSchema));
        return response;
    }

    // 构建User Schema
    private ScimSchema buildUserSchema() {
        ScimSchema schema = new ScimSchema();
        schema.setId("urn:ietf:params:scim:schemas:core:2.0:User");
        schema.setName("User");
        schema.setDescription("User Account");

        ScimSchemaAttribute userNameAttr = new ScimSchemaAttribute();
        userNameAttr.setName("userName");
        userNameAttr.setType("string");
        userNameAttr.setMultiValued(false);
        userNameAttr.setDescription("Unique identifier for the User, typically used by the user to directly authenticate to the service provider. Each User MUST include a non-empty userName value. This identifier MUST be unique across the service provider's entire set of Users. REQUIRED.");
        userNameAttr.setRequired(true);
        userNameAttr.setCaseExact(false);
        userNameAttr.setMutability("readWrite");
        userNameAttr.setReturned("default");
        userNameAttr.setUniqueness("server");
        schema.setAttributes(List.of(userNameAttr));

        ScimMeta meta = new ScimMeta();
        meta.setResourceType("Schema");
        meta.setLocation("/v2/Schemas/urn:ietf:params:scim:schemas:core:2.0:User");
        schema.setMeta(meta);
        return schema;
    }

    // 构建Group Schema
    private ScimSchema buildGroupSchema() {
        ScimSchema schema = new ScimSchema();
        schema.setId("urn:ietf:params:scim:schemas:core:2.0:Group");
        schema.setName("Group");
        schema.setDescription("Group");

        ScimSchemaAttribute displayNameAttr = new ScimSchemaAttribute();
        displayNameAttr.setName("displayName");
        displayNameAttr.setType("string");
        displayNameAttr.setMultiValued(false);
        displayNameAttr.setDescription("A human-readable name for the Group. REQUIRED.");
        displayNameAttr.setRequired(false);
        displayNameAttr.setCaseExact(false);
        displayNameAttr.setMutability("readWrite");
        displayNameAttr.setReturned("default");
        displayNameAttr.setUniqueness("none");
        schema.setAttributes(List.of(displayNameAttr));

        ScimMeta meta = new ScimMeta();
        meta.setResourceType("Schema");
        meta.setLocation("/v2/Schemas/urn:ietf:params:scim:schemas:core:2.0:Group");
        schema.setMeta(meta);
        return schema;
    }

    // 构建EnterpriseUser Schema
    private ScimSchema buildEnterpriseUserSchema() {
        ScimSchema schema = new ScimSchema();
        schema.setId("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        schema.setName("EnterpriseUser");
        schema.setDescription("Enterprise User");

        ScimSchemaAttribute employeeNumberAttr = new ScimSchemaAttribute();
        employeeNumberAttr.setName("employeeNumber");
        employeeNumberAttr.setType("string");
        employeeNumberAttr.setMultiValued(false);
        employeeNumberAttr.setDescription("Numeric or alphanumeric identifier assigned to a person, typically based on order of hire or association with an organization.");
        employeeNumberAttr.setRequired(false);
        employeeNumberAttr.setCaseExact(false);
        employeeNumberAttr.setMutability("readWrite");
        employeeNumberAttr.setReturned("default");
        employeeNumberAttr.setUniqueness("none");
        schema.setAttributes(List.of(employeeNumberAttr));

        ScimMeta meta = new ScimMeta();
        meta.setResourceType("Schema");
        meta.setLocation("/v2/Schemas/urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        schema.setMeta(meta);
        return schema;
    }
}