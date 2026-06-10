package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobListingScraperServiceWellfoundFallbackTest {
    private static final URI WELLFOUND_JOB_URI = URI.create(
            "https://wellfound.com/jobs/3324973-software-engineer-full-stack"
    );
    private static final String WELLFOUND_USER_URL = "https://wellfound.com/jobs/4079283-frontend-engineer";
    private static final URI WELLFOUND_COMPANY_JOB_URI = URI.create(
            "https://wellfound.com/company/blue-yonder/jobs/3324973-software-engineer-full-stack"
    );

    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void applyWellfoundUriFallback_extractsTitleAndCanonicalSourceFromJobSlug() {
        JobListingScraperService service = scraperService();

        ScrapedJobListingDto response = service.applyWellfoundUriFallback(null, WELLFOUND_JOB_URI);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Software Engineer Full Stack");
        assertThat(response.getCompany()).isNull();
        assertThat(response.getSource()).isEqualTo("wellfound");
        assertThat(response.getOriginalLink()).isEqualTo(WELLFOUND_JOB_URI.toString());
    }

    @Test
    void applyWellfoundUriFallback_extractsCompanyFromCompanySlugAndReplacesNoiseValues() {
        JobListingScraperService service = scraperService();
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("wellfound.com");
        parsed.setCompany("Wellfound");
        parsed.setSource("wellfound.com");

        ScrapedJobListingDto response = service.applyWellfoundUriFallback(parsed, WELLFOUND_COMPANY_JOB_URI);

        assertThat(response).isSameAs(parsed);
        assertThat(response.getTitle()).isEqualTo("Software Engineer Full Stack");
        assertThat(response.getCompany()).isEqualTo("Blue Yonder");
        assertThat(response.getSource()).isEqualTo("wellfound");
        assertThat(response.getOriginalLink()).isEqualTo(WELLFOUND_COMPANY_JOB_URI.toString());
    }

    @Test
    void applyWellfoundUriFallback_ignoresNonWellfoundLinks() {
        JobListingScraperService service = scraperService();
        URI nonWellfoundUri = URI.create("https://www.linkedin.com/jobs/view/1234567890");
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("Existing Title");
        parsed.setCompany("Existing Company");

        ScrapedJobListingDto response = service.applyWellfoundUriFallback(parsed, nonWellfoundUri);

        assertThat(response).isSameAs(parsed);
        assertThat(response.getTitle()).isEqualTo("Existing Title");
        assertThat(response.getCompany()).isEqualTo("Existing Company");
    }

    @Test
    void shouldRejectWellfoundShellScrape_returnsTrueWhenOnlyTitleCanBeDerived() {
        JobListingScraperService service = scraperService();

        ScrapedJobListingDto fallback = service.applyWellfoundUriFallback(null, URI.create(WELLFOUND_USER_URL));
        boolean rejected = service.shouldRejectWellfoundShellScrape(fallback, URI.create(WELLFOUND_USER_URL));

        assertThat(fallback).isNotNull();
        assertThat(fallback.getTitle()).isEqualTo("Frontend Engineer");
        assertThat(rejected).isTrue();
    }

    @Test
    void shouldRejectWellfoundShellScrape_returnsFalseWhenCompanyIsPresent() {
        JobListingScraperService service = scraperService();
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("Frontend Engineer");
        parsed.setCompany("Blue Yonder");

        boolean rejected = service.shouldRejectWellfoundShellScrape(parsed, URI.create(WELLFOUND_USER_URL));

        assertThat(rejected).isFalse();
    }

    @Test
    void scrapeJobFromLink_wellfoundBlockedLinkThrowsHelpfulGuidance() {
        JobListingScraperService service = scraperService();

        assertThatThrownBy(() -> service.scrapeJobFromLink(WELLFOUND_USER_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wellfound is blocking server-side scraping");
    }
}
