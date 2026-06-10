package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.constants.TrackerConstants;
import com.example.jobtracker.feature.tracking.model.entity.Board;
import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.tracking.repository.BoardRepository;
import com.example.jobtracker.feature.tracking.repository.BoardColumnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Service-layer component for Board workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class BoardService {
    private static final String LEGACY_PART_TIME_JOBS_BOARD_NAME = "Part-Time Jobs";

    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;

    public BoardService(BoardRepository boardRepository, BoardColumnRepository boardColumnRepository) {
        this.boardRepository = boardRepository;
        this.boardColumnRepository = boardColumnRepository;
    }

    /**
     * Get all boards for a user
     */
    @Transactional
    public List<Board> getUserBoards(User user) {
        ensureDefaultBoards(user);

        List<Board> boards = boardRepository.findByUser(user);
        boards.sort(Comparator
                .comparingInt(this::defaultBoardOrder)
                .thenComparing(Board::getName, String.CASE_INSENSITIVE_ORDER));
        
        boards.forEach(this::initializeBoardGraph);
        return boards;
    }

    /**
     * Get a board by ID with user authorization check
     */
    @Transactional(readOnly = true)
    public Board getBoardById(Long boardId, User user) {
        Board board = boardRepository.findByIdAndUser(boardId, user)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        initializeBoardGraph(board);
        return board;
    }

    /**
     * Create a new board for a user
     * LEARNING NOTE: Boards can have multiple columns that define the workflow
     */
    @Transactional
    public Board createBoard(String name, User user) {
        
        Board board = new Board(name, user);
        return boardRepository.save(board);
    }

    /**
     * Initialize default columns for a board
     * LEARNING NOTE: Standard Kanban columns for job tracking
     */
    @Transactional
    public void initializeDefaultColumns(Board board) {
        List<String> columnNames = TrackerConstants.DEFAULT_COLUMN_NAMES;

        for (int i = 0; i < columnNames.size(); i++) {
            
            BoardColumn column = new BoardColumn(columnNames.get(i), i, board);
            
            boardColumnRepository.save(column);
        }
    }

    /**
     * Get the "Job Hunt" board or create it if it doesn't exist
     * This is called during user signup to ensure a default board exists
     */
    @Transactional
    public Board getOrCreateDefaultBoard(User user) {
        return getOrCreateBoardByName(TrackerConstants.DEFAULT_BOARD_NAME, user);
    }

    @Transactional
    public Board getOrCreateBoardByName(String requestedName, User user) {
        String boardName = resolveSupportedBoardName(requestedName);
        renameLegacyFullTimeBoard(user);
        Board board = boardRepository.findByNameAndUser(boardName, user)
                .orElseGet(() -> createBoardWithDefaultColumns(boardName, user));
        ensureDefaultColumns(board);
        return board;
    }

    @Transactional
    public List<Board> ensureDefaultBoards(User user) {
        renameLegacyFullTimeBoard(user);

        List<Board> boards = new ArrayList<>();
        for (String boardName : TrackerConstants.DEFAULT_BOARD_NAMES) {
            boards.add(getOrCreateBoardByName(boardName, user));
        }
        return boards;
    }

    /**
     * Delete a board (with authorization check)
     */
    @Transactional
    public void deleteBoard(Long boardId, User user) {
        
        Board board = getBoardById(boardId, user);
        
        boardRepository.delete(board);
    }

    
    
    private void initializeBoardGraph(Board board) {
        board.getColumns().forEach(column -> column.getJobs().size());
    }

    private Board createBoardWithDefaultColumns(String name, User user) {
        Board newBoard = createBoard(name, user);
        initializeDefaultColumns(newBoard);
        return newBoard;
    }

    private void ensureDefaultColumns(Board board) {
        if (boardColumnRepository.findByBoard(board).isEmpty()) {
            initializeDefaultColumns(board);
        }
    }

    private String resolveSupportedBoardName(String requestedName) {
        if (requestedName == null || requestedName.trim().isEmpty()) {
            return TrackerConstants.DEFAULT_BOARD_NAME;
        }

        String normalized = requestedName.trim().toLowerCase(Locale.ROOT);
        if (LEGACY_PART_TIME_JOBS_BOARD_NAME.toLowerCase(Locale.ROOT).equals(normalized)) {
            return TrackerConstants.FULL_TIME_JOBS_BOARD_NAME;
        }

        for (String boardName : TrackerConstants.DEFAULT_BOARD_NAMES) {
            if (boardName.toLowerCase(Locale.ROOT).equals(normalized)) {
                return boardName;
            }
        }

        throw new IllegalArgumentException("Unsupported board: " + requestedName);
    }

    private void renameLegacyFullTimeBoard(User user) {
        Board legacyBoard = boardRepository.findByNameAndUser(LEGACY_PART_TIME_JOBS_BOARD_NAME, user)
                .orElse(null);
        if (legacyBoard == null) {
            return;
        }

        boolean fullTimeBoardAlreadyExists = boardRepository
                .findByNameAndUser(TrackerConstants.FULL_TIME_JOBS_BOARD_NAME, user)
                .isPresent();
        if (fullTimeBoardAlreadyExists) {
            return;
        }

        legacyBoard.setName(TrackerConstants.FULL_TIME_JOBS_BOARD_NAME);
        boardRepository.save(legacyBoard);
    }

    private int defaultBoardOrder(Board board) {
        String boardName = board.getName();
        for (int i = 0; i < TrackerConstants.DEFAULT_BOARD_NAMES.size(); i++) {
            if (TrackerConstants.DEFAULT_BOARD_NAMES.get(i).equalsIgnoreCase(boardName)) {
                return i;
            }
        }
        return TrackerConstants.DEFAULT_BOARD_NAMES.size();
    }
}
