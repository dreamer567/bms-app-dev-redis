//package jp.adsur.service;
//
//import com.microsoft.graph.models.User;
//import com.microsoft.graph.requests.GraphServiceClient;
//import com.microsoft.graph.requests.UserDeltaCollectionPage;
//import com.microsoft.graph.requests.UserDeltaCollectionRequestBuilder;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.Resource;
//import java.net.MalformedURLException;
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//@Slf4j
//public class EntraDeltaQueryService {
//
//    @Resource
//    private GraphServiceClient<?> graphClient;
//
//    /**
//     * デルタクエリ：Entra IDユーザーの増分変更を取得（SDK 5.x対応：デルタリンクの取得問題を解決）
//     * @param deltaLink 前回のクエリで返却されたデルタリンク（初回クエリはnull/空文字を渡す）
//     * @return カプセル化された増分クエリ結果（ユーザーリスト + 新しいデルタリンク）
//     */
//    public DeltaQueryResult queryUserDelta(String deltaLink) {
//        // 全ての増分ユーザーデータを格納
//        List<User> allDeltaUsers = new ArrayList<>();
//        // 最終的に返却する新しいデルタリンク
//        String newDeltaLink = null;
//        UserDeltaCollectionPage deltaPage = null;
//
//        try {
//            if (deltaLink == null || deltaLink.isEmpty()) {
//                // 初回クエリ：デルタクエリを初期化
//                deltaPage = graphClient.users()
//                        .delta()
//                        .buildRequest()
//                        .select("id,displayName,userPrincipalName,mail")
//                        .get();
//            } else {
//                // 後続クエリ：5.xバージョンの正しいデルタリンク継続方式
//                URL deltaUrl = new URL(deltaLink);
//                UserDeltaCollectionRequestBuilder deltaRequestBuilder =
//                        new UserDeltaCollectionRequestBuilder(
//                                deltaUrl.toString(),
//                                graphClient,
//                                null
//                        );
//                deltaPage = deltaRequestBuilder.buildRequest().get();
//            }
//
//            // ページング処理：全ページを走査して増分データを収集し、最終的なデルタリンクを取得
//            while (deltaPage != null) {
//                // 現在のページの増分ユーザーデータを収集
//                allDeltaUsers.addAll(deltaPage.getCurrentPage());
//                // 現在のページのデルタリンクを取得（優先：ページングシナリオで最終ページのデルタリンクのみ有効）
//                newDeltaLink = deltaPage.deltaLink();
//
//                // 次のページを処理：次のページが存在する場合は続けて取得
//                UserDeltaCollectionRequestBuilder nextPageBuilder = deltaPage.getNextPage();
//                if (nextPageBuilder != null) {
//                    deltaPage = nextPageBuilder.buildRequest().get();
//                } else {
//                    // 次のページがない場合、ループを終了
//                    deltaPage = null;
//                }
//            }
//
//            // 増分データのサンプル出力
//            for (User user : allDeltaUsers) {
//                log.info("増分ユーザー：ID=%s, 名前=%s, メール=%s%n",
//                        user.id, user.displayName, user.userPrincipalName);
//            }
//
//            return new DeltaQueryResult(allDeltaUsers, newDeltaLink);
//
//        } catch (MalformedURLException e) {
//            throw new RuntimeException("デルタリンクの形式が不正です：" + deltaLink, e);
//        } catch (Exception e) {
//            throw new RuntimeException("デルタクエリの実行に失敗しました：" + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * デルタクエリの結果をカプセル化（ユーザーリスト + 新しいデルタリンク）
//     * （Lombokを使用する場合は@Dataアノテーションでgetter/setterを簡略化可）
//     */
//    public static class DeltaQueryResult {
//        private List<User> deltaUsers; // 全ての増分ユーザーデータ
//        private String newDeltaLink;   // 次回クエリに使用するデルタリンク
//
//        public DeltaQueryResult(List<User> deltaUsers, String newDeltaLink) {
//            this.deltaUsers = deltaUsers;
//            this.newDeltaLink = newDeltaLink;
//        }
//
//        // Getter（必須：上位層で結果を取得するため）
//        public List<User> getDeltaUsers() {
//            return deltaUsers;
//        }
//
//        public String getNewDeltaLink() {
//            return newDeltaLink;
//        }
//    }
//}