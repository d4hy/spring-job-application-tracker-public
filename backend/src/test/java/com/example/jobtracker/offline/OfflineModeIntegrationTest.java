package com.example.jobtracker.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.offline-mode=true")
@AutoConfigureMockMvc
class OfflineModeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void scrapeLinkAndSaveReturnsServiceUnavailable() throws Exception {
        String token = loginAndGetToken("demo", "demo123");

        mockMvc.perform(post("/api/jobs/scrape-link-and-save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.linkedin.com/jobs/view/1234567890/\",\"markApplied\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(containsString("offline mode")));
    }

    @Test
    void googleAuthorizeReturnsServiceUnavailable() throws Exception {
        String token = loginAndGetToken("demo", "demo123");

        mockMvc.perform(get("/api/integrations/google/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(containsString("offline mode")));
    }

    @Test
    void cloudUploadReturnsServiceUnavailable() throws Exception {
        String token = loginAndGetToken("demo", "demo123");

        mockMvc.perform(post("/api/backups/cloud-upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(containsString("offline mode")));
    }

    @Test
    void localBackupExportStillWorksOffline() throws Exception {
        String token = loginAndGetToken("demo", "demo123");

        mockMvc.perform(get("/api/backups/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.formatVersion").value("1.0"));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = json.path("token").asText();
        assertFalse(token.isBlank(), "Login token should not be blank");
        return token;
    }
}

