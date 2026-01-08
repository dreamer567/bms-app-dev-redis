package jp.adsur.service;

import jp.adsur.db.entity.ScimUser;
import jp.adsur.db.repository.ScimUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScimUserService {

    private final ScimUserRepository repository;

    public ScimUser createUser(ScimUser user) {
        return repository.save(user);
    }

    public ScimUser getUser(String id) {
        return repository.findById(id);
    }

    public boolean delete(String id) {
        ScimUser user = repository.findById(id);
        if (user == null) return false;
        repository.delete(id);
        return true;
    }

    public ScimUser patchUser(String id, ScimUser patch) {
        ScimUser user = repository.findById(id);
        if (user == null) return null;

        // 简单示例：只更新非 null 字段
        if (patch.getUserName() != null) user.setUserName(patch.getUserName());
        if (patch.getName() != null) user.setName(patch.getName());
        if (patch.getEmails() != null) user.setEmails(patch.getEmails());
        if (patch.getPhoneNumbers() != null) user.setPhoneNumbers(patch.getPhoneNumbers());
        if (patch.getAddresses() != null) user.setAddresses(patch.getAddresses());
        if (patch.getEnterpriseExtension() != null) user.setEnterpriseExtension(patch.getEnterpriseExtension());
        user.setActive(patch.isActive());

        repository.update(user);
        return user;
    }

    public ScimUser putUser(ScimUser request) {
        // 1. 执行更新逻辑（UPDATE SQL）
        repository.update(request);
        // 2. 更新后查询完整对象（确保返回的字段完整）
        return repository.findById(request.getId());
    }

    public Optional<ScimUser> findByUserName(String userName) {
        // 假设 repository 有相应的方法
        ScimUser user = repository.findByUserName(userName);
        return Optional.ofNullable(user);
    }

    // 按userName模糊查询（支持filter）
    public List<ScimUser> findByUserNameLike(String userName) {
        return repository.findByUserNameLike(userName);
    }

    // 按externalId查询
    public List<ScimUser> findByExternalId(String externalId) {
        return repository.findByExternalId(externalId);
    }
    /**
     * 检查userName是否被其他用户占用（用于联合属性更新）
     * @param userName 新的userName
     * @param excludeId 排除当前用户ID（自己改自己的userName不触发重复）
     * @return 是否重复
     */
    public boolean isUserNameDuplicated(String userName, String excludeId) {
        Optional<ScimUser> user = findByUserName(userName);
        return user.isPresent() && !user.get().getId().equals(excludeId);
    }
    // 按id查询
    public Optional<ScimUser> findById(String id) {
        return Optional.ofNullable(repository.findById(id));
    }

    // 查询所有用户
    public List<ScimUser> findAll() {
        return repository.findAll();
    }

    // 硬删除用户
    public void deleteById(String id) {
        repository.delete(id);
    }

    // 保存用户（已修复ID自增逻辑）
    public ScimUser save(ScimUser user) {
        return repository.save(user);
    }

    public List<ScimUser> findByActive(boolean active) {
        return repository.findByActive(active);
    }
}
