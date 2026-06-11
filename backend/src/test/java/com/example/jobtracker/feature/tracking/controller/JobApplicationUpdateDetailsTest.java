package com.example.jobtracker.feature.tracking.controller;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.auth.service.UserService;
import com.example.jobtracker.feature.tracking.service.BoardService;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class JobApplicationUpdateDetailsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private BoardService boardService;

    @Test
    void updateJobApplication_updatesEditableDetails() throws Exception {
        String token = registerUserAndGetToken();
        BoardColumnIds ids = fetchBoardColumnIds(token);
        Long createdJobId = createWishListJob(token, ids.boardId(), ids.wishListColumnId());

        String updateBody = """
                {
                  "jobTitle": "Senior Backend Engineer",
                  "companyName": "Acme Renamed",
                  "location": "Austin, TX",
                  "salary": "$150,000 / year",
                  "jobUrl": "https://example.com/role/42?utm_source=test",
                  "notes": "Updated from saved jobs view"
                }
                """;

        mockMvc.perform(put("/api/jobs/{id}", createdJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdJobId))
                .andExpect(jsonPath("$.jobTitle").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.companyName").value("Acme Renamed"))
                .andExpect(jsonPath("$.location").value("Austin, TX"))
                .andExpect(jsonPath("$.salary").value("$150,000 / year"))
                .andExpect(jsonPath("$.jobUrl").value("https://example.com/role/42"))
                .andExpect(jsonPath("$.notes").value("Updated from saved jobs view"));

        mockMvc.perform(get("/api/jobs/saved")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(createdJobId))
                .andExpect(jsonPath("$[0].jobTitle").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$[0].companyName").value("Acme Renamed"))
                .andExpect(jsonPath("$[0].location").value("Austin, TX"))
                .andExpect(jsonPath("$[0].salary").value("$150,000 / year"))
                .andExpect(jsonPath("$[0].jobUrl").value("https://example.com/role/42"))
                .andExpect(jsonPath("$[0].notes").value("Updated from saved jobs view"));
    }

    @Test
    void updateJobApplication_rejectsBlankTitleWhenProvided() throws Exception {
        String token = registerUserAndGetToken();
        BoardColumnIds ids = fetchBoardColumnIds(token);
        Long createdJobId = createWishListJob(token, ids.boardId(), ids.wishListColumnId());

        mockMvc.perform(put("/api/jobs/{id}", createdJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title cannot be blank"));
    }

    private BoardColumnIds fetchBoardColumnIds(String token) throws Exception {
        MvcResult boardResult = mockMvc.perform(get("/api/boards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode boards = objectMapper.readTree(boardResult.getResponse().getContentAsString());
        JsonNode board = boards.get(0);
        Long boardId = board.path("id").asLong();

        Long wishListColumnId = null;
        for (JsonNode statusLane : board.path("statusLanes")) {
            String name = statusLane.path("name").asText("");
            if ("Wish List".equalsIgnoreCase(name)) {
                wishListColumnId = statusLane.path("id").asLong();
            }
        }

        assertNotNull(wishListColumnId, "Wish List column should exist");
        return new BoardColumnIds(boardId, wishListColumnId);
    }

    private Long createWishListJob(String token, Long boardId, Long wishListColumnId) throws Exception {
        String body = String.format(
                "{\"boardId\":%d,\"statusLaneId\":%d,\"companyName\":\"Acme Corp\",\"jobTitle\":\"Backend Engineer\"}",
                boardId,
                wishListColumnId
        );

        MvcResult createResult = mockMvc.perform(post("/api/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        return created.path("id").asLong();
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

    private String registerUserAndGetToken() throws Exception {
        String username = "job-update-" + UUID.randomUUID();
        String password = "demo123";
        User user = userService.registerUser(username, password);
        boardService.getOrCreateDefaultBoard(user);
        return loginAndGetToken(username, password);
    }

    private record BoardColumnIds(Long boardId, Long wishListColumnId) {
    }
}
