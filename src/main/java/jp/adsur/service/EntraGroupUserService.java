//package jp.adsur.service;
//
//import com.azure.core.credential.TokenRequestContext;
//import com.azure.identity.ClientSecretCredential;
//import com.microsoft.graph.models.Group;
//import com.microsoft.graph.models.User;
//import com.microsoft.graph.requests.GraphServiceClient;
//import com.microsoft.graph.requests.GroupCollectionPage;
//import com.microsoft.graph.requests.UserCollectionPage;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import javax.annotation.Resource;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.TimeUnit;
//
//@Slf4j
//@Service
//public class EntraGroupUserService {
//
//    @Resource
//    private GraphServiceClient<?> graphClient;
//
//    @Autowired
//    private RestTemplate restTemplate;
//
//    @Autowired
//    private ClientSecretCredential clientSecretCredential;
//
//    // グループ作成後の同期待機時間（Azure ADの同期遅延対策：最低5秒推奨）
//    private static final int GROUP_SYNC_DELAY_SECONDS = 5;
//
//    /**
//     * ユーザーをグループに追加（SDKのバグ回避：直接/$refエンドポイントを呼び出し）
//     */
//    public void addUserToGroup(String groupId, String userEmail) {
//        try {
//            // 1. ユーザーを検索（ユーザー存在確認：404エラーの事前回避）
//            UserCollectionPage userPage = graphClient.users()
//                    .buildRequest()
//                    .filter(String.format("userPrincipalName eq '%s'", userEmail))
//                    .select("id,userPrincipalName")
//                    .get();
//
//            Optional<User> userOptional = userPage.getCurrentPage().stream().findFirst();
//            if (userOptional.isEmpty()) {
//                throw new RuntimeException("ユーザー" + userEmail + "が存在しません。正しいUPNを確認してください");
//            }
//
//            // 2. ユーザー情報の取得
//            User targetUser = userOptional.get();
//            String userObjectId = targetUser.id;
//            String realUPN = targetUser.userPrincipalName;
//            log.info("✅ ユーザーを検索しました：UPN={}, ObjectID={}", realUPN, userObjectId);
//
//            // 3. グループ存在確認（コア処理：無効なグループIDによる404を回避）
//            if (!isGroupExists(groupId)) {
//                throw new RuntimeException("グループID " + groupId + " が存在しません。ユーザー追加処理を中断します");
//            }
//
//            // 4. アクセストークンの取得（TokenRequestContextを使用：型互換性確保）
//            TokenRequestContext tokenRequestContext = new TokenRequestContext();
//            tokenRequestContext.setScopes(Collections.singletonList("https://graph.microsoft.com/.default"));
//            String accessToken = clientSecretCredential.getToken(tokenRequestContext)
//                    .block()
//                    .getToken();
//
//            // 5. /$refエンドポイントのURL構築（Graph APIの正規エンドポイント）
//            String refEndpointUrl = String.format(
//                    "https://graph.microsoft.com/v1.0/groups/%s/members/$ref",
//                    groupId
//            );
//
//            // 6. リクエストボディの構築（@odata.idは必須：directoryObjects/{userObjectId}形式）
//            Map<String, String> requestBody = new HashMap<>();
//            requestBody.put("@odata.id", String.format(
//                    "https://graph.microsoft.com/v1.0/directoryObjects/%s",
//                    userObjectId
//            ));
//
//            // 7. HTTPリクエストの送信（RestTemplateで直接呼び出し：SDKのバグ回避）
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            headers.setBearerAuth(accessToken);
//            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
//            ResponseEntity<Void> response = restTemplate.exchange(
//                    refEndpointUrl,
//                    HttpMethod.POST,
//                    requestEntity,
//                    Void.class
//            );
//
//            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
//                log.info("✅ ユーザー{}（UPN：{}）をグループ{}に追加しました", userObjectId, realUPN, groupId);
//            } else {
//                throw new RuntimeException("ユーザー追加失敗：Graph APIがステータスコード " + response.getStatusCode() + " を返却しました");
//            }
//
//        } catch (Exception e) {
//            log.error("ユーザーをグループに追加する際にエラーが発生しました：", e);
//            throw new RuntimeException("ユーザーをグループに追加失敗：" + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * 新しいグループを作成してユーザーを追加（同期遅延対策+存在確認付き）
//     */
//    public String createNewGroupAndAddUser(String userEmail, String newGroupName, String newGroupDescription) {
//        try {
//            // 1. セキュリティグループの作成（必須項目の設定：400エラー回避）
//            Group newGroup = new Group();
//            newGroup.displayName = newGroupName;
//            newGroup.description = newGroupDescription;
//            newGroup.mailNickname = newGroupName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
//            newGroup.groupTypes = Collections.emptyList();
//            newGroup.securityEnabled = true;
//            newGroup.mailEnabled = false;
//
//            // 2. グループの作成実行
//            Group createdGroup = graphClient.groups()
//                    .buildRequest()
//                    .post(newGroup);
//            String newGroupId = createdGroup.id;
//            log.info("✅ 新しいグループを作成しました：グループ名={}, ID={}", newGroupName, newGroupId);
//
//            // 3. コア処理：グループ同期待機（作成直後の404エラー対策）
//            log.info("⏳ グループ同期のため{}秒待機します...", GROUP_SYNC_DELAY_SECONDS);
//            TimeUnit.SECONDS.sleep(GROUP_SYNC_DELAY_SECONDS);
//
//            // 4. グループ同期の二次確認（遅延が大きい場合の追加待機）
//            if (!isGroupExists(newGroupId)) {
//                log.warn("⚠️ グループ{}の同期が遅延しています。さらに2秒待機します...", newGroupId);
//                TimeUnit.SECONDS.sleep(2);
//                // 同期が完了しない場合、例外をスロー
//                if (!isGroupExists(newGroupId)) {
//                    throw new RuntimeException("新しいグループの作成に成功しましたが、同期がタイムアウトしました。グループID " + newGroupId + " は一時的に使用できません");
//                }
//            }
//
//            // 5. 作成したグループにユーザーを追加
//            addUserToGroup(newGroupId, userEmail);
//            return newGroupId;
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException("グループ同期待機中に処理が中断されました：" + e.getMessage(), e);
//        } catch (Exception e) {
//            log.error("新しいグループの作成とユーザー追加に失敗しました：", e);
//            throw new RuntimeException("新しいグループの作成に失敗しました：" + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * グループの存在確認（プライベートメソッド：404エラーの事前検知）
//     */
//    private boolean isGroupExists(String groupId) {
//        try {
//            // Graph APIで指定IDのグループを検索
//            GroupCollectionPage groupPage = graphClient.groups()
//                    .buildRequest()
//                    .filter(String.format("id eq '%s'", groupId))
//                    .select("id")
//                    .get();
//
//            // グループが存在する場合はtrueを返却
//            return groupPage.getCurrentPage().stream().findFirst().isPresent();
//        } catch (Exception e) {
//            log.error("グループ{}の存在確認に失敗しました：", groupId, e);
//            return false;
//        }
//    }
//
//    /**
//     * ユーザーIDを取得する補助メソッド（User::getIdの代替：フィールドに直接アクセス）
//     */
//    public String getUserIdByEmail(String userEmail) {
//        UserCollectionPage userPage = graphClient.users()
//                .buildRequest()
//                .filter(String.format("userPrincipalName eq '%s'", userEmail))
//                .select("id")
//                .get();
//
//        return userPage.getCurrentPage().stream()
//                .findFirst()
//                .map(user -> user.id)
//                .orElseThrow(() -> new RuntimeException("ユーザー" + userEmail + "が存在しません"));
//    }
//}