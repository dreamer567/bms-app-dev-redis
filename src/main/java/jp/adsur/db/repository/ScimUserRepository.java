package jp.adsur.db.repository;

import jp.adsur.db.entity.ScimUser;

import java.util.List;

public interface ScimUserRepository {

    ScimUser findById(String id);

    ScimUser save(ScimUser user);

    void update(ScimUser user);

    void delete(String id);

    ScimUser findByUserName(String userName);

    List<ScimUser> findAll();

    List<ScimUser> findByActive(boolean active);

    // ====================== 新增：findByUserNameLike（模糊查询userName） ======================
    List<ScimUser> findByUserNameLike(String userName);

    // ====================== 新增：findByExternalId（精准查询externalId） ======================
    List<ScimUser> findByExternalId(String externalId);
}
