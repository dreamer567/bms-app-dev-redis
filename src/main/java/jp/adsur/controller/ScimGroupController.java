package jp.adsur.controller;

import jp.adsur.controller.pojo.ScimListResponse;
import jp.adsur.controller.pojo.ScimPatchOp;
import jp.adsur.db.entity.ScimGroup;
import jp.adsur.service.ScimGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

// 路径必须一字不差匹配请求：/scim/v2/Groups
@RestController
// 低版本Spring建议用value属性，避免produces等额外配置干扰
@RequestMapping(value = "/scim/v2")
public class ScimGroupController {

    private final ScimGroupService groupService;

    // 低版本Spring推荐构造器注入（避免@Autowired找不到）
    public ScimGroupController(ScimGroupService groupService) {
        this.groupService = groupService;
    }

    // POST /scim/v2/Groups （创建Group）
    @PostMapping(value = "/Groups") // 严格匹配/Groups，大写开头（SCIM规范）
    public ResponseEntity<ScimGroup> createGroup(@RequestBody ScimGroup group) {
        try {
            ScimGroup createdGroup = groupService.createGroup(group);
            // Location路径也必须匹配/scim/v2/Groups/{id}
            URI location = URI.create("/scim/v2/Groups/" + createdGroup.getId());
            return ResponseEntity.created(location).body(createdGroup);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // GET /scim/v2/Groups/{id} （按ID查询）
    @GetMapping(value = "/Groups/{id}")
    public ResponseEntity<ScimGroup> getGroupById(
            @PathVariable String id,
            @RequestParam(required = false) String excludedAttributes) {
        ScimGroup group = groupService.getGroupById(id);
        if (group == null) {
            return ResponseEntity.notFound().build();
        }
        if ("members".equals(excludedAttributes)) {
            group.setMembers(null);
        }
        return ResponseEntity.ok(group);
    }

    // PATCH /scim/v2/Groups/{id} （更新Group）
    @PatchMapping(value = "/Groups/{id}")
    public ResponseEntity<Void> patchGroup(
            @PathVariable String id,
            @RequestBody ScimPatchOp patchOp) {
        try {
            groupService.patchGroup(id, patchOp);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // DELETE /scim/v2/Groups/{id} （删除Group）
    @DeleteMapping(value = "/Groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        ScimGroup group = groupService.getGroupById(id);
        if (group == null) {
            return ResponseEntity.notFound().build();
        }
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    // GET /scim/v2/Groups （按filter查询）
    @GetMapping(value = "/Groups")
    public ResponseEntity<ScimListResponse> getGroups(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String excludedAttributes) {
        ScimListResponse response = new ScimListResponse();
        List<ScimGroup> groups = List.of();
        if (filter != null && filter.startsWith("displayName eq ")) {
            String displayName = filter.replace("displayName eq \"", "").replace("\"", "");
            groups = groupService.getGroupsByDisplayName(displayName);
        }
        if ("members".equals(excludedAttributes)) {
            groups.forEach(g -> g.setMembers(null));
        }
        response.setTotalResults(groups.size());
        response.setResources(List.of(groups.toArray()));
        return ResponseEntity.ok(response);
    }

    // GET /scim/v2/Schemas （Schema检测）
    @GetMapping(value = "/Schemas")
    public ResponseEntity<ScimListResponse> getSchemas() {
        ScimListResponse schemas = groupService.getSchemas();
        return ResponseEntity.ok(schemas);
    }
}