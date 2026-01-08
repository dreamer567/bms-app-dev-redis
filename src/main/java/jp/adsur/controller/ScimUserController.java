package jp.adsur.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jp.adsur.controller.pojo.ScimListResponse;
import jp.adsur.controller.pojo.ScimPatchOperation;
import jp.adsur.controller.pojo.ScimPatchRequest;
import jp.adsur.db.entity.*;
import jp.adsur.service.ScimUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jp.adsur.controller.pojo.PatchOperationType.add;
import static jp.adsur.controller.pojo.PatchOperationType.replace;

@Slf4j
@RestController
@RequestMapping("/scim/v2/Users")
@RequiredArgsConstructor
public class ScimUserController {

    private final ScimUserService service;
    private final ObjectMapper objectMapper;

    // CREATE
    @PostMapping
    public ResponseEntity<ScimUser> createUser(@RequestBody ScimUser requestUser, HttpServletRequest request) {
        // 步骤1：检查唯一属性（userName）是否已存在（避免重复创建）
        Optional<ScimUser> existingUser = service.findByUserName(requestUser.getUserName());
        if (existingUser.isPresent()) {
            // 重复创建：返回409 Conflict（符合SCIM验证要求）
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        // 步骤2：设置创建时间（数据库层已处理，这里补充SCIM的meta字段）
        LocalDateTime now = LocalDateTime.now();
        String iso8601Time = now.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);

        // 步骤3：保存用户（已修复ID自增逻辑，返回带ID的用户）
        ScimUser savedUser = service.createUser(requestUser);

        // 步骤4：构建SCIM规范的响应体（匹配你要求的JSON格式）
        ScimMeta meta = new ScimMeta();
        meta.setResourceType("User");
        meta.setCreated(iso8601Time);
        meta.setLastModified(iso8601Time);

        savedUser.setSchemas(List.of(new String[]{"urn:ietf:params:scim:schemas:core:2.0:User"}));
        savedUser.setMeta(meta);
        // 确保name字段的formatted拼接（匹配示例格式）
//        if (savedUser.getName() != null) {
//            savedUser.getName().setFormatted(
//                    savedUser.getName().getGivenName() + " " + savedUser.getName().getFamilyName()
//            );
//        }

        // 步骤5：返回201 Created + SCIM规范的响应体
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", request.getRequestURL() + "/" + savedUser.getId()) // SCIM规范要求返回Location头
                .body(savedUser);
    }

