package jp.adsur.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.adsur.controller.pojo.ScimListResponse;
import jp.adsur.db.entity.ScimGroup;
import jp.adsur.db.entity.ScimMeta;
import jp.adsur.service.ScimGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ScimGroupController 单元测试（仅测试Controller层，Mock Service层）
 * 核心：不依赖数据库/外部服务，专注验证Controller的请求映射、参数接收、响应码/响应体是否符合预期
 */
@WebMvcTest(ScimGroupController.class) // 仅加载Controller相关Bean，轻量测试
@AutoConfigureMockMvc(addFilters = false) // 核心：禁用所有过滤器（包括Security）
public class ScimGroupControllerTest {

    // 注入MockMvc（模拟HTTP请求）
    @Autowired
    private MockMvc mockMvc;

    // Mock Service层（避免依赖真实业务逻辑/数据库）
    @MockBean
    private ScimGroupService scimGroupService;

    // JSON序列化/反序列化工具
    @Autowired
    private ObjectMapper objectMapper;

    // 测试用例基础数据
    private ScimGroup testGroup;
    private String testGroupId;
    private String testDisplayName;

    /**
     * 每个测试方法执行前初始化测试数据
     */
    @BeforeEach
    void setUp() {
        // 初始化测试Group
        testGroupId = UUID.randomUUID().toString().replace("-", "");
        testDisplayName = "regular1";

        testGroup = new ScimGroup();
        testGroup.setId(testGroupId);
        testGroup.setExternalId("8aa1a0c0-c4c3-4bc0-b4a5-2ef676900159");
        testGroup.setDisplayName(testDisplayName);
        testGroup.setSchemas(List.of(
                "urn:ietf:params:scim:schemas:core:2.0:Group",
                "http://schemas.microsoft.com/2006/11/ResourceManagement/ADSCIM/2.0/Group"
        ));

        ScimMeta meta = new ScimMeta();
        meta.setResourceType("Group");
        testGroup.setMeta(meta);
        testGroup.setMembers(List.of());
    }

    // ====================== 核心测试场景 ======================

    /**
     * 测试：创建Group成功（POST /scim/v2/Groups）
     * 预期：响应码201 Created + Location头 + 响应体包含创建的Group信息
     */
    @Test
    void testCreateGroup_Success() throws Exception {
        // 1. Mock Service行为：调用createGroup返回测试Group
        when(scimGroupService.createGroup(any(ScimGroup.class))).thenReturn(testGroup);

        // 2. 模拟POST请求
        mockMvc.perform(post("/scim/v2/Groups")
                        .contentType(MediaType.APPLICATION_JSON) // 请求体JSON格式
                        .content(objectMapper.writeValueAsString(testGroup))) // 请求体
                // 3. 验证响应
                .andExpect(status().isCreated()) // 响应码201
                .andExpect(header().exists("Location")) // 包含Location头
                .andExpect(header().string("Location", "/scim/v2/Groups/" + testGroupId)) // Location值正确
                .andExpect(jsonPath("$.id").value(testGroupId)) // 响应体id正确
                .andExpect(jsonPath("$.displayName").value(testDisplayName)) // displayName正确
                .andExpect(jsonPath("$.schemas[0]").value("urn:ietf:params:scim:schemas:core:2.0:Group")); // schemas正确

        // 4. 验证Service方法被调用1次
        verify(scimGroupService, times(1)).createGroup(any(ScimGroup.class));
    }

    /**
     * 测试：创建Group重复（POST /scim/v2/Groups）
     * 预期：响应码409 Conflict
     */
    @Test
    void testCreateGroup_Duplicate() throws Exception {
        // 1. Mock Service行为：调用createGroup抛出重复异常
        when(scimGroupService.createGroup(any(ScimGroup.class)))
                .thenThrow(new RuntimeException("Group with displayName " + testDisplayName + " already exists"));

        // 2. 模拟POST请求
        mockMvc.perform(post("/scim/v2/Groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testGroup)))
                // 3. 验证响应码409
                .andExpect(status().isConflict());

        // 4. 验证Service方法被调用1次
        verify(scimGroupService, times(1)).createGroup(any(ScimGroup.class));
    }

    /**
     * 测试：按ID查询Group成功（GET /scim/v2/Groups/{id}）
     * 预期：响应码200 OK + 响应体包含Group信息
     */
    @Test
    void testGetGroupById_Success() throws Exception {
        // 1. Mock Service行为：调用getGroupById返回测试Group
        when(scimGroupService.getGroupById(eq(testGroupId))).thenReturn(testGroup);

        // 2. 模拟GET请求（不带excludedAttributes参数）
        mockMvc.perform(get("/scim/v2/Groups/{id}", testGroupId)
                        .accept(MediaType.APPLICATION_JSON))
                // 3. 验证响应
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testGroupId))
                .andExpect(jsonPath("$.displayName").value(testDisplayName))
                .andExpect(jsonPath("$.members").isArray()); // members字段存在（未排除）

        // 4. 验证Service方法被调用1次
        verify(scimGroupService, times(1)).getGroupById(eq(testGroupId));
    }

