package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.tracking.model.dto.ScrapeAndSaveResult;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.example.jobtracker.feature.tracking.model.entity.Board;
import com.example.jobtracker.feature.tracking.model.entity.BoardColumn;
import com.example.jobtracker.feature.tracking.model.entity.JobApplication;
import com.example.jobtracker.feature.tracking.repository.BoardColumnRepository;
import com.example.jobtracker.feature.tracking.repository.BoardRepository;
import com.example.jobtracker.feature.tracking.repository.JobApplicationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobApplicationServiceDuplicateRefreshTest {
    @Test
    void saveScrapedListing_updatesExistingDuplicateWithNewScrapedFields() {
        JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        BoardColumnRepository boardColumnRepository = mock(BoardColumnRepository.class);
        BoardService boardService = mock(BoardService.class);
        JobApplicationService service = new JobApplicationService(
                jobApplicationRepository,
                boardRepository,
                boardColumnRepository,
                boardService
        );

        User user = new User("demo", "hash", "ROLE_USER");
        Board board = new Board("Job Hunt", user);
        BoardColumn applied = new BoardColumn("Applied", 200, board);

        JobApplication existing = new JobApplication("Old Company", "Old Title", 0, applied);
        existing.setLocation("Old Location");
        existing.setSalary("Old Salary");
        existing.setJobUrl("https://example.com/job/123");

        ScrapedJobListingDto listing = new ScrapedJobListingDto();
        listing.setTitle("Software Engineer I");
        listing.setCompany("JDA Careers");
        listing.setLocation("Dallas");
        listing.setSalary("Not listed");
        listing.setOriginalLink("https://example.com/job/123?utm_source=abc&ref=mail");
        listing.setSource("example.com");

        when(boardService.getOrCreateDefaultBoard(user)).thenReturn(board);
        when(boardColumnRepository.findByBoard(board)).thenReturn(List.of(applied));
        when(jobApplicationRepository.findFirstByColumn_Board_UserAndJobUrl(user, "https://example.com/job/123"))
                .thenReturn(Optional.of(existing));
        when(jobApplicationRepository.save(existing)).thenReturn(existing);

        ScrapeAndSaveResult response = service.saveScrapedListing(listing, user, true);

        assertThat(response.getSaveStatus()).isEqualTo("duplicate");
        assertThat(existing.getTitle()).isEqualTo("Software Engineer I");
        assertThat(existing.getCompany()).isEqualTo("JDA Careers");
        assertThat(existing.getLocation()).isEqualTo("Dallas");
        assertThat(existing.getSalary()).isEqualTo("Not listed");
        assertThat(existing.getJobUrl()).isEqualTo("https://example.com/job/123");
        verify(jobApplicationRepository).save(existing);
    }

    @Test
    void saveScrapedListing_matchesExistingLegacyTrackedUrlAndNormalizesIt() {
        JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        BoardColumnRepository boardColumnRepository = mock(BoardColumnRepository.class);
        BoardService boardService = mock(BoardService.class);
        JobApplicationService service = new JobApplicationService(
                jobApplicationRepository,
                boardRepository,
                boardColumnRepository,
                boardService
        );

        User user = new User("demo", "hash", "ROLE_USER");
        Board board = new Board("Job Hunt", user);
        BoardColumn applied = new BoardColumn("Applied", 200, board);

        String trackedUrl = "https://example.com/job/123?utm_source=abc&ref=mail";
        String normalizedUrl = "https://example.com/job/123";

        JobApplication existing = new JobApplication("Old Company", "Old Title", 0, applied);
        existing.setLocation("Old Location");
        existing.setSalary("Old Salary");
        existing.setJobUrl(trackedUrl);

        ScrapedJobListingDto listing = new ScrapedJobListingDto();
        listing.setTitle("Software Engineer I");
        listing.setCompany("JDA Careers");
        listing.setLocation("Dallas");
        listing.setSalary("Not listed");
        listing.setOriginalLink(trackedUrl);
        listing.setSource("example.com");

        when(boardService.getOrCreateDefaultBoard(user)).thenReturn(board);
        when(boardColumnRepository.findByBoard(board)).thenReturn(List.of(applied));
        when(jobApplicationRepository.findFirstByColumn_Board_UserAndJobUrl(user, normalizedUrl))
                .thenReturn(Optional.empty());
        when(jobApplicationRepository.findFirstByColumn_Board_UserAndJobUrl(user, trackedUrl))
                .thenReturn(Optional.of(existing));
        when(jobApplicationRepository.save(existing)).thenReturn(existing);

        ScrapeAndSaveResult response = service.saveScrapedListing(listing, user, true);

        assertThat(response.getSaveStatus()).isEqualTo("duplicate");
        assertThat(existing.getJobUrl()).isEqualTo(normalizedUrl);
        verify(jobApplicationRepository).save(existing);
    }

    @Test
    void saveScrapedListing_flagsWhenCompanyWasPreviouslyApplied() {
        JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        BoardColumnRepository boardColumnRepository = mock(BoardColumnRepository.class);
        BoardService boardService = mock(BoardService.class);
        JobApplicationService service = new JobApplicationService(
                jobApplicationRepository,
                boardRepository,
                boardColumnRepository,
                boardService
        );

        User user = new User("demo", "hash", "ROLE_USER");
        Board board = new Board("Job Hunt", user);
        BoardColumn applied = new BoardColumn("Applied", 200, board);
        BoardColumn rejected = new BoardColumn("Rejected", 500, board);

        JobApplication priorApplied = new JobApplication("JDA Careers", "Backend Engineer", 0, rejected);
        priorApplied.setJobUrl("https://example.com/job/older");

        ScrapedJobListingDto listing = new ScrapedJobListingDto();
        listing.setTitle("Software Engineer I");
        listing.setCompany("JDA Careers");
        listing.setLocation("Dallas");
        listing.setSalary("Not listed");
        listing.setOriginalLink("https://example.com/job/new");
        listing.setSource("example.com");

        JobApplication existing = new JobApplication("JDA Careers", "Current Listing", 0, applied);
        existing.setJobUrl("https://example.com/job/new");

        when(boardService.getOrCreateDefaultBoard(user)).thenReturn(board);
        when(boardColumnRepository.findByBoard(board)).thenReturn(List.of(applied));
        when(jobApplicationRepository.findByColumn_Board_UserOrderByCreatedAtDesc(user))
                .thenReturn(List.of(priorApplied));
        when(jobApplicationRepository.findFirstByColumn_Board_UserAndJobUrl(user, "https://example.com/job/new"))
                .thenReturn(Optional.of(existing));
        when(jobApplicationRepository.save(existing)).thenReturn(existing);

        ScrapeAndSaveResult response = service.saveScrapedListing(listing, user, true);

        assertThat(response.getSaveStatus()).isEqualTo("duplicate");
        assertThat(response.isCompanyPreviouslyApplied()).isTrue();
        assertThat(response.getPreviousApplicationCount()).isEqualTo(1);
    }

    @Test
    void saveScrapedListing_stripsHandshakeTrackingParametersFromLeverUrl() {
        JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        BoardColumnRepository boardColumnRepository = mock(BoardColumnRepository.class);
        BoardService boardService = mock(BoardService.class);
        JobApplicationService service = new JobApplicationService(
                jobApplicationRepository,
                boardRepository,
                boardColumnRepository,
                boardService
        );

        User user = new User("demo", "hash", "ROLE_USER");
        Board board = new Board("Job Hunt", user);
        BoardColumn applied = new BoardColumn("Applied", 200, board);

        String trackedUrl = "https://jobs.lever.co/hive/432e24aa-953f-4031-9ea7-75a00b183b70"
                + "?gh_src=Handshake&iisn=Handshake&iis=Handshake&src=Handshake&source=Handshake"
                + "&ref=Handshake&utm_medium=Handshake&referral=Handshake&utm_source=Handshake"
                + "&__jvst=Handshake&__jvsd=Handshake&sourceDetails=Handshake&trid=Handshake"
                + "&lever-source%5B%5D=Handshake&Source=Handshake&rb=Handshake"
                + "&jobBoardSource=Handshake&channel=Handshake&rcid=Handshake";
        String normalizedUrl = "https://jobs.lever.co/hive/432e24aa-953f-4031-9ea7-75a00b183b70";

        JobApplication existing = new JobApplication("Hive", "Backend Engineer", 0, applied);
        existing.setLocation("Remote");
        existing.setSalary("Not listed");
        existing.setJobUrl(normalizedUrl);

        ScrapedJobListingDto listing = new ScrapedJobListingDto();
        listing.setTitle("Backend Engineer");
        listing.setCompany("Hive");
        listing.setLocation("Remote");
        listing.setSalary("Not listed");
        listing.setOriginalLink(trackedUrl);
        listing.setSource("lever");

        when(boardService.getOrCreateDefaultBoard(user)).thenReturn(board);
        when(boardColumnRepository.findByBoard(board)).thenReturn(List.of(applied));
        when(jobApplicationRepository.findByColumn_Board_UserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(jobApplicationRepository.findFirstByColumn_Board_UserAndJobUrl(user, normalizedUrl))
                .thenReturn(Optional.of(existing));
        when(jobApplicationRepository.save(any(JobApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrapeAndSaveResult response = service.saveScrapedListing(listing, user, true);

        assertThat(response.getSaveStatus()).isEqualTo("duplicate");
        assertThat(listing.getOriginalLink()).isEqualTo(normalizedUrl);
        assertThat(existing.getJobUrl()).isEqualTo(normalizedUrl);
    }

    @Test
    void saveScrapedListing_stripsQueryParametersFromHandshakeUrl() {
        JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        BoardColumnRepository boardColumnRepository = mock(BoardColumnRepository.class);
        BoardService boardService = mock(BoardService.class);
        JobApplicationService service = new JobApplicationService(
                jobApplicationRepository,
                boardRepository,
                boardColumnRepository,
                boardService
        );

        User user = new User("demo", "hash", "ROLE_USER");
        Board board = new Board("Job Hunt", user);
        BoardColumn applied = new BoardColumn("Applied", 200, board);

        String trackedUrl = "https://tacomauw.joinhandshake.com/job-search/10921875"
                + "?per_page=25"
                + "&locationFilter=%7B%22label%22%3A%22Seattle%2C+WA%22%2C%22point%22%3A%2247.6062095%2C-122.3320708%22%7D"
                + "&page=1";
        String normalizedUrl = "https://tacomauw.joinhandshake.com/job-search/10921875";

        JobApplication existing = new JobApplication("UW Tacoma", "Handshake Job 10921875", 0, applied);
        existing.setLocation("Seattle, WA");
        existing.setSalary("Not listed");
        existing.setJobUrl(normalizedUrl);

        ScrapedJobListingDto listing = new ScrapedJobListingDto();
        listing.setTitle("Handshake Job 10921875");
        listing.setCompany("UW Tacoma");
        listing.setLocation("Seattle, WA");
        listing.setSalary("Not listed");
        listing.setOriginalLink(trackedUrl);
        listing.setSource("joinhandshake.com");

        when(boardService.getOrCreateDefaultBoard(user)).thenReturn(board);
        when(boardColumnRepository.findByBoard(board)).thenReturn(List.of(applied));
        when(jobApplicationRepository.findByColumn_Board_UserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(jobApplicationRepository.findFirstByColumn_Board_UserAndJobUrl(user, normalizedUrl))
                .thenReturn(Optional.of(existing));

        ScrapeAndSaveResult response = service.saveScrapedListing(listing, user, true);

        assertThat(response.getSaveStatus()).isEqualTo("duplicate");
        assertThat(listing.getOriginalLink()).isEqualTo(normalizedUrl);
        assertThat(existing.getJobUrl()).isEqualTo(normalizedUrl);
    }
}
