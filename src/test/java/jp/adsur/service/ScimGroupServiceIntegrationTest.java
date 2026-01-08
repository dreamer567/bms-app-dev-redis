package jp.adsur.service;

import jp.adsur.db.entity.ScimGroup;
import jp.adsur.db.entity.ScimMeta;
import jp.adsur.db.repository.ScimGroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ScimGroupService 集成测试（依赖真实H2数据库，定位createGroup 409问题）
 * 核心：验证重复displayName导致409的逻辑，排查数据库/判重SQL的问题
 */
@SpringBootTest // 加载完整Spring上下文，使用真实Repository和数据库
//@ActiveProfiles("test") // 加载test环境配置（H2数据库）
//@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS) // 测试前建表
public class ScimGroupServiceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ScimGroupServiceIntegrationTest.class);

    // 注入真实的Service（依赖真实Repository和数据库）
    @Autowired
    private ScimGroupService scimGroupService;

    // 注入Repository，辅助验证数据库数据
    @Autowired
    private ScimGroupRepository scimGroupRepository;

    // 测试基础数据（每个测试方法用唯一displayName，避免干扰）
    private String testDisplayName;
    private ScimGroup testGroup;
    private ScimMeta testMeta;

    /**
     * 每个测试方法执行前初始化唯一的displayName（避免跨方法干扰）
     */
    @BeforeEach
    void setUp() {
        // 生成唯一的displayName（前缀+随机串，避免测试数据残留）
        testDisplayName = "test-409-" + UUID.randomUUID().toString().substring(0, 8);

        // 初始化测试Meta
        testMeta = new ScimMeta();
        testMeta.setResourceType("Group");
        testMeta.setCreated(Instant.now().toString());
        testMeta.setLastModified(Instant.now().toString());

        // 初始化测试Group
        testGroup = new ScimGroup();
        testGroup.setExternalId(UUID.randomUUID().toString());
        testGroup.setDisplayName(testDisplayName);
        testGroup.setMembers(List.of()); // 空成员列表
        testGroup.setMeta(testMeta);
    }

    /**
     * 每个测试方法执行后清理数据（避免影响其他测试）
     */
    @AfterEach
    void tearDown() {
        // 删除当前测试的Group（按displayName）
        List<ScimGroup> groups = scimGroupRepository.findByDisplayName(testDisplayName);
        for (ScimGroup group : groups) {
            scimGroupRepository.delete(group.getId());
            log.info("清理测试数据：删除Group ID={}, displayName={}", group.getId(), testDisplayName);
        }
    }

    // ====================== 核心测试场景（聚焦409问题） ======================

    /**
     * 场景1：首次创建Group → 成功（无409）
     * 验证：数据库插入成功，existsByDisplayName返回true
     */
    @Test
    void testCreateGroup_Success_No409() {
        // 1. 执行创建
        log.info("测试首次创建Group，displayName={}", testDisplayName);
        ScimGroup createdGroup = scimGroupService.createGroup(testGroup);

        // 2. 断言：创建成功，返回非空Group
        Assertions.assertNotNull(createdGroup);
        Assertions.assertNotNull(createdGroup.getId(), "创建后应生成ID");
        Assertions.assertEquals(testDisplayName, createdGroup.getDisplayName());

        // 3. 验证数据库中存在该记录
        boolean exists = scimGroupRepository.existsByDisplayName(testDisplayName);
        Assertions.assertTrue(exists, "创建后existsByDisplayName应返回true");

        // 4. 验证数据库查询结果
        ScimGroup dbGroup = scimGroupRepository.findById(createdGroup.getId());
        Assertions.assertNotNull(dbGroup);
        Assertions.assertEquals(testDisplayName, dbGroup.getDisplayName());
    }

    /**
     * 场景2：重复创建相同displayName的Group → 抛409异常
     * 验证：第二次创建触发existsByDisplayName，抛出指定异常
     */
    @Test
    void testCreateGroup_Duplicate_DisplayName_409() {
        // 1. 第一次创建：成功
        log.info("第一次创建Group，displayName={}", testDisplayName);
        ScimGroup firstGroup = scimGroupService.createGroup(testGroup);
        Assertions.assertNotNull(firstGroup);

        // 2. 第二次创建相同displayName的Group：预期抛409异常
        log.info("第二次创建相同displayName的Group，预期抛409异常，displayName={}", testDisplayName);
        // 构造新的Group对象（仅displayName相同）
        ScimGroup duplicateGroup = new ScimGroup();
        duplicateGroup.setExternalId(UUID.randomUUID().toString());
        duplicateGroup.setDisplayName(testDisplayName); // 相同displayName
        duplicateGroup.setMembers(List.of());
        duplicateGroup.setMeta(testMeta);

        // 3. 执行创建，断言抛409异常
        RuntimeException exception = Assertions.assertThrows(RuntimeException.class, () -> {
            scimGroupService.createGroup(duplicateGroup);
        });

        // 4. 验证异常信息（和Service中定义的一致）
        String expectedMsg = "Group with displayName " + testDisplayName + " already exists";
        Assertions.assertEquals(expectedMsg, exception.getMessage(), "409异常信息不匹配");

        // 5. 验证数据库中仅存在一条记录（未重复插入）
        List<ScimGroup> groups = scimGroupRepository.findByDisplayName(testDisplayName);
        Assertions.assertEquals(1, groups.size(), "重复创建后数据库应仅保留一条记录");
    }

    /**
     * 场景3：删除Group后，重新创建相同displayName → 成功（无409）
     * 验证：删除后existsByDisplayName返回false，可正常创建
     */
    @Test
    void testCreateGroup_AfterDelete_Success_No409() {
        // 1. 先创建再删除
        log.info("创建并删除Group，displayName={}", testDisplayName);
        ScimGroup createdGroup = scimGroupService.createGroup(testGroup);
        scimGroupRepository.delete(createdGroup.getId());

        // 2. 验证删除后existsByDisplayName返回false
        boolean existsAfterDelete = scimGroupRepository.existsByDisplayName(testDisplayName);
        Assertions.assertFalse(existsAfterDelete, "删除后existsByDisplayName应返回false");

        // 3. 重新创建相同displayName的Group：成功
        log.info("删除后重新创建相同displayName的Group，displayName={}", testDisplayName);
        ScimGroup reCreatedGroup = scimGroupService.createGroup(testGroup);
        Assertions.assertNotNull(reCreatedGroup);
        Assertions.assertEquals(testDisplayName, reCreatedGroup.getDisplayName());
    }

    /**
     * 场景4：边界测试 - displayName为空字符串 → 验证是否触发409（若数据库有空名记录）
     */
    @Test
    void testCreateGroup_EmptyDisplayName() {
        // 1. 构造displayName为空的Group
        String emptyDisplayName = "";
        ScimGroup emptyNameGroup = new ScimGroup();
        emptyNameGroup.setExternalId(UUID.randomUUID().toString());
        emptyNameGroup.setDisplayName(emptyDisplayName);
        emptyNameGroup.setMembers(List.of());
        emptyNameGroup.setMeta(testMeta);

        // 2. 首次创建空名Group：成功
        ScimGroup created = scimGroupService.createGroup(emptyNameGroup);
        Assertions.assertNotNull(created);

        // 3. 重复创建空名Group：抛409异常
        ScimGroup duplicateEmptyGroup = new ScimGroup();
        duplicateEmptyGroup.setExternalId(UUID.randomUUID().toString());
        duplicateEmptyGroup.setDisplayName(emptyDisplayName);
        duplicateEmptyGroup.setMembers(List.of());
        duplicateEmptyGroup.setMeta(testMeta);

        RuntimeException exception = Assertions.assertThrows(RuntimeException.class, () -> {
            scimGroupService.createGroup(duplicateEmptyGroup);
        });
        Assertions.assertEquals("Group with displayName  already exists", exception.getMessage());

        // 4. 清理数据
        scimGroupRepository.delete(created.getId());
    }
}