    // GET
    @GetMapping("/{id}")
    public ResponseEntity<ScimUser> get(@PathVariable String id) {
        ScimUser user = service.getUser(id);
        if (user == null) {
            log.warn("[GET] SCIM User not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("[GET] SCIM Get user: {}", id);
        return ResponseEntity.ok(user);
    }

    // ====================== 2. GET /Users?filter={属性} eq "值" 过滤查询 ======================
    // 补充GET方法中filter的解析逻辑，新增active属性过滤
    @GetMapping
    public ResponseEntity<ScimListResponse> getUsers(@RequestParam(required = false) String filter) {
        // 1. 初始化SCIM ListResponse对象
        ScimListResponse response = new ScimListResponse();

        List<ScimUser> users;
        if (filter == null || filter.isEmpty()) {
            users = service.findAll();
        } else {
            Pattern pattern = Pattern.compile("(\\w+)\\s+eq\\s+\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(filter);
            if (!matcher.matches()) {
                return ResponseEntity.badRequest().build();
            }

            String property = matcher.group(1);
            String value = matcher.group(2);

            // 新增：支持active属性过滤（核心验证点）
            users = switch (property) {
                case "userName" -> service.findByUserNameLike(value);
                case "externalId" -> service.findByExternalId(value);
                case "id" -> service.findById(value).map(List::of).orElse(List.of());
                // 核心：处理active过滤（value为"true"/"false"）
                case "active" -> service.findByActive(Boolean.parseBoolean(value));
                default -> List.of();
            };
        }

        // 补充meta字段（保留原有逻辑）
        users.forEach(user -> {
            if (user.getMeta() == null) {
                ScimMeta meta = new ScimMeta();
                meta.setResourceType("User");
                String time = LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
                meta.setCreated(time);
                meta.setLastModified(time);
                user.setMeta(meta);
            }
            user.setSchemas(List.of(new String[]{"urn:ietf:params:scim:schemas:core:2.0:User"}));
        });

        // 3. 填充ListResponse字段（核心：适配0条结果的场景）
        response.setTotalResults(users.size()); // 总条数（0则为0）// List<ScimUser> 可自动赋值给 List<Object>
        response.setResources(List.of(users.toArray()));
        // startIndex/itemsPerPage使用默认值（1/20），也可支持前端传入参数

        // 4. 返回200 OK + 规范的ListResponse
        return ResponseEntity.ok(response);
    }

    // ====================== 3. DELETE /Users/{id} 硬删除测试用户 ======================
    // 替换原有DELETE方法，严格遵循SCIM验证要求
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        // 步骤1：检查用户是否存在（可选，验证工具一般允许对不存在的用户返回204，也可按规范返回404）
        Optional<ScimUser> user = service.findById(id);
        if (user.isEmpty()) {
            // 方案1：严格规范 - 用户不存在返回404
            // return ResponseEntity.notFound().build();
            // 方案2：适配验证工具 - 无论是否存在都返回204（部分SCIM验证工具要求）
            return ResponseEntity.noContent().build();
        }

        // 步骤2：执行硬删除（仅当系统支持硬删除时）
        service.deleteById(id);

        // 步骤3：必须返回204 No Content，无任何响应体（核心验证点）
        // headers可以为空，仅返回状态码204
        return ResponseEntity
                .noContent()
                .header("Content-Length", "0") // 可选：显式指定无内容
                .build();
    }
    /**
     * 修复后的PATCH接口：更新原有记录 + 修正meta.location
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ScimUser> patchUser(
            @PathVariable String id, // 路径里的真实用户ID（关键！）
            @RequestBody ScimPatchRequest patchRequest) {

        log.info("[PATCH] Update SCIM user, ID: {}", id);

        // 1. 校验用户是否存在（不存在直接404，避免新增）
        ScimUser existingUser = service.getUser(id);
        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 2. 校验ID一致性（防止request里的ID和路径ID不一致）
        if (!id.equals(existingUser.getId())) {
            throw new RuntimeException("User ID in path does not match existing user ID");
        }

        // 3. 执行PATCH操作（更新原有字段，不新增）
        for (ScimPatchOperation operation : patchRequest.getOperations()) {
            String path = operation.getPath().trim().replaceFirst("^/", "");
            Object value = operation.getValue();

            switch (operation.getOp().toLowerCase()) {
                case "replace" -> handleReplaceOperation(existingUser, path, value);
                case "add" -> handleAddOperation(existingUser, path, value);
                default -> throw new RuntimeException("Unsupported PATCH op: " + operation.getOp());
            }
        }

        // 4. 关键修复1：重新计算meta.location（用路径ID拼接，确保和实际ID一致）
        ScimMeta meta = existingUser.getMeta() != null ? existingUser.getMeta() : new ScimMeta();
        meta.setLocation("/scim/Users/" + id); // 强制用路径ID拼接，而非旧ID
        meta.setLastModified(Instant.now().toString()); // 更新最后修改时间
        meta.setResourceType("User"); // 确保不丢失
        existingUser.setMeta(meta);

        // 5. 关键修复2：调用update方法（更新），而非save（插入）
        ScimUser updatedUser = service.patchUser(id, existingUser);

        // 6. 返回更新后的用户（ID不变，Location正确）
        return ResponseEntity.ok(updatedUser);
    }

    // 保留原有handleReplaceOperation/handleAddOperation（适配你的Entity）
    private void handleReplaceOperation(ScimUser user, String path, Object value) {
        switch (path) {
            case "userName" -> user.setUserName(value.toString());
            case "active" -> {
                boolean newActive = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(value.toString());
                user.setActive(newActive);
            }
            case "name/givenName" -> user.getName().setGivenName(value.toString());
            case "name/familyName" -> user.getName().setFamilyName(value == null ? null : value.toString());
            // 补充其他需要replace的字段（emails/phoneNumbers/enterpriseExtension等）
            case "emails" -> {
                List<ScimEmail> emails = objectMapper.convertValue(value, new TypeReference<List<ScimEmail>>() {});
                user.setEmails(emails);
            }
            case "phoneNumbers" -> {
                List<ScimPhoneNumber> phones = objectMapper.convertValue(value, new TypeReference<List<ScimPhoneNumber>>() {});
                user.setPhoneNumbers(phones);
            }
            case "enterpriseExtension/department" -> {
                if (user.getEnterpriseExtension() == null) {
                    user.setEnterpriseExtension(new ScimEnterpriseExtension());
                }
                user.getEnterpriseExtension().setDepartment(value == null ? null : value.toString());
            }
            default -> throw new RuntimeException("Unsupported replace path: " + path);
        }
    }

    // 处理「属性追加」操作（add）：仅追加非必需属性，已存在则忽略
    private void handleAddOperation(ScimUser user, String path, Object value) {
        try {
            switch (path) {
                // 适配你的ScimPhoneNumber实体类
                case "phoneNumbers" -> {
                    if (user.getPhoneNumbers() == null) {
                        List<ScimPhoneNumber> phoneNumbers = objectMapper.convertValue(
                                value, new TypeReference<List<ScimPhoneNumber>>() {});
                        user.setPhoneNumbers(phoneNumbers);
                    }
                }
                // 适配你的ScimAddress实体类
                case "addresses" -> {
                    if (user.getAddresses() == null) {
                        List<ScimAddress> addresses = objectMapper.convertValue(
                                value, new TypeReference<List<ScimAddress>>() {});
                        user.setAddresses(addresses);
                    }
                }
                // 适配你的ScimEnterpriseExtension实体类
                case "enterpriseExtension" -> {
                    if (user.getEnterpriseExtension() == null) {
                        ScimEnterpriseExtension enterpriseExtension = objectMapper.convertValue(
                                value, ScimEnterpriseExtension.class);
                        user.setEnterpriseExtension(enterpriseExtension);
                    }
                }
                // 可扩展其他add操作的字段（如emails）
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle SCIM PATCH add operation for path: " + path, e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScimUser> putUser(
            @PathVariable String id,
            @RequestBody ScimUser request
    ) {
        log.info("[PUT] SCIM Replace user: {}", id);

        // 1. 检查用户是否存在
        ScimUser existing = service.getUser(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null); // 404返回空体，更符合SCIM规范
        }

        // 2. 覆盖字段（保留SCIM核心字段，仅更新业务字段）
        // 强制设置ID（和路径一致）
        request.setId(id);

        // 保留原有Meta的created字段（PUT不修改创建时间），仅更新lastModified
        ScimMeta meta = existing.getMeta() != null ? existing.getMeta() : new ScimMeta();
        meta.setResourceType("User"); // SCIM强制要求，固定为User
        meta.setLastModified(Instant.now().toString()); // 更新最后修改时间
        // 保留创建时间（从现有用户获取，不覆盖）
        if (meta.getCreated() == null) {
            meta.setCreated(Instant.now().toString()); // 兼容无创建时间的情况
        }
        request.setMeta(meta);

        // 补充SCIM必需的schemas字段（确保响应包含）
        if (request.getSchemas() == null || request.getSchemas().isEmpty()) {
            request.setSchemas(List.of(new String[]{"urn:ietf:params:scim:schemas:core:2.0:User"}));
        }

//        // 补充name.formatted字段（SCIM规范要求）
//        if (request.getName() != null) {
//            ScimName name = request.getName();
//            if (name.getFormatted() == null && name.getGivenName() != null && name.getFamilyName() != null) {
//                name.setFormatted(name.getGivenName() + " " + name.getFamilyName());
//            }
//        }

        // 3. 保存到数据库（确保修改生效）
        ScimUser updatedUser = service.putUser(request); // 要求service返回更新后的完整对象

        // 4. 返回200 OK + 完整的SCIM User JSON（从数据库查询的对象，而非入参）
        return ResponseEntity.ok(updatedUser);
    }

    // ====================== 异常处理（可选，优化体验）======================
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
}
