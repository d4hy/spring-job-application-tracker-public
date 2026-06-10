package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobListingScraperServiceHandshakeTextScrapeTest {
    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void scrapeJobFromText_extractsHandshakeFieldsFromPastedPostingText() {
        JobListingScraperService service = scraperService();
        String text = """
                Nextstep logo
                Nextstep
                Management Consulting
                Software Engineer
                Posted 3 days ago. Apply by March 22, 2026 at 8:59 AM

                At a glance
                Paid
                Remote, based in United States
                Work from home
                Job
                Full-time
                Job description
                SOFTWARE ENGINEER (FULL-TIME)
                """;

        ScrapedJobListingDto response = service.scrapeJobFromText(text, null);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Software Engineer");
        assertThat(response.getCompany()).isEqualTo("Nextstep");
        assertThat(response.getLocation()).isEqualTo("Remote (United States)");
        assertThat(response.getSalary()).isEqualTo("Not listed");
        assertThat(response.getSource()).isEqualTo("joinhandshake.com");
        assertThat(response.getOriginalLink()).isNull();
    }

    @Test
    void scrapeJobFromText_usesHandshakeUrlForSourceAndOriginalLink() {
        JobListingScraperService service = scraperService();
        String text = """
                Software Engineer at Blue Yonder | Handshake
                At a glance
                Remote, based in United States
                """;

        ScrapedJobListingDto response = service.scrapeJobFromText(
                text,
                "https://app.joinhandshake.com/stu/jobs/1234567890"
        );

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Software Engineer");
        assertThat(response.getCompany()).isEqualTo("Blue Yonder");
        assertThat(response.getLocation()).isEqualTo("Remote (United States)");
        assertThat(response.getSource()).isEqualTo("app.joinhandshake.com");
        assertThat(response.getOriginalLink()).isEqualTo("https://app.joinhandshake.com/stu/jobs/1234567890");
    }

    @Test
    void scrapeJobFromText_extractsShortSalaryRangeAndBasedInLocationWithCount() {
        JobListingScraperService service = scraperService();
        String text = """
                Tata Consultancy Services logo
                Tata Consultancy Services
                Internet & Software
                Agentic AI Engineer
                Posted 1 month ago. Apply by March 27, 2026 at 8:59 PM
                At a glance
                $71–88K/yr
                Onsite, based in Santa Clara, CA, Cincinnati, OH, +2
                Work in person from one of the locations
                Job
                Full-time
                Job description
                """;

        ScrapedJobListingDto response = service.scrapeJobFromText(text, null);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Agentic AI Engineer");
        assertThat(response.getCompany()).isEqualTo("Tata Consultancy Services");
        assertThat(response.getLocation()).isEqualTo("Santa Clara, CA, Cincinnati, OH, +2");
        assertThat(response.getSalary()).isEqualTo("$71–88K/yr");
        assertThat(response.getSource()).isEqualTo("joinhandshake.com");
    }

    @Test
    void scrapeJobFromText_convertsHourlyRateToAnnualSalary() {
        JobListingScraperService service = scraperService();
        String text = """
                Software Engineer at Acme | Handshake
                At a glance
                $35/hr
                Remote, based in United States
                Job
                Full-time
                """;

        ScrapedJobListingDto response = service.scrapeJobFromText(text, null);

        assertThat(response).isNotNull();
        assertThat(response.getSalary()).isEqualTo("$72,800 / year");
    }

    @Test
    void scrapeJobFromText_convertsHourlyRangeToAnnualSalaryRange() {
        JobListingScraperService service = scraperService();
        String text = """
                Software Engineer at Acme | Handshake
                At a glance
                $25 - $30 per hour
                Remote, based in United States
                Job
                Full-time
                """;

        ScrapedJobListingDto response = service.scrapeJobFromText(text, null);

        assertThat(response).isNotNull();
        assertThat(response.getSalary()).isEqualTo("$52,000 - $62,400 / year");
    }

    @Test
    void scrapeJobFromText_rejectsBlankText() {
        JobListingScraperService service = scraperService();

        assertThatThrownBy(() -> service.scrapeJobFromText("   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text is required");
    }

    @Test
    void scrapeJobFromText_extractsWellfoundStyleLabeledFields() {
        JobListingScraperService service = scraperService();
        String text = """
                Job Title: Frontend Engineer
                Company: Acme Labs
                Location: New York, NY
                Salary Range: $140,000 - $180,000 / year
                Apply privately on Wellfound.
                """;

        ScrapedJobListingDto response = service.scrapeJobFromText(
                text,
                "https://wellfound.com/jobs/4079283-frontend-engineer"
        );

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Frontend Engineer");
        assertThat(response.getCompany()).isEqualTo("Acme Labs");
        assertThat(response.getLocation()).isEqualTo("New York, NY");
        assertThat(response.getSalary()).contains("$140,000");
        assertThat(response.getSource()).isEqualTo("wellfound");
        assertThat(response.getOriginalLink()).isEqualTo("https://wellfound.com/jobs/4079283-frontend-engineer");
    }

    @Test
    void scrapeJobFromText_extractsCompanyWhenLineAppearsAfterTitle() {
        JobListingScraperService service = scraperService();
        String text = """
                Frontend Engineer
                Acme Labs
                Remote (US)
                $120,000 - $150,000
                """;

        ScrapedJobListingDto response = service.scrapeJobFromText(
                text,
                "https://wellfound.com/jobs/4079283-frontend-engineer"
        );

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Frontend Engineer");
        assertThat(response.getCompany()).isEqualTo("Acme Labs");
        assertThat(response.getLocation()).isEqualTo("Remote (US)");
        assertThat(response.getSalary()).contains("$120,000");
        assertThat(response.getSource()).isEqualTo("wellfound");
    }

    @Test
    void scrapeJobFromText_extractsWellfoundCompanyFromAboutSectionWhenPageHasSearchNoise() {
        JobListingScraperService service = scraperService();
        String text = """
                Open to offers
                Avatar for David Hoang
                Home
                Profile
                Jobs
                Applied
                Messages
                Discover
                Saved Search 1
                Software Engineer - Frontend Engineer
                Filters
                118 results
                Sort by:
                Recommended

                Avatar for TrickCV
                TrickCV
                Actively Hiring
                Software Engineer
                $80k - $130k
                Remote (Everywhere)
                Full Time

                About the job
                About TrickCV
                We're building AI tools that help job seekers land more interviews.

                About the company
                TrickCV company logo
                TrickCV
                Actively Hiring
                """;

        ScrapedJobListingDto response = service.scrapeJobFromText(
                text,
                "https://wellfound.com/jobs/4079283-software-engineer"
        );

        assertThat(response).isNotNull();
        assertThat(response.getCompany()).isEqualTo("TrickCV");
        assertThat(response.getSource()).isEqualTo("wellfound");
        assertThat(response.getOriginalLink()).isEqualTo("https://wellfound.com/jobs/4079283-software-engineer");
    }
}
