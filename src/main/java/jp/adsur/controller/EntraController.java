package jp.adsur.controller;
import jp.adsur.service.EntraDeltaQueryService;
import jp.adsur.service.EntraGroupUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entra")
@RequiredArgsConstructor
public class EntraController {

    private final EntraGroupUserService entraService;
    private final EntraDeltaQueryService deltaQueryService;

    @GetMapping("/user-delta")
    public EntraDeltaQueryService.DeltaQueryResult getUserDelta(
            @RequestParam(required = false) String deltaLink) {
        return deltaQueryService.queryUserDelta(deltaLink);
    }
    /**
     * 接口1：添加用户到现有组
     * 示例请求：POST /api/entra/add-user-to-group?userId=li.cailing@adsur.jp&groupId=现有组ID
     */
    @PostMapping("/add-user-to-group")
    public ResponseEntity<String> addUserToExistingGroup(
            @RequestParam String userId,
            @RequestParam String groupId) {
        try {
            entraService.addUserToGroup(groupId, userId);
            return ResponseEntity.ok("用户添加到现有组成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("操作失败：" + e.getMessage());
        }
    }

    /**
     * 接口2：创建新组并添加用户
     * 示例请求：POST /api/entra/create-group-and-add-user?userId=li.cailing@adsur.jp&newGroupName=测试组&newGroupDescription=测试用组
     */
    @PostMapping("/create-group-and-add-user")
    public ResponseEntity<String> createNewGroupAndAddUser(
            @RequestParam String userId,
            @RequestParam String newGroupName,
            @RequestParam String newGroupDescription) {
        try {
            String groupId = entraService.createNewGroupAndAddUser(userId, newGroupName, newGroupDescription);
            return ResponseEntity.ok("新组创建并添加用户成功，组ID：" + groupId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("操作失败：" + e.getMessage());
        }
    }
}