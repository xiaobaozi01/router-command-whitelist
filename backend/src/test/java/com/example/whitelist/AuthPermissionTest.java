package com.example.whitelist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authpermission;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.username=config-admin",
        "app.admin.password=config-password",
        "app.admin.display-name=配置管理员"
})
@AutoConfigureMockMvc
class AuthPermissionTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void enforcesConfiguredAdminAndRolePermissions() throws Exception {
        mockMvc.perform(get("/api/commands"))
                .andExpect(status().isUnauthorized());

        MockHttpSession adminSession = login("config-admin", "config-password", "ADMIN");
        createUser(adminSession, "developer1", "开发人员一", "DEVELOPER", "password1");
        createUser(adminSession, "viewer1", "普通用户一", "USER", "password1");

        MockHttpSession developerSession = login("developer1", "password1", "DEVELOPER");
        mockMvc.perform(post("/api/commands/regex-preview")
                        .session(developerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regexTemplate":"display","matchStart":true,"matchEnd":true,"testText":"display"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/scenes/export")
                        .session(developerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneIds\":[1]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").session(developerSession))
                .andExpect(status().isForbidden());

        MockHttpSession viewerSession = login("viewer1", "password1", "USER");
        mockMvc.perform(get("/api/commands").session(viewerSession))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/commands/regex-preview")
                        .session(viewerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regexTemplate\":\"display\",\"testText\":\"display\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/auth/password")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"config-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员密码请在后端 application.yml 中修改"));

        mockMvc.perform(put("/api/auth/password")
                        .session(viewerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1\",\"newPassword\":\"password2\"}"))
                .andExpect(status().isOk());
        login("viewer1", "password2", "USER");
    }

    private MockHttpSession login(String username, String password, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(role))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void createUser(
            MockHttpSession session,
            String username,
            String displayName,
            String role,
            String password
    ) throws Exception {
        mockMvc.perform(post("/api/users")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"%s","role":"%s","password":"%s"}
                                """.formatted(username, displayName, role, password)))
                .andExpect(status().isOk());
    }
}
