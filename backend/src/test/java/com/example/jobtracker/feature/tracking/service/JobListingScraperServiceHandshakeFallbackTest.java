package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class JobListingScraperServiceHandshakeFallbackTest {
    private static final URI HANDSHAKE_JOB_URI = URI.create("https://app.joinhandshake.com/stu/jobs/1234567890");
    private static final URI HANDSHAKE_JOB_SEARCH_URI = URI.create("https://tacomauw.joinhandshake.com/job-search/10539768?page=1&per_page=25");
    private static final URI HANDSHAKE_EMPLOYER_URI = URI.create("https://app.joinhandshake.com/employers/blue-yonder-123456");

    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void applyHandshakeUriFallback_extractsTitleAndCompanyFromHandshakeTitle() {
        JobListingScraperService service = scraperService();
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("Software Engineer at Blue Yonder | Handshake");
        parsed.setCompany("Handshake");

        ScrapedJobListingDto response = service.applyHandshakeUriFallback(parsed, HANDSHAKE_JOB_URI);

        assertThat(response).isSameAs(parsed);
        assertThat(response.getTitle()).isEqualTo("Software Engineer");
        assertThat(response.getCompany()).isEqualTo("Blue Yonder");
        assertThat(response.getSource()).isEqualTo("app.joinhandshake.com");
        assertThat(response.getOriginalLink()).isEqualTo(HANDSHAKE_JOB_URI.toString());
    }

    @Test
    void applyHandshakeUriFallback_canInferCompanyFromEmployerSlug() {
        JobListingScraperService service = scraperService();

        ScrapedJobListingDto response = service.applyHandshakeUriFallback(null, HANDSHAKE_EMPLOYER_URI);

        assertThat(response).isNotNull();
        assertThat(response.getCompany()).isEqualTo("Blue Yonder");
        assertThat(response.getSource()).isEqualTo("app.joinhandshake.com");
        assertThat(response.getOriginalLink()).isEqualTo(HANDSHAKE_EMPLOYER_URI.toString());
    }

    @Test
    void applyHandshakeUriFallback_canInferPlaceholderTitleFromJobSearchId() {
        JobListingScraperService service = scraperService();

        ScrapedJobListingDto response = service.applyHandshakeUriFallback(null, HANDSHAKE_JOB_SEARCH_URI);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Handshake Job 10539768");
        assertThat(response.getSource()).isEqualTo("tacomauw.joinhandshake.com");
        assertThat(response.getOriginalLink()).isEqualTo(HANDSHAKE_JOB_SEARCH_URI.toString());
    }

    @Test
    void applyHandshakeUriFallback_ignoresNonHandshakeLinks() {
        JobListingScraperService service = scraperService();
        URI nonHandshakeUri = URI.create("https://www.linkedin.com/jobs/view/1234567890");
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("Existing Title");
        parsed.setCompany("Existing Company");

        ScrapedJobListingDto response = service.applyHandshakeUriFallback(parsed, nonHandshakeUri);

        assertThat(response).isSameAs(parsed);
        assertThat(response.getTitle()).isEqualTo("Existing Title");
        assertThat(response.getCompany()).isEqualTo("Existing Company");
    }

    @Test
    void shouldRejectHandshakeJobScrape_returnsTrueWhenHandshakeJobLinkHasNoTitle() {
        JobListingScraperService service = scraperService();
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("Handshake");
        parsed.setCompany("Not found");
        parsed.setLocation("Not found");

        boolean result = service.shouldRejectHandshakeJobScrape(parsed, HANDSHAKE_JOB_SEARCH_URI);

        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectHandshakeJobScrape_returnsFalseWhenHandshakeTitleWasExtracted() {
        JobListingScraperService service = scraperService();
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("Software Engineer at Blue Yonder | Handshake");
        parsed.setCompany("Handshake");

        ScrapedJobListingDto normalized = service.applyHandshakeUriFallback(parsed, HANDSHAKE_JOB_URI);
        boolean result = service.shouldRejectHandshakeJobScrape(normalized, HANDSHAKE_JOB_URI);

        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectHandshakeJobScrape_returnsFalseForHandshakeEmployerPage() {
        JobListingScraperService service = scraperService();
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setCompany("Blue Yonder");

        boolean result = service.shouldRejectHandshakeJobScrape(parsed, HANDSHAKE_EMPLOYER_URI);

        assertThat(result).isFalse();
    }
}
