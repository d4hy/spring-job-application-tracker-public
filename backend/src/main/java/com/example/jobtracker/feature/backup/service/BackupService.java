package com.example.jobtracker.feature.backup.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.backup.model.dto.BackupBoardData;
import com.example.jobtracker.feature.backup.model.dto.BackupCloudStatusResponse;
import com.example.jobtracker.feature.backup.model.dto.BackupCloudUploadResponse;
import com.example.jobtracker.feature.backup.model.dto.BackupGoogleSheetUploadRequest;
import com.example.jobtracker.feature.backup.model.dto.BackupGoogleSheetUploadResponse;
import com.example.jobtracker.feature.backup.model.dto.BackupColumnData;
import com.example.jobtracker.feature.backup.model.dto.BackupImportResponse;
import com.example.jobtracker.feature.backup.model.dto.BackupJobData;
import com.example.jobtracker.feature.backup.model.dto.TrackerBackupPayload;
import com.example.jobtracker.feature.integration.model.entity.GmailIntegrationSettings;
import com.example.jobtracker.feature.integration.repository.GmailIntegrationSettingsRepository;
import com.example.jobtracker.feature.integration.service.GoogleOAuthClient;
import com.example.jobtracker.feature.tracking.model.entity.Board;
import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.tracking.model.entity.JobApplication;
import com.example.jobtracker.feature.tracking.repository.BoardColumnRepository;
import com.example.jobtracker.feature.tracking.repository.BoardRepository;
import com.example.jobtracker.feature.tracking.repository.JobApplicationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service-layer component for Backup workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class BackupService {
    private static final Pattern GOOGLE_SHEET_URL_PATTERN =
            Pattern.compile("https?://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);

    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final GmailIntegrationSettingsRepository settingsRepository;
    private final GoogleOAuthClient googleOAuthClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String cloudUploadUrl;
    private final String cloudApiKey;
    private final OfflineModeSupport offlineModeSupport;

    public BackupService(BoardRepository boardRepository,
                         BoardColumnRepository boardColumnRepository,
                         JobApplicationRepository jobApplicationRepository,
                         GmailIntegrationSettingsRepository settingsRepository,
                         GoogleOAuthClient googleOAuthClient,
                         ObjectMapper objectMapper,
                         @Value("${app.cloud-backup.upload-url:}") String cloudUploadUrl,
                         @Value("${app.cloud-backup.api-key:}") String cloudApiKey,
                         OfflineModeSupport offlineModeSupport) {
        this.boardRepository = boardRepository;
        this.boardColumnRepository = boardColumnRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.settingsRepository = settingsRepository;
        this.googleOAuthClient = googleOAuthClient;
        this.objectMapper = objectMapper;
        this.cloudUploadUrl = cloudUploadUrl;
        this.cloudApiKey = cloudApiKey;
        
        this.httpClient = HttpClient.newHttpClient();
        this.offlineModeSupport = offlineModeSupport;
    }

    
    
    @Transactional(readOnly = true)
    public TrackerBackupPayload exportBackup(User user) {
        
        List<Board> boards = boardRepository.findByUser(user);
        
        TrackerBackupPayload payload = new TrackerBackupPayload();
        
        payload.setFormatVersion("1.0");
        payload.setExportedAt(LocalDateTime.now().toString());
        payload.setUsername(user.getUsername());
        payload.setBoards(boards.stream()
                .map(this::mapBoard)
                .toList());
        return payload;
    }

    
    
    @Transactional
    public BackupImportResponse importBackup(User user, TrackerBackupPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Backup payload is required");
        }
        if (payload.getBoards() == null) {
            throw new IllegalArgumentException("Backup payload must include boards.");
        }

        List<BackupBoardData> incomingBoards = safeList(payload.getBoards());

        
        List<Board> existingBoards = boardRepository.findByUser(user);
        for (Board board : existingBoards) {
            
            boardRepository.delete(board);
        }
        
        boardRepository.flush();

        int boardsImported = 0;
        int columnsImported = 0;
        int jobsImported = 0;

        for (int boardIndex = 0; boardIndex < incomingBoards.size(); boardIndex++) {
            
            BackupBoardData boardData = incomingBoards.get(boardIndex);
            String boardName = defaultIfBlank(boardData.getName(), "Imported Board " + (boardIndex + 1));
            Board savedBoard = boardRepository.save(new Board(boardName, user));
            boardsImported++;

            List<BackupColumnData> columns = safeList(boardData.getColumns());
            for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                
                BackupColumnData columnData = columns.get(colIndex);
                String columnName = defaultIfBlank(columnData.getName(), "Column " + (colIndex + 1));
                int orderIndex = columnData.getOrderIndex() == null ? colIndex : columnData.getOrderIndex();

                BoardColumn savedColumn = boardColumnRepository.save(new BoardColumn(columnName, orderIndex, savedBoard));
                columnsImported++;

                List<BackupJobData> jobs = safeList(columnData.getJobs());
                for (int jobIndex = 0; jobIndex < jobs.size(); jobIndex++) {
                    
                    BackupJobData jobData = jobs.get(jobIndex);
                    int jobOrder = jobData.getOrderIndex() == null ? jobIndex * 100 : jobData.getOrderIndex();
                    JobApplication job = new JobApplication(
                            defaultIfBlank(jobData.getCompany(), "Unknown company"),
                            defaultIfBlank(jobData.getTitle(), "Untitled role"),
                            jobOrder,
                            savedColumn
                    );
                    job.setLocation(defaultIfBlank(jobData.getLocation(), ""));
                    job.setSalary(defaultIfBlank(jobData.getSalary(), ""));
                    job.setNotes(defaultIfBlank(jobData.getNotes(), ""));
                    job.setJobUrl(defaultIfBlank(jobData.getJobUrl(), ""));

                    LocalDateTime parsedCreatedAt = parseDateTime(jobData.getCreatedAt());
                    if (parsedCreatedAt != null) {
                        
                        job.setCreatedAt(parsedCreatedAt);
                    }

                    
                    jobApplicationRepository.save(job);
                    jobsImported++;
                }
            }
        }

        return new BackupImportResponse(
                "Backup imported successfully.",
                boardsImported,
                columnsImported,
                jobsImported
        );
    }

    
    
    @Transactional(readOnly = true)
    public BackupCloudStatusResponse cloudStatus() {
        if (offlineModeSupport.isEnabled()) {
            return new BackupCloudStatusResponse(false, "");
        }

        
        String destination = normalizeCloudUrl(cloudUploadUrl);
        
        boolean configured = !isBlank(destination);
        return new BackupCloudStatusResponse(configured, configured ? destination : "");
    }

    
    
    @Transactional(readOnly = true)
    public BackupCloudUploadResponse uploadBackupToCloud(User user) {
        
        offlineModeSupport.requireOnline("Cloud backup upload");

        
        String destination = normalizeCloudUrl(cloudUploadUrl);
        if (isBlank(destination)) {
            throw new IllegalArgumentException("Cloud backup upload URL is not configured.");
        }

        
        TrackerBackupPayload payload = exportBackup(user);
        
        String body = toJson(payload);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(destination))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (!isBlank(cloudApiKey)) {
            requestBuilder.header("Authorization", "Bearer " + cloudApiKey.trim());
        }

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            String message = success
                    ? "Backup uploaded to cloud."
                    : "Cloud upload failed with status " + response.statusCode() + ".";
            return new BackupCloudUploadResponse(success, response.statusCode(), destination, message);
        
        
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Cloud upload was interrupted.", e);
        
        
        } catch (IOException e) {
            throw new IllegalArgumentException("Cloud upload failed.", e);
        }
    }

    
    
    @Transactional(readOnly = true)
    public BackupGoogleSheetUploadResponse uploadBackupToGoogleSheet(User user, BackupGoogleSheetUploadRequest request) {
        
        offlineModeSupport.requireOnline("Google Sheets backup export");

        if (request == null || isBlank(request.getSpreadsheet())) {
            throw new IllegalArgumentException("Google spreadsheet URL or ID is required.");
        }

        GmailIntegrationSettings settings = settingsRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Connect Google OAuth first from Integrations."));

        if (!settings.isConnected() || isBlank(settings.getAppPassword())) {
            throw new IllegalArgumentException("Connect Google OAuth first from Integrations.");
        }

        String spreadsheetId = parseSpreadsheetId(request.getSpreadsheet());
        String sheetName = normalizeSheetName(request.getSheetName());
        
        String refreshToken = settings.getAppPassword();
        String accessToken;

        try {
            
            accessToken = googleOAuthClient.refreshAccessToken(refreshToken);
        
        
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Google token refresh failed. Reconnect Google OAuth and try again.", ex);
        }

        
        TrackerBackupPayload payload = exportBackup(user);
        
        List<List<String>> rows = buildGoogleSheetRows(payload);
        String clearRange = isBlank(sheetName) ? "A:Z" : escapeSheetName(sheetName) + "!A:Z";
        String writeRange = isBlank(sheetName) ? "A1" : escapeSheetName(sheetName) + "!A1";

        try {
            
            googleOAuthClient.replaceSheetValues(accessToken, spreadsheetId, clearRange, writeRange, rows);
        
        
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Could not write to Google Sheet. If access was denied, reconnect Google OAuth and try again.", ex);
        }

        int rowsWritten = Math.max(0, rows.size() - 1);
        String targetSheet = isBlank(sheetName) ? "first-sheet" : sheetName;
        return new BackupGoogleSheetUploadResponse(
                true,
                spreadsheetId,
                targetSheet,
                rowsWritten,
                "Backup synced to Google Sheet (" + rowsWritten + " rows)."
        );
    }

    
    
    private BackupBoardData mapBoard(Board board) {
        
        BackupBoardData boardData = new BackupBoardData();
        boardData.setName(board.getName());
        boardData.setColumns(safeList(board.getColumns()).stream()
                .sorted(Comparator.comparingInt(BoardColumn::getOrderIndex))
                .map(this::mapColumn)
                .toList());
        return boardData;
    }

    
    
    private BackupColumnData mapColumn(BoardColumn column) {
        
        BackupColumnData columnData = new BackupColumnData();
        columnData.setName(column.getName());
        columnData.setOrderIndex(column.getOrderIndex());
        columnData.setJobs(safeList(column.getJobs()).stream()
                .sorted(Comparator.comparingInt(JobApplication::getOrderIndex))
                .map(this::mapJob)
                .toList());
        return columnData;
    }

    
    
    private BackupJobData mapJob(JobApplication job) {
        
        BackupJobData jobData = new BackupJobData();
        jobData.setCompany(job.getCompany());
        jobData.setTitle(job.getTitle());
        jobData.setOrderIndex(job.getOrderIndex());
        jobData.setLocation(job.getLocation());
        jobData.setSalary(job.getSalary());
        jobData.setNotes(job.getNotes());
        jobData.setJobUrl(job.getJobUrl());
        jobData.setCreatedAt(job.getCreatedAt() == null ? null : job.getCreatedAt().toString());
        return jobData;
    }

    
    
    private LocalDateTime parseDateTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        
        
        } catch (Exception ex) {
            return null;
        }
    }

    
    
    private List<List<String>> buildGoogleSheetRows(TrackerBackupPayload payload) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Board", "Column", "Company", "Title", "Location", "Salary", "Job URL", "Notes", "Created At"));

        for (BackupBoardData board : safeList(payload.getBoards())) {
            String boardName = defaultIfBlank(board.getName(), "Board");
            for (BackupColumnData column : safeList(board.getColumns())) {
                String columnName = defaultIfBlank(column.getName(), "Column");
                for (BackupJobData job : safeList(column.getJobs())) {
                    rows.add(List.of(
                            boardName,
                            columnName,
                            defaultIfBlank(job.getCompany(), ""),
                            defaultIfBlank(job.getTitle(), ""),
                            defaultIfBlank(job.getLocation(), ""),
                            defaultIfBlank(job.getSalary(), ""),
                            defaultIfBlank(job.getJobUrl(), ""),
                            defaultIfBlank(job.getNotes(), ""),
                            defaultIfBlank(job.getCreatedAt(), "")
                    ));
                }
            }
        }

        return rows;
    }

    
    
    private String parseSpreadsheetId(String input) {
        
        String trimmed = input.trim();
        
        Matcher urlMatcher = GOOGLE_SHEET_URL_PATTERN.matcher(trimmed);
        if (urlMatcher.find()) {
            return urlMatcher.group(1);
        }

        if (trimmed.matches("[a-zA-Z0-9_-]{20,}")) {
            return trimmed;
        }

        throw new IllegalArgumentException("Provide a valid Google Sheet URL or spreadsheet ID.");
    }

    
    
    private String normalizeSheetName(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim();
    }

    
    
    private String escapeSheetName(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    
    
    private String toJson(TrackerBackupPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        
        
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize backup payload.", e);
        }
    }

    
    
    private String normalizeCloudUrl(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.trim();
    }

    
    
    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    
    
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    
    
    private <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }
}
