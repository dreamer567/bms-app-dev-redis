package jp.adsur.service;

import com.azure.identity.ClientSecretCredential;
import com.azure.core.credential.TokenRequestContext; // 关键：导入TokenRequestContext
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class EntraGroupUserService {

    @Resource
    private GraphServiceClient<?> graphClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ClientSecretCredential clientSecretCredential;

    /**
     * 终极方案：修正Token获取参数类型 + 手动调用/$ref端点，解决所有错误
     */
    public void addUserToGroup(String groupId, String userEmail) {
        try {
            // 1. 查询用户（获取真实Object ID）
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

            // 2. 提取用户核心信息
            User targetUser = userOptional.get();
            String userObjectId = targetUser.id;
            String realUPN = targetUser.userPrincipalName;
            log.info("✅ 查询到用户：UPN={}, ObjectID={}", realUPN, userObjectId);

            // 3. 核心修正：创建TokenRequestContext（替代String[]，解决类型不兼容）
            TokenRequestContext tokenRequestContext = new TokenRequestContext();
            // 设置Graph API默认作用域，和你日志中一致
            tokenRequestContext.setScopes(Collections.singletonList("https://graph.microsoft.com/.default"));

            // 4. 获取有效Token（参数改为TokenRequestContext，类型兼容）
            String accessToken = clientSecretCredential.getToken(tokenRequestContext)
                    .block() // 同步获取Token（非异步）
                    .getToken(); // 提取Token字符串

            // 5. 构造/$ref端点URL（Graph API原生正确端点）
            String refEndpointUrl = String.format(
                    "https://graph.microsoft.com/v1.0/groups/%s/members/$ref",
                    groupId
            );

            // 6. 构造请求体（Graph API强制要求的@odata.id格式）
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("@odata.id", String.format(
                    "https://graph.microsoft.com/v1.0/directoryObjects/%s",
                    userObjectId
            ));

            // 7. 构造请求头（Bearer Token + JSON格式）
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            // 8. 发送POST请求（绕开SDK bug，直接调用原生端点）
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Void> response = restTemplate.exchange(
                    refEndpointUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Void.class
            );

            // 9. 验证响应（204 No Content = 成功，Graph API标准）
            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                log.info("✅ 用户{}（UPN：{}）已成功添加到组{}", userObjectId, realUPN, groupId);
            } else {
                throw new RuntimeException("添加失败：Graph API返回状态码 " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("添加用户到组失败（完整堆栈）：", e);
            throw new RuntimeException("添加用户到组失败：" + e.getMessage(), e);
        }
    }

    /**
     * 创建新组并添加用户（适配修正后的Token逻辑）
     */
    public String createNewGroupAndAddUser(String userEmail, String newGroupName, String newGroupDescription) {
        try {
            // 1. 构建合法安全组
            Group newGroup = new Group();
            newGroup.displayName = newGroupName;
            newGroup.description = newGroupDescription;
            newGroup.mailNickname = newGroupName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            newGroup.groupTypes = Collections.emptyList();
            newGroup.securityEnabled = true;
            newGroup.mailEnabled = false;

            // 2. 创建组（SDK无bug，正常使用）
            Group createdGroup = graphClient.groups()
                    .buildRequest()
                    .post(newGroup);
            String newGroupId = createdGroup.id;
            log.info("✅ 新组创建成功：名称={}, ID={}", newGroupName, newGroupId);

            // 3. 添加用户到新组
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
                .map(user -> user.id)
                .orElseThrow(() -> new RuntimeException("用户" + userEmail + "不存在"));
    }
}