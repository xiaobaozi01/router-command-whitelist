package com.example.whitelist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:approvalworkflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.username=approval-admin",
        "app.admin.password=admin-password",
        "app.admin.display-name=审批管理员",
        "app.ai.enabled=true",
        "app.ai.protocol=mock"
})
@AutoConfigureMockMvc
class CommandApprovalWorkflowTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void developerChangesOnlyTakeEffectAfterAdminApproval() throws Exception {
        MockHttpSession admin = login("approval-admin", "admin-password");
        createDeveloper(admin);
        MockHttpSession developer = login("approval-dev", "password1");
        long sceneId = createNamed(admin, "/api/scenes", "审批场景", null);
        long viewId = createNamed(admin, "/api/views", "审批视图", "\"displayOrder\":1,");

        String createPayload = commandPayload("display version", "初始描述", sceneId, viewId, null, null);
        mockMvc.perform(post("/api/commands")
                        .session(developer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isForbidden());

        long createRequestId = responseId(mockMvc.perform(post("/api/command-approvals/commands")
                        .session(developer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestType").value("CREATE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn());

        mockMvc.perform(get("/api/commands").session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/command-approvals").session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/command-approvals/{id}/ai-analysis", createRequestId)
                        .session(developer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/command-approvals/{id}/ai-analysis", createRequestId)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.recommendation").value("REVIEW"))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.checklist").isArray());

        MvcResult approvedCreate = mockMvc.perform(post("/api/command-approvals/{id}/approve", createRequestId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"新增内容已核对\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andReturn();
        long commandId = data(approvedCreate).path("generatedCommandId").asLong();

        MvcResult createdCommand = mockMvc.perform(get("/api/commands/{id}", commandId).session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdBy").value("approval-dev"))
                .andExpect(jsonPath("$.data.expressionText").value("display version"))
                .andReturn();
        long createVersion = data(createdCommand).path("version").asLong();

        String updatePayload = commandPayload(
                "display current-configuration", "已修改", sceneId, viewId, createVersion, "调整查询命令");
        long updateRequestId = responseId(mockMvc.perform(put("/api/command-approvals/commands/{id}", commandId)
                        .session(developer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestType").value("UPDATE"))
                .andReturn());
        mockMvc.perform(get("/api/commands/{id}", commandId).session(developer))
                .andExpect(jsonPath("$.data.expressionText").value("display version"));
        mockMvc.perform(post("/api/command-approvals/{id}/approve", updateRequestId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"\"}"))
                .andExpect(status().isOk());

        MvcResult updatedCommand = mockMvc.perform(get("/api/commands/{id}", commandId).session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expressionText").value("display current-configuration"))
                .andExpect(jsonPath("$.data.updatedBy").value("approval-dev"))
                .andReturn();
        long updatedVersion = data(updatedCommand).path("version").asLong();

        long rejectedDeleteId = responseId(mockMvc.perform(delete("/api/command-approvals/commands/{id}", commandId)
                        .session(developer)
                        .param("version", String.valueOf(updatedVersion))
                        .param("reason", "认为已不需要"))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/command-approvals/{id}/reject", rejectedDeleteId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"命令仍在使用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        mockMvc.perform(get("/api/commands/{id}", commandId).session(developer))
                .andExpect(status().isOk());

        long approvedDeleteId = responseId(mockMvc.perform(delete("/api/command-approvals/commands/{id}", commandId)
                        .session(developer)
                        .param("version", String.valueOf(updatedVersion))
                        .param("reason", "已确认下线"))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/command-approvals/{id}/approve", approvedDeleteId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意下线\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/commands/{id}", commandId).session(developer))
                .andExpect(status().isNotFound());
    }

    private String commandPayload(
            String expression,
            String description,
            long sceneId,
            long viewId,
            Long version,
            String reason
    ) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("expressionHtml", expression);
        payload.put("description", description);
        payload.put("regexTemplate", expression);
        payload.put("matchStart", true);
        payload.put("matchEnd", true);
        payload.putArray("currentViewIds").add(viewId);
        payload.putArray("sceneIds").add(sceneId);
        if (version != null) payload.put("version", version);
        if (reason != null) payload.put("changeReason", reason);
        return objectMapper.writeValueAsString(payload);
    }

    private long createNamed(MockHttpSession admin, String path, String name, String extra) throws Exception {
        String body = "{" + (extra == null ? "" : extra) + "\"name\":\"" + name + "\"}";
        return responseId(mockMvc.perform(post(path)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void createDeveloper(MockHttpSession admin) throws Exception {
        mockMvc.perform(post("/api/users")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"approval-dev","displayName":"审批开发人员","role":"DEVELOPER","password":"password1"}
                                """))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long responseId(MvcResult result) throws Exception {
        return data(result).path("id").asLong();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
