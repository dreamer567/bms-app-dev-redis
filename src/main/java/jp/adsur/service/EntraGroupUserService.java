package jp.adsur.service;

import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Service
public class EntraGroupUserService {

    @Resource
    private GraphServiceClient<?> graphClient;

    /**
     * 最终定稿：完全匹配5.75.0 SDK源码
     * references().post() 仅接受 DirectoryObject 类型参数，解决所有编译/运行错误
     */
    public void addUserToGroup(String groupId, String userEmail) {
        try {
            // 1. 查询用户（获取真实Object ID，修复User::getId无效问题）
            UserCollectionPage userPage = graphClient.users()
                    .buildRequest()
                    .filter(String.format("userPrincipalName eq '%s'", userEmail))
                    .select("id,userPrincipalName")
                    .get();

            Optional<User> userOptional = userPage.getCurrentPage().stream().findFirst();
            if (userOptional.isEmpty()) {
                log.error("❌ 用户{}不存在！租户内前50个UPN：", userEmail);
                graphClient.users()
                        .buildRequest()
                        .select("userPrincipalName")
                        .top(50)
                        .get()
                        .getCurrentPage()
                        .forEach(u -> log.error("  - {}", u.userPrincipalName));
                throw new RuntimeException("用户" + userEmail + "不存在，请核对真实UPN");
            }

            // 2. 提取用户ID，构造DirectoryObject（SDK要求的唯一参数类型）
            User targetUser = userOptional.get();
            String userObjectId = targetUser.id; // 直接访问公有字段，无getId()方法
            String realUPN = targetUser.userPrincipalName;
            log.info("✅ 查询到用户：UPN={}, ObjectID={}", realUPN, userObjectId);

            // 3. 构造DirectoryObject（references().post()唯一合法参数）
            DirectoryObject userDirectoryObject = new DirectoryObject();
            userDirectoryObject.id = userObjectId; // 仅需设置ID，SDK自动处理@odata.id

            // 4. 核心正确调用（完全匹配5.75.0 SDK源码）
            // references() → 对应/$ref端点（解决400错误）
            // post(DirectoryObject) → 唯一参数，符合SDK方法签名
            graphClient.groups(groupId)
                    .members()
                    .references() // 必须加：对应/$ref端点，避免"Unsupported resource type"
                    .buildRequest()
                    .post(userDirectoryObject); // 仅传DirectoryObject，无其他参数

            log.info("✅ 用户{}（UPN：{}）已成功添加到组{}", userObjectId, realUPN, groupId);

        } catch (Exception e) {
            log.error("添加用户到组失败（完整堆栈）：", e); // 打印行号+详细错误
            throw new RuntimeException("添加用户到组失败：" + e.getMessage(), e);
        }
    }

    /**
     * 创建新组并添加用户（适配5.75.0 SDK）
     */
    public String createNewGroupAndAddUser(String userEmail, String newGroupName, String newGroupDescription) {
        try {
            // 1. 构建合法安全组（避免400错误）
            Group newGroup = new Group();
            newGroup.displayName = newGroupName;
            newGroup.description = newGroupDescription;
            // mailNickname：仅保留字母数字，SDK强制要求
            newGroup.mailNickname = newGroupName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            newGroup.groupTypes = Collections.emptyList(); // 安全组标识
            newGroup.securityEnabled = true;
            newGroup.mailEnabled = false;

            // 2. 创建组（5.75.0原生API）
            Group createdGroup = graphClient.groups()
                    .buildRequest()
                    .post(newGroup);
            String newGroupId = createdGroup.id;
            log.info("✅ 新组创建成功：名称={}, ID={}", newGroupName, newGroupId);

            // 3. 添加用户到新组（调用最终版方法）
            addUserToGroup(newGroupId, userEmail);
            return newGroupId;

        } catch (Exception e) {
            log.error("创建新组失败（完整堆栈）：", e);
            throw new RuntimeException("创建新组失败：" + e.getMessage(), e);
        }
    }

    /**
     * 辅助方法：获取用户ID（无编译错误）
     */
    public String getUserIdByEmail(String userEmail) {
        UserCollectionPage userPage = graphClient.users()
                .buildRequest()
                .filter(String.format("userPrincipalName eq '%s'", userEmail))
                .select("id")
                .get();

        return userPage.getCurrentPage().stream()
                .findFirst()
                .map(user -> user.id) // 直接访问id字段，替代无效的User::getId
                .orElseThrow(() -> new RuntimeException("用户" + userEmail + "不存在"));
    }
}