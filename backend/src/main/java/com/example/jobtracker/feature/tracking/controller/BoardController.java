package com.example.jobtracker.feature.tracking.controller;

import com.example.jobtracker.feature.tracking.model.entity.Board;
import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.tracking.model.entity.JobApplication;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.tracking.service.BoardService;
import com.example.jobtracker.feature.auth.service.UserService;
import com.example.jobtracker.feature.tracking.model.dto.JobBoardDto;
import com.example.jobtracker.feature.tracking.model.dto.JobCardDto;
import com.example.jobtracker.feature.tracking.model.dto.JobStatusLaneDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for board retrieval workflows.
 * Exposes read endpoints under /api/boards for the authenticated user.
 * Resolves the current user from Spring Security context, loads data through services,
 * and maps entities to API DTOs.
 *
 * Payload shape represented by this controller:
 * - JobBoardDto: one board category, such as Internships or Full-Time Jobs.
 * - JobStatusLaneDto: one workflow lane inside that board, such as Applied.
 * - JobCardDto: one saved job card inside a status lane.
 *
 * In UI terms, this is the full Kanban payload: board -> status lanes -> job cards.
 * Requests reaching this controller are expected to already be authenticated by
 * Spring Security configuration.
 */
@RestController
@RequestMapping("/api/boards")
public class BoardController {
    /**
     * Domain service handling board retrieval and authorization-aware board access.
     */
    private final BoardService boardService;

    /**
     * User service used to resolve the full {@link User} entity from authenticated username.
     */
    private final UserService userService;

    /**
     * Creates the board controller with required service dependencies.
     *
     * @param boardService service for board read operations
     * @param userService service for resolving authenticated users
     */
    public BoardController(BoardService boardService, UserService userService) {
        this.boardService = boardService;
        this.userService = userService;
    }

    /**
     * Resolves the current authenticated user from {@link SecurityContextHolder}.
     * Reads the authenticated username from the security context, then loads the
     * persisted {@link User} entity used by service methods.
     *
     * @return authenticated {@link User} entity for the current request
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        String username = authentication.getName();
        return userService.findByUsername(username);
    }

    /**
     * Returns all boards owned by the current authenticated user.
     * Payload contains board metadata and nested status lanes/jobs mapped to DTOs.
     * Data is scoped to the caller through service-layer ownership checks.
     *
     * The service also ensures the standard boards exist:
     * Internships and Full-Time Jobs.
     *
     * This endpoint is used to render a board chooser or initial dashboard state.
     * Each board in the list is returned with nested status lanes and jobs, so clients
     * can render immediately without making extra per-board requests.
     *
     * @return HTTP {@code 200 OK} with a list of {@link JobBoardDto}
     */
    @GetMapping
    public ResponseEntity<List<JobBoardDto>> getBoards() {
        
        User user = getCurrentUser();
        
        List<Board> boards = boardService.getUserBoards(user);

        List<JobBoardDto> boardDtos = new ArrayList<>(boards.size());
        for (Board board : boards) {
            boardDtos.add(boardToDto(board));
        }

        return ResponseEntity.ok(boardDtos);
    }

    /**
     * Returns one board by identifier for the current authenticated user.
     * Access checks (ownership/visibility) are delegated to {@link BoardService}.
     * The returned object has the same nested shape as the list endpoint:
     * board metadata + ordered status lanes + each lane's jobs.
     *
     * @param id board identifier from the request path
     * @return HTTP {@code 200 OK} with the requested {@link JobBoardDto}
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobBoardDto> getBoard(@PathVariable Long id) {
        
        User user = getCurrentUser();
        
        Board board = boardService.getBoardById(id, user);
        return ResponseEntity.ok(boardToDto(board));
    }

    /**
     * Maps a {@link Board} entity graph to a transport-safe {@link JobBoardDto} DTO.
     * Includes board identity/name, status lanes with ordering metadata, and jobs with
     * core display fields plus optional details.
     * This keeps the HTTP payload contract separate from JPA entity internals.
     *
     * Why this mapping exists:
     * - Entities are persistence models and may include lazy relations/internal fields.
     * - DTOs are API models with only data needed by frontend consumers.
     * - DTOs provide a stable contract even if entities change later.
     *
     * Field intent in returned DTOs:
     * - orderIndex on status lanes/jobs controls left-to-right and top-to-bottom display order.
     * - columnId in JSON means the status lane id used to locate/move cards efficiently.
     * - createdAt supports sorting and relative time display in UI.
     *
     * @param board source board entity
     * @return mapped {@link JobBoardDto}
     */
    private JobBoardDto boardToDto(Board board) {
        // Top-level board payload shown in the UI as one Kanban board.
        JobBoardDto boardDto = new JobBoardDto(board.getId(), board.getName());

        List<JobStatusLaneDto> statusLaneDtos = new ArrayList<>(board.getColumns().size());
        for (BoardColumn statusLane : board.getColumns()) {
            statusLaneDtos.add(statusLaneToDto(statusLane));
        }

        // Final nested shape: board -> status lanes -> jobs.
        boardDto.setStatusLanes(statusLaneDtos);
        return boardDto;
    }

    /**
     * Converts one persisted board column into a clearer API status-lane DTO.
     * The database entity is still named BoardColumn because the table/model
     * started as a Kanban column, but the API name now explains the app meaning.
     */
    private JobStatusLaneDto statusLaneToDto(BoardColumn statusLane) {
        JobStatusLaneDto statusLaneDto = new JobStatusLaneDto(
                statusLane.getId(),
                statusLane.getName(),
                statusLane.getOrderIndex()
        );

        List<JobCardDto> jobDtos = new ArrayList<>(statusLane.getJobs().size());
        for (JobApplication job : statusLane.getJobs()) {
            jobDtos.add(jobToDto(job, statusLane.getId()));
        }

        statusLaneDto.setJobs(jobDtos);
        return statusLaneDto;
    }

    /**
     * Converts one saved job into the job-card shape used by board payloads.
     */
    private JobCardDto jobToDto(JobApplication job, Long statusLaneId) {
        JobCardDto jobDto = new JobCardDto(
                job.getId(),
                job.getCompany(),
                job.getTitle(),
                job.getOrderIndex(),
                statusLaneId,
                job.getCreatedAt()
        );
        jobDto.setLocation(job.getLocation());
        jobDto.setNotes(job.getNotes());
        jobDto.setSalary(job.getSalary());
        jobDto.setJobUrl(job.getJobUrl());
        return jobDto;
    }
}
