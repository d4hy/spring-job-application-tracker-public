package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.constants.TrackerConstants;
import com.example.jobtracker.feature.tracking.model.entity.JobApplication;
import com.example.jobtracker.feature.tracking.model.entity.Board;
import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.tracking.repository.JobApplicationRepository;
import com.example.jobtracker.feature.tracking.repository.BoardRepository;
import com.example.jobtracker.feature.tracking.repository.BoardColumnRepository;
import com.example.jobtracker.feature.tracking.model.dto.CreateJobRequest;
import com.example.jobtracker.feature.tracking.model.dto.UpdateJobRequest;
import com.example.jobtracker.feature.tracking.model.dto.SavedJobSummaryDto;
import com.example.jobtracker.feature.tracking.model.dto.ScrapeAndSaveResult;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service-layer component for Job Application workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class JobApplicationService {
    private final JobApplicationRepository jobApplicationRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardService boardService;
    private static final Set<String> TRACKING_QUERY_KEYS = Set.of(
            "ref",
            "referrer",
            "source",
            "src",
            "gh_src",
            "trk",
            "iis",
            "iisn",
            "referral",
            "sourcedetails",
            "trid",
            "channel",
            "rb",
            "rcid",
            "jobboardsource",
            "__jvst",
            "__jvsd",
            "lever-source"
    );

    public JobApplicationService(JobApplicationRepository jobApplicationRepository,
                               BoardRepository boardRepository,
                               BoardColumnRepository boardColumnRepository,
                               BoardService boardService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.boardRepository = boardRepository;
        this.boardColumnRepository = boardColumnRepository;
        this.boardService = boardService;
    }

    /**
     * Create a new job application
     * LEARNING NOTE: Order is calculated as (count * 100) to allow future insertions
     */
    @Transactional
    public JobApplication createJobApplication(CreateJobRequest request, User user) {
        if (request.getBoardId() == null) {
            throw new IllegalArgumentException("boardId is required");
        }
        if (request.getStatusLaneId() == null) {
            throw new IllegalArgumentException("statusLaneId is required");
        }
        if (request.getCompanyName() == null || request.getCompanyName().trim().isEmpty()) {
            throw new IllegalArgumentException("company is required");
        }
        if (request.getJobTitle() == null || request.getJobTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("title is required");
        }

        // Verify board ownership
        Board board = boardRepository.findByIdAndUser(request.getBoardId(), user)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));

        // Verify column belongs to board
        BoardColumn column = boardColumnRepository.findByIdAndBoard(request.getStatusLaneId(), board)
                .orElseThrow(() -> new IllegalArgumentException("Column not found"));

        // Calculate order based on existing jobs in column
        
        List<JobApplication> existingJobs = jobApplicationRepository.findByColumnOrderByOrderIndexAsc(column);
        int newOrder = existingJobs.isEmpty()
                ? 0
                : existingJobs.get(existingJobs.size() - 1).getOrderIndex() + TrackerConstants.JOB_ORDER_STEP;

        JobApplication jobApplication = new JobApplication(
                request.getCompanyName(),
                request.getJobTitle(),
                newOrder,
                column
        );
        jobApplication.setLocation(request.getLocation());
        jobApplication.setNotes(request.getNotes());
        jobApplication.setSalary(request.getSalary());
        jobApplication.setJobUrl(request.getJobUrl());

        return jobApplicationRepository.save(jobApplication);
    }

    /**
     * Move job to different column or reorder within same column
     * LEARNING NOTE: This is complex because it handles:
     * 1. Moving between columns
     * 2. Reordering within a column
     * 3. Shifting other jobs as needed
    */
    @Transactional
    public JobApplication updateJobApplication(Long jobId,
                                               String newStatusLaneId,
                                               Integer newOrder,
                                               UpdateJobRequest updateRequest,
                                               User user) {
        JobApplication job = jobApplicationRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        // Verify user owns this job's board
        Board board = boardRepository.findByIdAndUser(job.getColumn().getBoard().getId(), user)
                .orElseThrow(() -> new IllegalArgumentException("Unauthorized"));

        if (newStatusLaneId != null) {
            
            Long newStatusLaneIdLong;
            try {
                newStatusLaneIdLong = Long.parseLong(newStatusLaneId);
            
            
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("statusLaneId must be a number");
            }
            BoardColumn newColumn = boardColumnRepository.findByIdAndBoard(newStatusLaneIdLong, board)
                    .orElseThrow(() -> new IllegalArgumentException("Column not found"));

            
            job.setColumn(newColumn);

            if (newOrder != null) {
                // Insert at specific position
                int orderValue = newOrder * TrackerConstants.JOB_ORDER_STEP;
                
                job.setOrderIndex(orderValue);

                // Shift jobs that come after this position
                
                List<JobApplication> jobsAfter = jobApplicationRepository.findByColumnOrderByOrderIndexAsc(newColumn);
                jobsAfter.stream()
                    .filter(j -> !j.getId().equals(jobId) && j.getOrderIndex() >= orderValue)
                    .forEach(j -> j.setOrderIndex(j.getOrderIndex() + TrackerConstants.JOB_ORDER_STEP));
            } else {
                // Append to end
                
                List<JobApplication> jobsInColumn = jobApplicationRepository.findByColumnOrderByOrderIndexAsc(newColumn);
                int maxOrder = jobsInColumn.isEmpty() ? 0 : jobsInColumn.get(jobsInColumn.size() - 1).getOrderIndex();
                
                job.setOrderIndex(maxOrder + TrackerConstants.JOB_ORDER_STEP);
            }
        
        
        } else if (newOrder != null) {
            // Reorder within same column
            int orderValue = newOrder * TrackerConstants.JOB_ORDER_STEP;
            
            job.setOrderIndex(orderValue);
        }

        applyEditableFieldUpdates(job, updateRequest);

        return jobApplicationRepository.save(job);
    }

    private void applyEditableFieldUpdates(JobApplication job, UpdateJobRequest updateRequest) {
        if (updateRequest == null) {
            return;
        }

        if (updateRequest.getCompanyName() != null) {
            job.setCompany(requireNonBlank(updateRequest.getCompanyName(), "company"));
        }

        if (updateRequest.getJobTitle() != null) {
            job.setTitle(requireNonBlank(updateRequest.getJobTitle(), "title"));
        }

        if (updateRequest.getLocation() != null) {
            job.setLocation(normalizeOptionalText(updateRequest.getLocation()));
        }

        if (updateRequest.getNotes() != null) {
            job.setNotes(normalizeOptionalText(updateRequest.getNotes()));
        }

        if (updateRequest.getSalary() != null) {
            job.setSalary(normalizeOptionalText(updateRequest.getSalary()));
        }

        if (updateRequest.getJobUrl() != null) {
            String normalizedUrl = normalizeJobUrlForStorage(updateRequest.getJobUrl());
            job.setJobUrl(normalizeOptionalText(normalizedUrl));
        }
    }

    /**
     * Delete a job application
     */
    @Transactional
    public void deleteJobApplication(Long jobId, User user) {
        JobApplication job = jobApplicationRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        // Verify user owns this job's board
        boardRepository.findByIdAndUser(job.getColumn().getBoard().getId(), user)
                .orElseThrow(() -> new IllegalArgumentException("Unauthorized"));

        
        jobApplicationRepository.delete(job);
    }

    /**
     * Get all jobs in a column
     */
    public List<JobApplication> getJobsByColumn(BoardColumn column) {
        return jobApplicationRepository.findByColumnOrderByOrderIndexAsc(column);
    }

    
    
    @Transactional
    public JobApplication setApplied(Long jobId, User user) {
        return setStatus(jobId, TrackerConstants.STATUS_APPLIED, user);
    }

    
    
    @Transactional
    public JobApplication setStatus(Long jobId, String status, User user) {
        if (isBlank(status)) {
            throw new IllegalArgumentException("status is required");
        }

        String normalizedStatus = status.trim().toLowerCase();
        return switch (normalizedStatus) {
            case TrackerConstants.STATUS_APPLIED -> moveToColumnByName(jobId, TrackerConstants.COLUMN_APPLIED, user);
            case TrackerConstants.STATUS_ACCEPTED -> moveToAcceptedColumn(jobId, user);
            case TrackerConstants.STATUS_REJECTED -> moveToColumnByName(jobId, TrackerConstants.COLUMN_REJECTED, user);
            default -> throw new IllegalArgumentException("Unsupported status: " + status);
        };
    }

    
    
    @Transactional
    public JobApplication moveToColumnByName(Long jobId, String targetColumnName, User user) {
        if (isBlank(targetColumnName)) {
            throw new IllegalArgumentException("target column is required");
        }

        JobApplication job = jobApplicationRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        Board board = boardRepository.findByIdAndUser(job.getColumn().getBoard().getId(), user)
                .orElseThrow(() -> new IllegalArgumentException("Unauthorized"));

        BoardColumn targetColumn = boardColumnRepository.findByBoard(board).stream()
                .filter(column -> targetColumnName.equalsIgnoreCase(column.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Column not found: " + targetColumnName));

        if (job.getColumn().getId().equals(targetColumn.getId())) {
            return job;
        }

        
        List<JobApplication> existing = jobApplicationRepository.findByColumnOrderByOrderIndexAsc(targetColumn);
        int newOrder = existing.isEmpty()
                ? 0
                : existing.get(existing.size() - 1).getOrderIndex() + TrackerConstants.JOB_ORDER_STEP;

        
        job.setColumn(targetColumn);
        
        job.setOrderIndex(newOrder);
        return jobApplicationRepository.save(job);
    }

    
    
    @Transactional(readOnly = true)
    public List<SavedJobSummaryDto> getSavedJobs(User user) {
        return jobApplicationRepository.findByColumn_Board_UserOrderByCreatedAtDesc(user)
                .stream()
                .map(job -> new SavedJobSummaryDto(
                        job.getId(),
                        job.getTitle(),
                        job.getCompany(),
                        job.getLocation(),
                        job.getSalary(),
                        job.getJobUrl(),
                        job.getColumn().getBoard().getName(),
                        job.getColumn().getName(),
                        job.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    
    
    @Transactional
    public ScrapeAndSaveResult saveScrapedListing(ScrapedJobListingDto listing, User user) {
        return saveScrapedListing(listing, user, false);
    }

    
    
    @Transactional
    public ScrapeAndSaveResult saveScrapedListing(ScrapedJobListingDto listing, User user, boolean markApplied) {
        if (listing == null) {
            throw new IllegalArgumentException("listing is required");
        }
        Board board = boardService.getOrCreateDefaultBoard(user);
        return saveScrapedListingToBoard(listing, user, markApplied, board);
    }

    
    
    @Transactional
    public ScrapeAndSaveResult saveScrapedListing(ScrapedJobListingDto listing,
                                                    User user,
                                                    boolean markApplied,
                                                    String boardName) {
        if (listing == null) {
            throw new IllegalArgumentException("listing is required");
        }
        Board board = boardService.getOrCreateBoardByName(boardName, user);
        return saveScrapedListingToBoard(listing, user, markApplied, board);
    }

    private ScrapeAndSaveResult saveScrapedListingToBoard(ScrapedJobListingDto listing,
                                                            User user,
                                                            boolean markApplied,
                                                            Board board) {
        CompanyHistory companyHistory = findCompanyHistory(user, listing.getCompany());

        List<BoardColumn> columns = boardColumnRepository.findByBoard(board);
        if (columns.isEmpty()) {
            
            boardService.initializeDefaultColumns(board);
            
            columns = boardColumnRepository.findByBoard(board);
        }

        String targetName = markApplied ? TrackerConstants.COLUMN_APPLIED : TrackerConstants.COLUMN_WISH_LIST;

        BoardColumn targetColumn = columns.stream()
                .filter(column -> targetName.equalsIgnoreCase(column.getName()))
                .findFirst()
                
                .orElse(null);
        if (targetColumn == null) {
            targetColumn = columns.stream()
                    .min(Comparator.comparingInt(BoardColumn::getOrderIndex))
                    
                    .orElse(null);
        }

        if (targetColumn == null) {
            throw new IllegalArgumentException("No target column found");
        }

        String rawOriginalLink = isBlank(listing.getOriginalLink()) ? null : listing.getOriginalLink().trim();
        
        String originalLink = normalizeJobUrlForStorage(rawOriginalLink);
        if (!isBlank(originalLink)) {
            
            listing.setOriginalLink(originalLink);
        }
        if (!isBlank(originalLink)) {
            Optional<JobApplication> existing = jobApplicationRepository
                    
                    .findFirstByColumn_Board_UserAndJobUrl(user, originalLink);
            if (existing.isEmpty() && !isBlank(rawOriginalLink) && !rawOriginalLink.equals(originalLink)) {
                
                existing = jobApplicationRepository.findFirstByColumn_Board_UserAndJobUrl(user, rawOriginalLink);
            }

            if (existing.isPresent()) {
                
                JobApplication job = existing.get();
                if (applyScrapedFieldsToExisting(job, listing)) {
                    
                    job = jobApplicationRepository.save(job);
                }
                if (markApplied && !TrackerConstants.COLUMN_APPLIED.equalsIgnoreCase(job.getColumn().getName())) {
                    job = setApplied(job.getId(), user);
                }
                return new ScrapeAndSaveResult(
                        listing,
                        TrackerConstants.SCRAPE_RESULT_DUPLICATE,
                        job.getColumn().getBoard().getName(),
                        job.getColumn().getName(),
                        job.getId(),
                        companyHistory.previouslyApplied(),
                        companyHistory.applicationCount()
                );
            }
        }

        
        CreateJobRequest request = new CreateJobRequest();
        request.setBoardId(board.getId());
        request.setStatusLaneId(targetColumn.getId());
        request.setCompanyName(defaultIfBlank(listing.getCompany(), TrackerConstants.SCRAPE_FALLBACK_NOT_FOUND));
        request.setJobTitle(defaultIfBlank(listing.getTitle(), TrackerConstants.SCRAPE_FALLBACK_NOT_FOUND));
        request.setLocation(defaultIfBlank(listing.getLocation(), TrackerConstants.SCRAPE_FALLBACK_NOT_FOUND));
        request.setSalary(defaultIfBlank(listing.getSalary(), TrackerConstants.SCRAPE_FALLBACK_NOT_LISTED));
        request.setJobUrl(defaultIfBlank(originalLink, ""));
        request.setNotes(
                "Saved from scrape (" + defaultIfBlank(listing.getSource(), TrackerConstants.SCRAPE_FALLBACK_UNKNOWN_SOURCE) + ")."
                        + (markApplied ? " Marked as applied." : "")
        );

        
        JobApplication saved = createJobApplication(request, user);
        return new ScrapeAndSaveResult(
                listing,
                TrackerConstants.SCRAPE_RESULT_SAVED,
                board.getName(),
                targetColumn.getName(),
                saved.getId(),
                companyHistory.previouslyApplied(),
                companyHistory.applicationCount()
        );
    }

    
    
    private boolean applyScrapedFieldsToExisting(JobApplication job, ScrapedJobListingDto listing) {
        boolean changed = false;

        String title = defaultIfBlank(listing.getTitle(), TrackerConstants.SCRAPE_FALLBACK_NOT_FOUND);
        if (!title.equals(job.getTitle())) {
            
            job.setTitle(title);
            changed = true;
        }

        String company = defaultIfBlank(listing.getCompany(), TrackerConstants.SCRAPE_FALLBACK_NOT_FOUND);
        if (!company.equals(job.getCompany())) {
            
            job.setCompany(company);
            changed = true;
        }

        String location = defaultIfBlank(listing.getLocation(), TrackerConstants.SCRAPE_FALLBACK_NOT_FOUND);
        if (!location.equals(job.getLocation())) {
            
            job.setLocation(location);
            changed = true;
        }

        String salary = defaultIfBlank(listing.getSalary(), TrackerConstants.SCRAPE_FALLBACK_NOT_LISTED);
        if (!salary.equals(job.getSalary())) {
            
            job.setSalary(salary);
            changed = true;
        }

        String jobUrl = defaultIfBlank(normalizeJobUrlForStorage(listing.getOriginalLink()), "");
        if (!isBlank(jobUrl) && !jobUrl.equals(job.getJobUrl())) {
            
            job.setJobUrl(jobUrl);
            changed = true;
        }

        return changed;
    }

    
    
    private String normalizeJobUrlForStorage(String value) {
        if (isBlank(value)) {
            return null;
        }

        
        String trimmed = value.trim();
        try {
            
            URI uri = URI.create(trimmed);

            String host = uri.getHost();
            if (!isBlank(host) && shouldStripAllQueryParameters(host)) {
                URI noQueryNoFragment = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
                return noQueryNoFragment.toString();
            }
            
            String rawQuery = uri.getRawQuery();
            if (isBlank(rawQuery)) {
                URI noFragment = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
                return noFragment.toString();
            }

            
            String[] pairs = rawQuery.split("&");
            List<String> retained = new ArrayList<>();
            for (String pair : pairs) {
                if (isBlank(pair)) {
                    continue;
                }
                String key = pair;
                
                int equalsIndex = pair.indexOf('=');
                if (equalsIndex >= 0) {
                    
                    key = pair.substring(0, equalsIndex);
                }
                String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (isTrackingQueryParameter(decodedKey)) {
                    continue;
                }
                
                retained.add(pair);
            }

            String normalizedQuery = retained.isEmpty() ? null : String.join("&", retained);
            URI normalized = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), normalizedQuery, null);
            return normalized.toString();
        
        
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    private boolean isTrackingQueryParameter(String decodedKey) {
        if (isBlank(decodedKey)) {
            return false;
        }
        String key = decodedKey.trim().toLowerCase(Locale.ROOT);
        if (key.endsWith("[]")) {
            key = key.substring(0, key.length() - 2);
        }
        if (key.startsWith("utm_")) {
            return true;
        }
        if (TRACKING_QUERY_KEYS.contains(key)) {
            return true;
        }
        return key.endsWith("_source") || key.endsWith("source");
    }

    private boolean shouldStripAllQueryParameters(String host) {
        if (isBlank(host)) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.endsWith("lever.co")
                || normalized.equals("handshake.com")
                || normalized.endsWith(".handshake.com")
                || normalized.endsWith("joinhandshake.com")
                || normalized.endsWith(".joinhandshake.com");
    }

    
    
    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private CompanyHistory findCompanyHistory(User user, String company) {
        String normalizedCompany = normalizeCompanyForComparison(company);
        if (normalizedCompany == null) {
            return CompanyHistory.none();
        }

        List<JobApplication> existingJobs = jobApplicationRepository.findByColumn_Board_UserOrderByCreatedAtDesc(user);
        if (existingJobs == null || existingJobs.isEmpty()) {
            return CompanyHistory.none();
        }

        int applicationCount = 0;
        for (JobApplication job : existingJobs) {
            if (job == null || !countAsAppliedHistory(job)) {
                continue;
            }

            String normalizedJobCompany = normalizeCompanyForComparison(job.getCompany());
            if (normalizedCompany.equals(normalizedJobCompany)) {
                applicationCount++;
            }
        }

        return applicationCount > 0
                ? new CompanyHistory(true, applicationCount)
                : CompanyHistory.none();
    }

    private String normalizeCompanyForComparison(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (TrackerConstants.SCRAPE_FALLBACK_NOT_FOUND.equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private boolean countAsAppliedHistory(JobApplication job) {
        BoardColumn column = job.getColumn();
        if (column == null || isBlank(column.getName())) {
            return false;
        }
        return !TrackerConstants.COLUMN_WISH_LIST.equalsIgnoreCase(column.getName().trim());
    }

    private String requireNonBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    
    
    private JobApplication moveToAcceptedColumn(Long jobId, User user) {
        try {
            return moveToColumnByName(jobId, TrackerConstants.COLUMN_OFFER, user);
        
        
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Column not found")) {
                return moveToColumnByName(jobId, TrackerConstants.COLUMN_ACCEPTED, user);
            }
            throw ex;
        }
    }

    
    
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record CompanyHistory(boolean previouslyApplied, int applicationCount) {
        private static CompanyHistory none() {
            return new CompanyHistory(false, 0);
        }
    }
}
