package com.example.jobtracker.feature.tracking.controller;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.tracking.model.entity.JobApplication;
import com.example.jobtracker.feature.tracking.service.JobApplicationService;
import com.example.jobtracker.feature.tracking.service.JobListingScraperService;
import com.example.jobtracker.feature.auth.service.UserService;
import com.example.jobtracker.feature.tracking.model.dto.CreateJobRequest;
import com.example.jobtracker.feature.tracking.model.dto.JobCardDto;
import com.example.jobtracker.feature.tracking.model.dto.UpdateJobRequest;
import com.example.jobtracker.feature.tracking.model.dto.UpdateJobStatusRequest;
import com.example.jobtracker.feature.tracking.model.dto.SavedJobSummaryDto;
import com.example.jobtracker.feature.tracking.model.dto.ScrapeAndSaveResult;
import com.example.jobtracker.feature.tracking.model.dto.ScrapeLinkRequest;
import com.example.jobtracker.feature.tracking.model.dto.ScrapeTextRequest;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for Job Application workflows.
 * Defines HTTP endpoints for this feature, resolves authenticated user context where
 * required, and delegates business orchestration to service-layer components.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobApplicationController {
    private final JobApplicationService jobApplicationService;
    private final JobListingScraperService jobListingScraperService;
    private final UserService userService;

    public JobApplicationController(JobApplicationService jobApplicationService,
                                    JobListingScraperService jobListingScraperService,
                                    UserService userService) {
        this.jobApplicationService = jobApplicationService;
        this.jobListingScraperService = jobListingScraperService;
        this.userService = userService;
    }

    
    
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        String username = authentication.getName();
        return userService.findByUsername(username);
    }

    /**
     * Converts a saved job entity into the API DTO used by create/update/status endpoints.
     * Keeping this mapping in one place prevents small payload-shape differences between
     * endpoints that all return the same kind of job card.
     */
    private JobCardDto toJobCardDto(JobApplication job) {
        JobCardDto jobDto = new JobCardDto(
                job.getId(),
                job.getCompany(),
                job.getTitle(),
                job.getOrderIndex(),
                job.getColumn().getId(),
                job.getCreatedAt()
        );
        jobDto.setLocation(job.getLocation());
        jobDto.setNotes(job.getNotes());
        jobDto.setSalary(job.getSalary());
        jobDto.setJobUrl(job.getJobUrl());
        return jobDto;
    }

    
    
    @PostMapping
    public ResponseEntity<?> createJobApplication(@RequestBody CreateJobRequest request) {
        try {
            
            User user = getCurrentUser();
            
            var job = jobApplicationService.createJobApplication(request, user);

            return ResponseEntity.ok(toJobCardDto(job));
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/scrape")
    public ResponseEntity<?> scrapeJobListings(@RequestParam("query") String query,
                                               @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Query is required"));
        }

        
        List<ScrapedJobListingDto> listings = jobListingScraperService.scrapeJobs(query, limit);
        return ResponseEntity.ok(listings);
    }

    
    
    @PostMapping("/scrape-link")
    public ResponseEntity<?> scrapeJobLink(@Valid @RequestBody ScrapeLinkRequest request) {
        try {
            ScrapedJobListingDto listing = jobListingScraperService.scrapeJobFromLink(request.getUrl());
            return ResponseEntity.ok(listing);
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    @PostMapping("/scrape-text")
    public ResponseEntity<?> scrapeJobText(@Valid @RequestBody ScrapeTextRequest request) {
        try {
            ScrapedJobListingDto listing = jobListingScraperService.scrapeJobFromText(
                    request.getText(),
                    request.getUrl()
            );
            return ResponseEntity.ok(listing);
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    @PostMapping("/scrape-text-and-save")
    public ResponseEntity<?> scrapeJobTextAndSave(@Valid @RequestBody ScrapeTextRequest request) {
        try {
            User user = getCurrentUser();
            ScrapedJobListingDto listing = jobListingScraperService.scrapeJobFromText(
                    request.getText(),
                    request.getUrl()
            );
            ScrapeAndSaveResult result = jobApplicationService.saveScrapedListing(
                    listing,
                    user,
                    request.isMarkApplied(),
                    request.getBoardName()
            );
            return ResponseEntity.ok(result);
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    @PostMapping("/scrape-link-and-save")
    public ResponseEntity<?> scrapeJobLinkAndSave(@Valid @RequestBody ScrapeLinkRequest request) {
        try {
            
            User user = getCurrentUser();
            ScrapedJobListingDto listing = jobListingScraperService.scrapeJobFromLink(request.getUrl());
            ScrapeAndSaveResult result = jobApplicationService.saveScrapedListing(
                    listing,
                    user,
                    request.isMarkApplied(),
                    request.getBoardName()
            );
            return ResponseEntity.ok(result);
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    
    
    @GetMapping("/saved")
    public ResponseEntity<List<SavedJobSummaryDto>> getSavedJobs() {
        
        User user = getCurrentUser();
        return ResponseEntity.ok(jobApplicationService.getSavedJobs(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateJobApplication(@PathVariable("id") Long id,
                                                  @RequestParam(value = "statusLaneId", required = false) String statusLaneId,
                                                  @RequestParam(value = "order", required = false) Integer order,
                                                  @RequestBody(required = false) UpdateJobRequest updateRequest) {
        try {
            
            User user = getCurrentUser();
            
            var job = jobApplicationService.updateJobApplication(id, statusLaneId, order, updateRequest, user);

            return ResponseEntity.ok(toJobCardDto(job));
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/{id}/set-applied")
    public ResponseEntity<?> setApplied(@PathVariable("id") Long id) {
        try {
            
            User user = getCurrentUser();
            
            var job = jobApplicationService.setApplied(id, user);

            return ResponseEntity.ok(toJobCardDto(job));
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/{id}/set-status")
    public ResponseEntity<?> setStatus(@PathVariable("id") Long id,
                                       @Valid @RequestBody UpdateJobStatusRequest request) {
        try {
            
            User user = getCurrentUser();
            var job = jobApplicationService.setStatus(id, request.getStatus(), user);

            return ResponseEntity.ok(toJobCardDto(job));
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJobApplication(@PathVariable("id") Long id) {
        try {
            
            User user = getCurrentUser();
            
            jobApplicationService.deleteJobApplication(id, user);
            return ResponseEntity.ok(new SuccessResponse("Job deleted"));
        
        
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Simple API error payload used by the Job Application endpoints.
     * Provides a stable `message` payload shape so frontend code can display failures
     * consistently across validation, authorization, and domain-rule errors.
     */
    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        
        
        public String getMessage() {
            return message;
        }
    }

    /**
     * Simple API success payload used by the Job Application endpoints.
     * Wraps user-facing confirmation messages in a predictable payload structure for
     * operations that do not need to return a full resource body.
     */
    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        
        
        public String getMessage() {
            return message;
        }
    }
}
