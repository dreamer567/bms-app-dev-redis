package jp.adsur.service;

import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntraGroupUserService {

    private final GraphServiceClient<?> graphClient;

    /**
     * 功能1：将Entra用户添加到**已存在**的用户组
     * @param userId 用户ID（或用户UPN/邮箱，如li.cailing@adsur.jp）
     * @param groupId 现有组的ID（或组显示名称）
     * @throws Exception 操作异常（用户/组不存在、权限不足等）
     */
    public void addUserToExistingGroup(String userId, String groupId) throws Exception {
        try {
            // 1. 解析用户（支持用户ID、UPN、邮箱）
            User user = graphClient.users(userId).buildRequest().get();
            // 2. 解析组（支持组ID、组显示名称）
            Group group = graphClient.groups(groupId).buildRequest().get();

            // 3. 将用户添加到组
            graphClient.groups(group.id)
                    .members()
                    .references()
                    .buildRequest()
                    .post(user);

            System.out.printf("用户[%s]已成功添加到现有组[%s]%n", user.userPrincipalName, group.displayName);
        } catch (Exception e) {
            throw new Exception("添加用户到现有组失败：" + e.getMessage(), e);
        }
    }

    /**
     * 功能2：创建**新的**Entra用户组，并将指定用户添加到该组
     * @param userId 用户ID（或用户UPN/邮箱）
     * @param newGroupName 新组名称
     * @param newGroupDescription 新组描述
     * @return 新建组的ID
     * @throws Exception 操作异常
     */
    public String createNewGroupAndAddUser(String userId, String newGroupName, String newGroupDescription) throws Exception {
        try {
            // 步骤1：创建新的安全组（可根据需求修改组类型）
            Group newGroup = new Group();
            newGroup.displayName = newGroupName;
            newGroup.description = newGroupDescription;
            newGroup.mailEnabled = false;  // 安全组默认关闭邮件功能
            newGroup.securityEnabled = true;  // 标记为安全组
            newGroup.mailNickname = newGroupName.replace(" ", "_");  // 邮件别名（无空格）

            // 执行创建
            Group createdGroup = graphClient.groups()
                    .buildRequest()
                    .post(newGroup);
            System.out.printf("新组[%s]创建成功，组ID：%s%n", newGroupName, createdGroup.id);

            // 步骤2：将用户添加到新建组
            User user = graphClient.users(userId).buildRequest().get();
            graphClient.groups(createdGroup.id)
                    .members()
                    .references()
                    .buildRequest()
                    .post(user);

            System.out.printf("用户[%s]已成功添加到新组[%s]%n", user.userPrincipalName, newGroupName);
            return createdGroup.id;
        } catch (Exception e) {
            throw new Exception("创建新组并添加用户失败：" + e.getMessage(), e);
        }
    }
}