    /**
     * 测试：按ID查询Group成功（排除members字段）
     * 预期：响应体中members字段为null
     */
    @Test
    void testGetGroupById_ExcludeMembers() throws Exception {
        // 1. Mock Service行为：返回测试Group
        when(scimGroupService.getGroupById(eq(testGroupId))).thenReturn(testGroup);

        // 2. 模拟GET请求（带excludedAttributes=members参数）
        mockMvc.perform(get("/scim/v2/Groups/{id}", testGroupId)
                        .param("excludedAttributes", "members")
                        .accept(MediaType.APPLICATION_JSON))
                // 3. 验证响应：members字段为null
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members").doesNotExist()); // members被排除

        verify(scimGroupService, times(1)).getGroupById(eq(testGroupId));
    }

    /**
     * 测试：按ID查询Group不存在（GET /scim/v2/Groups/{id}）
     * 预期：响应码404 Not Found
     */
    @Test
    void testGetGroupById_NotFound() throws Exception {
        // 1. Mock Service行为：返回null（表示不存在）
        when(scimGroupService.getGroupById(eq(testGroupId))).thenReturn(null);

        // 2. 模拟GET请求
        mockMvc.perform(get("/scim/v2/Groups/{id}", testGroupId))
                // 3. 验证响应码404
                .andExpect(status().isNotFound());

        verify(scimGroupService, times(1)).getGroupById(eq(testGroupId));
    }

    /**
     * 测试：按filter查询Group（GET /scim/v2/Groups）
     * 预期：响应码200 OK + 响应体包含ScimListResponse
     */
    /**
     * 测试：按filter查询Group（GET /scim/v2/Groups）
     * 预期：响应码200 OK + 响应体包含ScimListResponse
     */
    @Test
    void testGetGroups_ByFilter() throws Exception {
        // 1. 构建测试ListResponse
        ScimListResponse response = new ScimListResponse();
        response.setTotalResults(1);
        response.setResources(List.of(testGroup)); // 注意：实体类中是setResources（大写），但序列化后是小写

        // 2. Mock Service行为：返回ListResponse
        when(scimGroupService.getGroupsByDisplayName(eq(testDisplayName))).thenReturn(List.of(testGroup));

        // 3. 模拟GET请求（带filter参数）
        mockMvc.perform(get("/scim/v2/Groups")
                        .param("filter", "displayName eq \"" + testDisplayName + "\"")
                        .accept(MediaType.APPLICATION_JSON))
                // 4. 验证响应：修正JSON Path为小写的resources
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(1)) // 总数1
                .andExpect(jsonPath("$.resources[0].displayName").value(testDisplayName)); // 关键：Resources → resources

        verify(scimGroupService, times(1)).getGroupsByDisplayName(eq(testDisplayName));
    }

    /**
     * 测试：PATCH更新Group成功（PATCH /scim/v2/Groups/{id}）
     * 预期：响应码204 No Content
     */
    @Test
    void testPatchGroup_Success() throws Exception {
        // 1. Mock Service行为：无异常（表示更新成功）
        doNothing().when(scimGroupService).patchGroup(eq(testGroupId), any());

        // 2. 模拟PATCH请求（请求体为ScimPatchOp，这里用空JSON示例）
        mockMvc.perform(patch("/scim/v2/Groups/{id}", testGroupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // 3. 验证响应码204
                .andExpect(status().isNoContent());

        verify(scimGroupService, times(1)).patchGroup(eq(testGroupId), any());
    }

    /**
     * 测试：PATCH更新Group不存在（PATCH /scim/v2/Groups/{id}）
     * 预期：响应码404 Not Found
     */
    @Test
    void testPatchGroup_NotFound() throws Exception {
        // 1. Mock Service行为：抛出异常（表示不存在）
        doThrow(new RuntimeException("Group not found")).when(scimGroupService).patchGroup(eq(testGroupId), any());

        // 2. 模拟PATCH请求
        mockMvc.perform(patch("/scim/v2/Groups/{id}", testGroupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // 3. 验证响应码404
                .andExpect(status().isNotFound());

        verify(scimGroupService, times(1)).patchGroup(eq(testGroupId), any());
    }

    /**
     * 测试：删除Group成功（DELETE /scim/v2/Groups/{id}）
     * 预期：响应码204 No Content
     */
    @Test
    void testDeleteGroup_Success() throws Exception {
        // 1. Mock Service行为：无异常（删除成功）
        when(scimGroupService.getGroupById(anyString())).thenReturn(testGroup);
        doNothing().when(scimGroupService).deleteGroup(eq(testGroupId));

        // 2. 模拟DELETE请求
        mockMvc.perform(delete("/scim/v2/Groups/{id}", testGroupId))
                // 3. 验证响应码204
                .andExpect(status().isNoContent());

        verify(scimGroupService, times(1)).deleteGroup(eq(testGroupId));
    }

    /**
     * 测试：删除Group不存在（DELETE /scim/v2/Groups/{id}）
     * 预期：响应码404 Not Found（若Service抛异常）/ 204（若Service无处理）
     * 注：可根据实际业务逻辑调整预期响应码
     */
    @Test
    void testDeleteGroup_NotFound() throws Exception {
        // 1. Mock Service行为：抛出异常
        doThrow(new RuntimeException("Group not found")).when(scimGroupService).deleteGroup(eq(testGroupId));

        // 2. 模拟DELETE请求
        mockMvc.perform(delete("/scim/v2/Groups/{id}", testGroupId))
                // 3. 验证响应码404
                .andExpect(status().isNotFound());

//        verify(scimGroupService, times(1)).deleteGroup(eq(testGroupId));
    }
}