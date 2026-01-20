package jp.adsur.service;

import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group; // 新增：创建组所需的模型类
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EntraGroupUserService {

    private final GraphServiceClient<?> graphClient;

    /**
     * 新增核心方法：创建新组并添加指定用户到该组
     * @param userId 要添加的用户Object ID（必须是GUID，可通过getUserIdByEmail获取）
     * @param newGroupName 新组的显示名称
     * @param newGroupDescription 新组的描述
     * @return 新创建组的Object ID（GUID）
     */
    public String createNewGroupAndAddUser(String userId, String newGroupName, String newGroupDescription) {
        try {
            // 步骤1：构建新组的参数（必填项：displayName、mailNickname，其他可选）
            Group newGroup = new Group();
            newGroup.displayName = newGroupName; // 组显示名称
            newGroup.description = newGroupDescription; // 组描述
            // mailNickname：组的邮件别名（必填，不能有空格/特殊字符，建议用组名拼音/缩写）
            newGroup.mailNickname = newGroupName.replaceAll("\\s+", "").toLowerCase();
            // 组类型：安全组（默认），若要创建Microsoft 365组，需添加groupTypes: ["Unified"]
            newGroup.groupTypes = java.util.List.of(); // 空列表=安全组；如需365组：List.of("Unified")
            newGroup.securityEnabled = true; // 安全组启用（必填）
            newGroup.mailEnabled = false; // 非邮件启用（安全组默认false；365组需设为true）

            // 步骤2：调用Graph API创建新组，获取新组ID
            Group createdGroup = graphClient.groups()
                    .buildRequest()
                    .post(newGroup);
            String newGroupId = createdGroup.id;
            log.info("新组创建成功：名称=%s，ID=%s%n", newGroupName, newGroupId);

            // 步骤3：调用已有的addUserToGroup方法，把用户添加到新组
            addUserToGroup(newGroupId, userId);
            log.info("用户%s已添加到新创建的组%s%n", userId, newGroupId);

            // 返回新组ID
            return newGroupId;

        } catch (Exception e) {
            throw new RuntimeException("创建新组并添加用户失败：" + e.getMessage(), e);
        }
    }

    /**
     * 原有方法：添加用户到现有组（不使用ReferenceCreate）
     * @param groupId 目标组的ID（GUID）
     * @param userId 要添加的用户ID（GUID）
     */
    public void addUserToGroup(String groupId, String userId) {
        try {
            // 1. 验证用户ID是否有效（可选，增强鲁棒性）
            User user = graphClient.users(userId)
                    .buildRequest()
                    .select("id")
                    .get();
            String userObjectId = user.id;

            // 2. 核心：添加用户到组（无ReferenceCreate写法）
            DirectoryObject directoryObject = new DirectoryObject();
            directoryObject.id = userObjectId;

            graphClient.groups(groupId)
                    .members()
                    .buildRequest()
                    .post(directoryObject);

            log.info("用户%s已成功添加到组%s%n", userObjectId, groupId);

        } catch (Exception e) {
            throw new RuntimeException("添加用户到组失败：" + e.getMessage(), e);
        }
    }

    /**
     * 原有辅助方法：通过邮箱获取用户ID
     */
    public String getUserIdByEmail(String userEmail) {
        User user = graphClient.users()
                .buildRequest()
                .filter(String.format("userPrincipalName eq '%s'", userEmail))
                .get()
                .getCurrentPage()
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("用户不存在：" + userEmail));
        return user.id;
    }
}