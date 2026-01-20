package jp.adsur.service;

import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserDeltaCollectionPage;
import com.microsoft.graph.requests.UserDeltaCollectionRequestBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class EntraDeltaQueryService {

    @Resource
    private GraphServiceClient<?> graphClient;

    /**
     * Delta查询：获取Entra ID用户的增量变更（适配SDK 5.x，解决deltaLink获取问题）
     * @param deltaLink 上一次查询返回的deltaLink（首次查询传null/空）
     * @return 封装后的增量查询结果（用户列表 + 新的deltaLink）
     */
    public DeltaQueryResult queryUserDelta(String deltaLink) {
        // 存储所有增量用户数据
        List<User> allDeltaUsers = new ArrayList<>();
        // 最终要返回的新deltaLink
        String newDeltaLink = null;
        UserDeltaCollectionPage deltaPage = null;

        try {
            if (deltaLink == null || deltaLink.isEmpty()) {
                // 首次查询：初始化Delta查询
                deltaPage = graphClient.users()
                        .delta()
                        .buildRequest()
                        .select("id,displayName,userPrincipalName,mail")
                        .get();
            } else {
                // 后续查询：5.x版本正确的deltaLink续传方式
                URL deltaUrl = new URL(deltaLink);
                UserDeltaCollectionRequestBuilder deltaRequestBuilder =
                        new UserDeltaCollectionRequestBuilder(
                                deltaUrl.toString(),
                                graphClient,
                                null
                        );
                deltaPage = deltaRequestBuilder.buildRequest().get();
            }

            // 处理分页：遍历所有页面，收集增量数据，并获取最终的deltaLink
            while (deltaPage != null) {
                // 收集当前页的增量用户数据
                allDeltaUsers.addAll(deltaPage.getCurrentPage());
                // 获取当前页的deltaLink（优先取这个，分页场景下最后一页的deltaLink才有效）
                newDeltaLink = deltaPage.deltaLink();

                // 处理下一页：若有下一页，继续获取
                UserDeltaCollectionRequestBuilder nextPageBuilder = deltaPage.getNextPage();
                if (nextPageBuilder != null) {
                    deltaPage = nextPageBuilder.buildRequest().get();
                } else {
                    // 无下一页，终止循环
                    deltaPage = null;
                }
            }

            // 打印增量数据示例
            for (User user : allDeltaUsers) {
                log.info("增量用户：ID=%s, 名称=%s, 邮箱=%s%n",
                        user.id, user.displayName, user.userPrincipalName);
            }

            return new DeltaQueryResult(allDeltaUsers, newDeltaLink);

        } catch (MalformedURLException e) {
            throw new RuntimeException("DeltaLink格式错误：" + deltaLink, e);
        } catch (Exception e) {
            throw new RuntimeException("Delta查询失败：" + e.getMessage(), e);
        }
    }

    /**
     * 封装Delta查询结果（用户列表 + 新的deltaLink）
     * （若用Lombok，可加@Data注解简化getter/setter）
     */
    public static class DeltaQueryResult {
        private List<User> deltaUsers; // 所有增量用户数据
        private String newDeltaLink;   // 下次查询用的deltaLink

        public DeltaQueryResult(List<User> deltaUsers, String newDeltaLink) {
            this.deltaUsers = deltaUsers;
            this.newDeltaLink = newDeltaLink;
        }

        // Getter（必须，否则上层无法获取结果）
        public List<User> getDeltaUsers() {
            return deltaUsers;
        }

        public String getNewDeltaLink() {
            return newDeltaLink;
        }
    }
}