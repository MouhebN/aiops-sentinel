package com.aiops.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NetFlowAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void operatorCanReachImportLatestEndpoint() throws Exception {
        mockMvc.perform(post("/api/netflow/import/latest")
                        .param("sourceId", "1")
                        .header("Authorization", "Bearer " + login("operator@aiops.local", "operator123")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/netflow/import/latest"));
    }

    @Test
    void operatorCanImportSample() throws Exception {
        mockMvc.perform(post("/api/netflow/import/sample")
                        .header("Authorization", "Bearer " + login("operator@aiops.local", "operator123")))
                .andExpect(status().isOk());
    }

    @Test
    void operatorCanListNetFlowSources() throws Exception {
        mockMvc.perform(get("/api/netflow/sources")
                        .header("Authorization", "Bearer " + login("operator@aiops.local", "operator123")))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotCallImportEndpoint() throws Exception {
        mockMvc.perform(post("/api/netflow/import/latest")
                        .param("sourceId", "1")
                        .header("Authorization", "Bearer " + login("viewer@aiops.local", "viewer123")))
                .andExpect(status().isForbidden());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String marker = "\"token\":\"";
        int start = body.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("Login response did not include token: " + body);
        }
        int tokenStart = start + marker.length();
        int tokenEnd = body.indexOf('"', tokenStart);
        if (tokenEnd < 0) {
            throw new IllegalStateException("Login response token was not terminated: " + body);
        }
        return body.substring(tokenStart, tokenEnd);
    }
}
