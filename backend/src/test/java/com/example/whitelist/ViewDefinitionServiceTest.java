package com.example.whitelist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "spring.datasource.url=jdbc:h2:mem:viewdefinition;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.username=config-admin",
        "app.admin.password=config-password"
})
@AutoConfigureMockMvc
class ViewDefinitionServiceTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ordersViewOptionsByDisplayOrderThenName() throws Exception {
        MockHttpSession session = login();
        long lowPriorityId = createView(session, "低频视图", 0);
        createView(session, "常用视图B", 100);
        createView(session, "常用视图A", 100);

        mockMvc.perform(get("/api/views/options").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("常用视图A"))
                .andExpect(jsonPath("$.data[1].name").value("常用视图B"))
                .andExpect(jsonPath("$.data[2].name").value("低频视图"));

        mockMvc.perform(get("/api/views").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].displayOrder").value(100))
                .andExpect(jsonPath("$.data.records[2].displayOrder").value(0));

        mockMvc.perform(put("/api/views/{id}", lowPriorityId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"低频视图\",\"displayOrder\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayOrder").value(200));

        mockMvc.perform(get("/api/views/options").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("低频视图"));
    }

    @Test
    void rejectsDisplayOrderOutsideAllowedRange() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/views")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无效视图\",\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.displayOrder").value("展示顺序不能小于0"));
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"config-admin\",\"password\":\"config-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long createView(MockHttpSession session, String name, int displayOrder) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/views")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","displayOrder":%d}
                                """.formatted(name, displayOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayOrder").value(displayOrder))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();
    }
}
