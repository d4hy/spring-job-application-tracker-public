package com.example.jobtracker.feature.backup.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportBackupReturnsUserPayload() throws Exception {
        String token = loginAndGetToken("demo", "demo123");

        mockMvc.perform(get("/api/backups/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.formatVersion").value("1.0"))
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.boards").isArray())
                .andExpect(jsonPath("$.boards.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void importBackupReplacesTrackerData() throws Exception {
        String token = loginAndGetToken("demo", "demo123");

        String payload = """
                {
                  "formatVersion": "1.0",
                  "exportedAt": "2026-02-16T12:00:00",
                  "username": "demo",
                  "boards": [
                    {
                      "name": "Job Hunt",
                      "columns": [
                        {"name": "Wish List", "orderIndex": 0, "jobs": []},
                        {
                          "name": "Applied",
                          "orderIndex": 1,
                          "jobs": [
                            {
                              "company": "Acme Corp",
                              "title": "Software Engineer",
                              "orderIndex": 0,
                              "location": "Remote",
                              "salary": "$120,000 - $140,000",
                              "jobUrl": "https://example.com/jobs/1",
                              "notes": "Imported from backup"
                            }
                          ]
                        },
                        {"name": "Interviewing", "orderIndex": 2, "jobs": []},
                        {"name": "Offer", "orderIndex": 3, "jobs": []},
                        {"name": "Rejected", "orderIndex": 4, "jobs": []}
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/backups/import")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boardsImported").value(1))
                .andExpect(jsonPath("$.columnsImported").value(5))
                .andExpect(jsonPath("$.jobsImported").value(1));

        mockMvc.perform(get("/api/backups/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boards.length()").value(1))
                .andExpect(jsonPath("$.boards[0].name").value("Job Hunt"))
                .andExpect(jsonPath("$.boards[0].columns.length()").value(5));
    }

    @Test
    void cloudStatusAndUploadReflectUnconfiguredDefaults() throws Exception {
        String token = loginAndGetToken("demo", "demo123");

        mockMvc.perform(get("/api/backups/cloud-status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        mockMvc.perform(post("/api/backups/cloud-upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cloud backup upload URL is not configured."));
    }

    @Test
    void googleSheetUploadRequiresConnectedGoogleOAuth() throws Exception {
        String token = loginAndGetToken("demo", "demo123");

        mockMvc.perform(post("/api/backups/google-sheet-upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spreadsheet\":\"https://docs.google.com/spreadsheets/d/1abcdefghijklmnopqrstuvwxyz1234567890/edit\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Connect Google OAuth first from Integrations."));
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
