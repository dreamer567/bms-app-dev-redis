package jp.adsur.service;

import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.extern.slf4j.Slf4j; // 导入Slf4j注解
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Optional;

@Slf4j // 添加Slf4j注解，自动生成log对象
@Service
public class EntraGroupUserService {

    @Resource
    private GraphServiceClient<?> graphClient;

    /**
     * 最终修复版：添加用户到组（彻底杜绝404问题）
     * @param groupId 组的Object ID（GUID，必须）
     * @param userEmail 用户的邮箱/UPN（会先通过$filter查询，不再直接传/users/{id}）
     */
    public void addUserToGroup(String groupId, String userEmail) {
        try {
            // ========== 核心修复：用$filter查询用户，而非直接调用/users/{邮箱} ==========
            UserCollectionPage userPage = graphClient.users()
                    .buildRequest()
                    // 用UPN精准过滤，这是Graph API识别邮箱的唯一可靠方式
                    .filter(String.format("userPrincipalName eq '%s'", userEmail))
                    .select("id,userPrincipalName,displayName") // 只查需要的字段
                    .get();

            // 检查用户是否存在
            Optional<User> userOptional = userPage.getCurrentPage().stream().findFirst();
            if (userOptional.isEmpty()) {
                // 打印租户内所有用户的UPN，方便你核对真实的UPN
                String errorMsg = String.format("❌ 用户不存在：%s，租户内已存在的用户UPN列表：", userEmail);
                log.error(errorMsg); // 替换System.err为log.error

                // 列出前50个用户的UPN，快速定位真实值
                StringBuilder upnList = new StringBuilder();
                graphClient.users()
                        .buildRequest()
                        .select("userPrincipalName")
                        .top(50)
                        .get()
                        .getCurrentPage()
                        .forEach(u -> upnList.append("\n- ").append(u.userPrincipalName));
                log.error("租户内已配置的用户UPN：{}", upnList); // 用占位符优化日志

                throw new RuntimeException(errorMsg);
            }

            // 获取用户真实的Object ID
            User targetUser = userOptional.get();
            String userObjectId = targetUser.id;
            String realUPN = targetUser.userPrincipalName;
            log.info("✅ 查询到用户：传入邮箱={}，真实UPN={}，Object ID={}",
                    userEmail, realUPN, userObjectId); // 替换System.out为log.info，用占位符

            // ========== 添加用户到组（用Object ID，而非邮箱） ==========
            DirectoryObject directoryObject = new DirectoryObject();
            directoryObject.id = userObjectId;

            graphClient.groups(groupId)
                    .members()
                    .buildRequest()
                    .post(directoryObject);

            log.info("✅ 用户{}（{}）已成功添加到组{}", realUPN, userObjectId, groupId); // 替换System.out为log.info

        } catch (Exception e) {
            log.error("添加用户到组失败：{}", e.getMessage(), e); // 打印异常堆栈，便于排查
            throw new RuntimeException("添加用户到组失败：" + e.getMessage(), e);
        }
    }

    /**
     * 创建新组并添加用户（同步修复：传入邮箱，内部自动查Object ID）
     */
    public String createNewGroupAndAddUser(String userEmail, String newGroupName, String newGroupDescription) {
        try {
            // 第一步：先通过邮箱查用户Object ID（核心修复）
            String userId = getUserIdByEmail(userEmail);

            // 第二步：创建新组
            Group newGroup = new Group();
            newGroup.displayName = newGroupName;
            newGroup.description = newGroupDescription;
            newGroup.mailNickname = newGroupName.replaceAll("\\s+", "").toLowerCase();
            newGroup.groupTypes = java.util.List.of();
            newGroup.securityEnabled = true;
            newGroup.mailEnabled = false;

            Group createdGroup = graphClient.groups()
                    .buildRequest()
                    .post(newGroup);
            String newGroupId = createdGroup.id;
            log.info("✅ 新组创建成功：{}（ID：{}）", newGroupName, newGroupId); // 替换System.out为log.info

            // 第三步：添加用户到新组（用修复后的addUserToGroup）
            addUserToGroup(newGroupId, userEmail);

            return newGroupId;
        } catch (Exception e) {
            log.error("创建新组并添加用户失败：{}", e.getMessage(), e); // 打印异常堆栈
            throw new RuntimeException("创建新组并添加用户失败：" + e.getMessage(), e);
        }
    }

    /**
     * 辅助方法：通过邮箱/UPN获取用户Object ID（强制$filter，杜绝404）
     */
    public String getUserIdByEmail(String userEmail) {
        UserCollectionPage userPage = graphClient.users()
                .buildRequest()
                .filter(String.format("userPrincipalName eq '%s'", userEmail))
                .select("id")
                .get();

        Optional<User> userOptional = userPage.getCurrentPage().stream().findFirst();
        if (userOptional.isEmpty()) {
            // 关键：打印租户内所有用户UPN，方便你核对真实值
            StringBuilder upnList = new StringBuilder();
            graphClient.users()
                    .buildRequest()
                    .select("userPrincipalName")
                    .top(50)
                    .get()
                    .getCurrentPage()
                    .forEach(u -> upnList.append("\n- ").append(u.userPrincipalName));

            String errorMsg = String.format("用户%s不存在！租户内已配置的用户UPN：%s", userEmail, upnList);
            log.error(errorMsg); // 替换System.err为log.error
            throw new RuntimeException(errorMsg);
        }
        return userOptional.get().id;
    }
}