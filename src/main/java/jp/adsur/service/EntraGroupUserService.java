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
     * 最终版：完全匹配5.75.0源码的post(DirectoryObject)调用方式
     * 核心：调用你贴的DirectoryObjectCollectionReferenceRequest.post(DirectoryObject)
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

            // 2. 提取用户ID，构造DirectoryObject（匹配你源码的参数类型）
            User targetUser = userOptional.get();
            String userObjectId = targetUser.id;
            String realUPN = targetUser.userPrincipalName;
            log.info("✅ 查询到用户：UPN={}, ObjectID={}", realUPN, userObjectId);

            // 3. 构造DirectoryObject对象（你源码要求的参数类型）
            DirectoryObject userDirectoryObject = new DirectoryObject();
            userDirectoryObject.id = userObjectId; // 核心：设置用户Object ID

            // 4. 核心调用：匹配你贴的源码——post(DirectoryObject)
            // graphClient.groups(groupId).members()返回DirectoryObjectCollectionReferenceRequest
            // 直接调用post(DirectoryObject)，完全对齐源码
            graphClient.groups(groupId)
                    .members() // 返回DirectoryObjectCollectionReferenceRequest
                    .buildRequest()
                    .post(userDirectoryObject); // 传入DirectoryObject，匹配你源码的参数类型

            log.info("✅ 用户{}（UPN：{}）已成功添加到组{}", userObjectId, realUPN, groupId);

        } catch (Exception e) {
            log.error("添加用户到组失败（核心错误）：", e); // 打印完整堆栈
            throw new RuntimeException("添加用户到组失败：" + e.getMessage(), e);
        }
    }

    /**
     * 创建新组并添加用户（5.75.0原生API）
     */
    public String createNewGroupAndAddUser(String userEmail, String newGroupName, String newGroupDescription) {
        try {
            // 1. 构建新组（安全组，必填项完整）
            Group newGroup = new Group();
            newGroup.displayName = newGroupName;
            newGroup.description = newGroupDescription;
            // mailNickname：仅保留字母数字，避免非法字符
            newGroup.mailNickname = newGroupName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            newGroup.groupTypes = Collections.emptyList(); // 安全组标识
            newGroup.securityEnabled = true;
            newGroup.mailEnabled = false;

            // 2. 创建组（5.75.0原生POST）
            Group createdGroup = graphClient.groups()
                    .buildRequest()
                    .post(newGroup);
            String newGroupId = createdGroup.id;
            log.info("✅ 新组创建成功：名称={}, ID={}", newGroupName, newGroupId);

            // 3. 添加用户到新组（调用匹配源码的addUserToGroup）
            addUserToGroup(newGroupId, userEmail);

            return newGroupId;
        } catch (Exception e) {
            log.error("创建新组失败：", e);
            throw new RuntimeException("创建新组失败：" + e.getMessage(), e);
        }
    }

    /**
     * 辅助方法：获取用户ID（修复User::getId无效问题）
     */
    public String getUserIdByEmail(String userEmail) {
        UserCollectionPage userPage = graphClient.users()
                .buildRequest()
                .filter(String.format("userPrincipalName eq '%s'", userEmail))
                .select("id")
                .get();

        // 用lambda访问id字段，替代无效的User::getId方法引用
        return userPage.getCurrentPage().stream()
                .findFirst()
                .map(user -> user.id) // 直接访问公有字段id，无getId()方法
                .orElseThrow(() -> new RuntimeException("用户" + userEmail + "不存在"));
    }
}