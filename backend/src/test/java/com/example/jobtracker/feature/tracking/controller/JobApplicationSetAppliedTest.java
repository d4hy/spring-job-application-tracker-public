package com.example.jobtracker.feature.tracking.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobApplicationSetAppliedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void setAppliedMovesJobIntoAppliedColumn() throws Exception {
        String token = loginAndGetToken("demo", "demo123");
        BoardColumnIds ids = fetchBoardColumnIds(token);
        Long createdJobId = createWishListJob(token, ids.boardId(), ids.wishListColumnId());

        mockMvc.perform(post("/api/jobs/{id}/set-applied", createdJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdJobId))
                .andExpect(jsonPath("$.statusLaneId").value(ids.appliedColumnId()));
    }

    @Test
    void setStatusMovesJobToAcceptedThenRejected() throws Exception {
        String token = loginAndGetToken("demo", "demo123");
        BoardColumnIds ids = fetchBoardColumnIds(token);
        Long createdJobId = createWishListJob(token, ids.boardId(), ids.wishListColumnId());

        mockMvc.perform(post("/api/jobs/{id}/set-status", createdJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdJobId))
                .andExpect(jsonPath("$.statusLaneId").value(ids.offerColumnId()));

        mockMvc.perform(post("/api/jobs/{id}/set-status", createdJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"rejected\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdJobId))
                .andExpect(jsonPath("$.statusLaneId").value(ids.rejectedColumnId()));
    }

    @Test
    void setStatusRejectsUnsupportedValue() throws Exception {
        String token = loginAndGetToken("demo", "demo123");
        BoardColumnIds ids = fetchBoardColumnIds(token);
        Long createdJobId = createWishListJob(token, ids.boardId(), ids.wishListColumnId());

        mockMvc.perform(post("/api/jobs/{id}/set-status", createdJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"interviewing\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported status: interviewing"));
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
        Long appliedColumnId = null;
        Long offerColumnId = null;
        Long rejectedColumnId = null;
        for (JsonNode statusLane : board.path("statusLanes")) {
            String name = statusLane.path("name").asText("");
            if ("Wish List".equalsIgnoreCase(name)) {
                wishListColumnId = statusLane.path("id").asLong();
            }
            if ("Applied".equalsIgnoreCase(name)) {
                appliedColumnId = statusLane.path("id").asLong();
            }
            if ("Offer".equalsIgnoreCase(name) || "Accepted".equalsIgnoreCase(name)) {
                offerColumnId = statusLane.path("id").asLong();
            }
            if ("Rejected".equalsIgnoreCase(name)) {
                rejectedColumnId = statusLane.path("id").asLong();
            }
        }

        assertNotNull(wishListColumnId, "Wish List column should exist");
        assertNotNull(appliedColumnId, "Applied column should exist");
        assertNotNull(offerColumnId, "Offer/Accepted column should exist");
        assertNotNull(rejectedColumnId, "Rejected column should exist");
        return new BoardColumnIds(boardId, wishListColumnId, appliedColumnId, offerColumnId, rejectedColumnId);
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

    private record BoardColumnIds(Long boardId,
                                  Long wishListColumnId,
                                  Long appliedColumnId,
                                  Long offerColumnId,
                                  Long rejectedColumnId) {
    }
}

