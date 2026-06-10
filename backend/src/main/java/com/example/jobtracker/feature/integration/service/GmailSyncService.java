package com.example.jobtracker.feature.integration.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.entity.Board;
import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.integration.model.entity.GmailIntegrationSettings;
import com.example.jobtracker.feature.tracking.model.entity.JobApplication;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.tracking.repository.BoardColumnRepository;
import com.example.jobtracker.feature.tracking.service.BoardService;
import com.example.jobtracker.feature.integration.repository.GmailIntegrationSettingsRepository;
import com.example.jobtracker.feature.tracking.repository.JobApplicationRepository;
import com.example.jobtracker.feature.integration.model.dto.GmailStatusResponse;
import com.example.jobtracker.feature.integration.model.dto.GmailSyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service-layer component for Gmail Sync workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class GmailSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(GmailSyncService.class);
    private static final int MAX_EMAILS_TO_SCAN = 80;

    private final GmailIntegrationSettingsRepository settingsRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final BoardService boardService;
    private final BoardColumnRepository boardColumnRepository;
    private final GoogleOAuthClient googleOAuthClient;
    private final OfflineModeSupport offlineModeSupport;

    public GmailSyncService(GmailIntegrationSettingsRepository settingsRepository,
                            JobApplicationRepository jobApplicationRepository,
                            BoardService boardService,
                            BoardColumnRepository boardColumnRepository,
                            GoogleOAuthClient googleOAuthClient,
                            OfflineModeSupport offlineModeSupport) {
        this.settingsRepository = settingsRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.boardService = boardService;
        this.boardColumnRepository = boardColumnRepository;
        this.googleOAuthClient = googleOAuthClient;
        this.offlineModeSupport = offlineModeSupport;
    }

    
    
    @Transactional(readOnly = true)
    public GmailStatusResponse getStatus(User user) {
        return settingsRepository.findByUser(user)
                .map(this::toStatusResponse)
                .orElseGet(() -> new GmailStatusResponse(false, false, null, null));
    }

    
    
    @Transactional
    public GmailStatusResponse setAutoSync(User user, boolean enabled) {
        
        offlineModeSupport.requireOnline("Gmail auto-sync");

        GmailIntegrationSettings settings = settingsRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Connect Google first"));

        if (enabled && (!settings.isConnected() || isBlank(settings.getAppPassword()))) {
            throw new IllegalArgumentException("Connect Google first");
        }

        
        settings.setAutoSyncEnabled(enabled);
        
        GmailIntegrationSettings saved = settingsRepository.save(settings);
        return toStatusResponse(saved);
    }

    
    
    @Transactional
    public GmailSyncResponse syncNow(User user) {
        
        offlineModeSupport.requireOnline("Gmail sync");

        GmailIntegrationSettings settings = settingsRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Connect Google first"));

        if (!settings.isConnected() || isBlank(settings.getAppPassword())) {
            throw new IllegalArgumentException("Connect Google first");
        }

        return syncUserInternal(user, settings);
    }

    
    
    @Scheduled(fixedDelayString = "${app.gmail.auto-sync-delay-ms:300000}")
    public void autoSyncEnabledUsers() {
        if (offlineModeSupport.isEnabled()) {
            return;
        }

        
        List<GmailIntegrationSettings> settingsList = settingsRepository.findByConnectedTrueAndAutoSyncEnabledTrue();
        for (GmailIntegrationSettings settings : settingsList) {
            try {
                if (!isBlank(settings.getAppPassword())) {
                    syncUserInternal(settings.getUser(), settings);
                }
            
            
            } catch (Exception ex) {
                String username = settings.getUser() == null ? "unknown" : settings.getUser().getUsername();
                log.warn("Gmail auto-sync failed for user {}: {}", username, ex.getMessage());
            }
        }
    }

    
    
    private GmailSyncResponse syncUserInternal(User user, GmailIntegrationSettings settings) {
        
        LocalDateTime syncedAt = LocalDateTime.now();

        
        String refreshToken = settings.getAppPassword();
        
        String accessToken = googleOAuthClient.refreshAccessToken(refreshToken);
        if (isBlank(settings.getGmailAddress())) {
            settings.setGmailAddress(googleOAuthClient.fetchUserEmail(accessToken));
        }

        List<GoogleOAuthClient.GmailMessageSnippet> emails =
                
                googleOAuthClient.fetchRecentMessageSnippets(accessToken, MAX_EMAILS_TO_SCAN);
        
        List<JobApplication> jobs = jobApplicationRepository.findByColumn_Board_UserOrderByCreatedAtDesc(user);

        if (jobs.isEmpty()) {
            
            settings.setLastSyncedAt(syncedAt);
            
            settingsRepository.save(settings);
            return new GmailSyncResponse(emails.size(), 0, 0, 0, 0, 0, syncedAt);
        }

        
        Map<String, BoardColumn> statusColumns = resolveStatusColumns(user);
        Set<Long> touchedJobs = new HashSet<>();

        int movedToApplied = 0;
        int movedToInterviewing = 0;
        int movedToOffer = 0;
        int movedToRejected = 0;

        for (GoogleOAuthClient.GmailMessageSnippet email : emails) {
            StatusSignal signal = detectStatusSignal(email.searchText());
            if (signal == StatusSignal.NONE) {
                continue;
            }

            JobApplication matched = findBestJobMatch(jobs, email.searchText(), touchedJobs);
            if (matched == null) {
                continue;
            }

            
            BoardColumn targetColumn = targetColumnForSignal(signal, statusColumns);
            if (targetColumn == null || matched.getColumn().getId().equals(targetColumn.getId())) {
                touchedJobs.add(matched.getId());
                continue;
            }

            moveToColumn(matched, targetColumn);
            appendSyncNote(matched, signal, email.subject(), syncedAt);
            touchedJobs.add(matched.getId());

            switch (signal) {
                case APPLIED -> movedToApplied++;
                case INTERVIEWING -> movedToInterviewing++;
                case OFFER -> movedToOffer++;
                case REJECTED -> movedToRejected++;
                default -> { }
            }
        }

        
        settings.setLastSyncedAt(syncedAt);
        
        settingsRepository.save(settings);

        int updatedJobs = movedToApplied + movedToInterviewing + movedToOffer + movedToRejected;
        return new GmailSyncResponse(
                emails.size(),
                updatedJobs,
                movedToApplied,
                movedToInterviewing,
                movedToOffer,
                movedToRejected,
                syncedAt
        );
    }

    
    
    private GmailStatusResponse toStatusResponse(GmailIntegrationSettings settings) {
        return new GmailStatusResponse(
                settings.isConnected(),
                settings.isAutoSyncEnabled(),
                settings.getGmailAddress(),
                settings.getLastSyncedAt()
        );
    }

    
    
    private void moveToColumn(JobApplication job, BoardColumn targetColumn) {
        
        List<JobApplication> existing = jobApplicationRepository.findByColumnOrderByOrderIndexAsc(targetColumn);
        int newOrder = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getOrderIndex() + 100;
        
        job.setColumn(targetColumn);
        
        job.setOrderIndex(newOrder);
        
        jobApplicationRepository.save(job);
    }

    
    
    private void appendSyncNote(JobApplication job, StatusSignal signal, String emailSubject, LocalDateTime syncedAt) {
        String subject = isBlank(emailSubject) ? "No subject" : emailSubject.trim();
        String newLine = "Gmail sync (" + syncedAt + "): moved to " + signal.columnName + " from email \"" + subject + "\".";
        if (isBlank(job.getNotes())) {
            
            job.setNotes(newLine);
        } else {
            job.setNotes(job.getNotes().trim() + "\n" + newLine);
        }
    }

    
    
    private Map<String, BoardColumn> resolveStatusColumns(User user) {
        
        Board board = boardService.getOrCreateDefaultBoard(user);
        
        List<BoardColumn> columns = boardColumnRepository.findByBoard(board);
        Map<String, BoardColumn> byName = new HashMap<>();
        for (BoardColumn column : columns) {
            byName.put(column.getName().toLowerCase(Locale.ROOT), column);
        }
        return byName;
    }

    
    
    private BoardColumn targetColumnForSignal(StatusSignal signal, Map<String, BoardColumn> columns) {
        return switch (signal) {
            case APPLIED -> columns.get("applied");
            case INTERVIEWING -> columns.get("interviewing");
            case OFFER -> columns.get("offer");
            case REJECTED -> columns.get("rejected");
            case NONE -> null;
        };
    }

    
    
    private JobApplication findBestJobMatch(List<JobApplication> jobs, String emailText, Set<Long> touchedJobIds) {
        JobApplication best = null;
        int bestScore = 0;
        for (JobApplication job : jobs) {
            if (touchedJobIds.contains(job.getId())) {
                continue;
            }
            
            int score = scoreJobMatch(job, emailText);
            if (score > bestScore) {
                best = job;
                bestScore = score;
            }
        }
        return bestScore >= 4 ? best : null;
    }

    
    
    private int scoreJobMatch(JobApplication job, String emailText) {
        int score = 0;
        score += matchTokenScore(job.getCompany(), emailText, 4);
        score += matchTokenScore(job.getTitle(), emailText, 3);
        return score;
    }

    
    
    private int matchTokenScore(String value, String emailText, int weight) {
        if (isBlank(value)) {
            return 0;
        }

        
        String normalized = normalize(value);
        if (normalized.length() < 3) {
            return 0;
        }

        if (emailText.contains(normalized)) {
            return weight;
        }

        
        String[] parts = normalized.split("\\s+");
        int score = 0;
        for (String part : parts) {
            if (part.length() >= 4 && emailText.contains(part)) {
                score += 1;
            }
        }
        return score;
    }

    
    
    private StatusSignal detectStatusSignal(String emailText) {
        if (containsAny(emailText,
                "unfortunately",
                "not moving forward",
                "rejected",
                "declined",
                "position has been filled",
                "we have decided to move forward with other candidates",
                "no longer considering")) {
            return StatusSignal.REJECTED;
        }

        if (containsAny(emailText,
                "offer letter",
                "job offer",
                "offer for",
                "we are excited to offer")) {
            return StatusSignal.OFFER;
        }

        if (containsAny(emailText,
                "interview",
                "schedule a call",
                "next round",
                "technical assessment",
                "onsite")) {
            return StatusSignal.INTERVIEWING;
        }

        if (containsAny(emailText,
                "application received",
                "thank you for applying",
                "thanks for applying",
                "your application has been submitted")) {
            return StatusSignal.APPLIED;
        }

        return StatusSignal.NONE;
    }

    
    
    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    
    
    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    
    
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private enum StatusSignal {
        
        NONE(""),
        APPLIED("Applied"),
        INTERVIEWING("Interviewing"),
        OFFER("Offer"),
        REJECTED("Rejected");

        private final String columnName;

        StatusSignal(String columnName) {
            this.columnName = columnName;
        }
    }
}
