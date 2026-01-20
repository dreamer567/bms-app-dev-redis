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
//    @PostMapping("/add-user-to-group")
//    public ResponseEntity<String> addUserToExistingGroup(
//            @RequestParam String userId,
//            @RequestParam String groupId) {
//        try {
//            entraService.addUserToGroup(groupId, userId);
//            return ResponseEntity.ok("用户添加到现有组成功");
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body("操作失败：" + e.getMessage());
//        }
//    }

    // 正确调用示例（控制器中）
    @PostMapping("/add-user")
    public String addUser(String groupId, String userEmail) {
        try {
            // 直接传入邮箱，内部会自动通过$filter查Object ID
            entraService.addUserToGroup(groupId, userEmail);
            return "添加成功";
        } catch (Exception e) {
            return "添加失败：" + e.getMessage();
        }
    }

    /**
     * 接口2：创建新组并添加用户
     * 示例请求：POST /api/entra/create-group-and-add-user?userId=li.cailing@adsur.jp&newGroupName=测试组&newGroupDescription=测试用组
     */
    // 创建新组并添加用户
    @PostMapping("/create-group")
    public String createGroup(
            @RequestParam String userEmail,
            @RequestParam String groupName,
            @RequestParam String groupDesc
    ) {
        try {
            String groupId = entraService.createNewGroupAndAddUser(userEmail, groupName, groupDesc);
            return "✅ 操作成功：新组ID=" + groupId;
        } catch (Exception e) {
            return "❌ 操作失败：" + e.getMessage();
        }
    }
}