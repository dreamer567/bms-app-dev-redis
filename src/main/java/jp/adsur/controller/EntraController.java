//package jp.adsur.controller;
//
//import jp.adsur.service.EntraDeltaQueryService;
//import jp.adsur.service.EntraGroupUserService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/entra")
//@RequiredArgsConstructor
//public class EntraController {
//
//    private final EntraGroupUserService entraService;
//    private final EntraDeltaQueryService deltaQueryService;
//
//    /**
//     * ユーザーデルタクエリを実行
//     * @param deltaLink デルタリンク（任意パラメータ）
//     * @return デルタクエリ結果
//     */
//    @GetMapping("/user-delta")
//    public EntraDeltaQueryService.DeltaQueryResult getUserDelta(
//            @RequestParam(required = false) String deltaLink) {
//        return deltaQueryService.queryUserDelta(deltaLink);
//    }
//
//    /**
//     * API1：既存のグループにユーザーを追加
//     * リクエスト例：POST /api/entra/add-user?groupId=既存のグループID&userEmail=li.cailing@adsur.jp
//     */
//    @PostMapping("/add-user")
//    public String addUser(String groupId, String userEmail) {
//        try {
//            // メールアドレスを直接渡す：内部で$filterでObject IDを自動検索
//            entraService.addUserToGroup(groupId, userEmail);
//            return "✅ ユーザーの追加に成功しました";
//        } catch (Exception e) {
//            return "❌ ユーザーの追加に失敗しました：" + e.getMessage();
//        }
//    }
//
//    /**
//     * API2：新規グループを作成してユーザーを追加
//     * リクエスト例：POST /api/entra/create-group?userEmail={UPN}&groupName=テストグループ&groupDesc=テスト用グループ
//     */
//    @PostMapping("/create-group")
//    public String createGroup(
//            @RequestParam String userEmail,
//            @RequestParam String groupName,
//            @RequestParam String groupDesc
//    ) {
//        try {
//            String groupId = entraService.createNewGroupAndAddUser(userEmail, groupName, groupDesc);
//            return "✅ 操作に成功しました：新規グループID=" + groupId;
//        } catch (Exception e) {
//            return "❌ 操作に失敗しました：" + e.getMessage();
//        }
//    }
//}