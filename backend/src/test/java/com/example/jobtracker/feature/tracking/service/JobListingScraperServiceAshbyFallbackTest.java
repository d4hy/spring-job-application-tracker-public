package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class JobListingScraperServiceAshbyFallbackTest {
    private static final URI ASHBY_URI = URI.create("https://jobs.ashbyhq.com/openai/test-job");

    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void parseFromAshbyPage_extractsFieldsFromAppDataPosting() throws Exception {
        JobListingScraperService service = scraperService();
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode root = mapper.createObjectNode();
        root.putObject("organization").put("name", "OpenAI");

        ObjectNode posting = root.putObject("posting");
        posting.put("title", "Software Engineer, Real Time");
        posting.put("locationName", "Seattle");
        posting.put("scrapeableCompensationSalarySummary", "$185K - $385K");
        posting.put("isRemote", false);

        String appDataJson = mapper.writeValueAsString(root);
        String html = """
                <html><head>
                <meta property="og:url" content="https://jobs.ashbyhq.com/openai/test-job" />
                </head><body>
                <script>window.__appData = %s;</script>
                </body></html>
                """.formatted(appDataJson);

        ScrapedJobListingDto response = service.parseFromAshbyPage(html, ASHBY_URI);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Software Engineer, Real Time");
        assertThat(response.getCompany()).isEqualTo("OpenAI");
        assertThat(response.getLocation()).isEqualTo("Seattle");
        assertThat(response.getSalary()).isEqualTo("$185K - $385K");
        assertThat(response.getSource()).isEqualTo("jobs.ashbyhq.com");
        assertThat(response.getOriginalLink()).isEqualTo("https://jobs.ashbyhq.com/openai/test-job");
    }

    @Test
    void parseFromAshbyPage_setsRemoteWhenLocationMissingAndIsRemoteTrue() throws Exception {
        JobListingScraperService service = scraperService();
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode root = mapper.createObjectNode();
        root.putObject("organization").put("name", "OpenAI");

        ObjectNode posting = root.putObject("posting");
        posting.put("title", "Backend Engineer");
        posting.put("isRemote", true);

        String appDataJson = mapper.writeValueAsString(root);
        String html = """
                <html><head></head><body>
                <script>window.__appData = %s;</script>
                </body></html>
                """.formatted(appDataJson);

        ScrapedJobListingDto response = service.parseFromAshbyPage(html, ASHBY_URI);

        assertThat(response).isNotNull();
        assertThat(response.getLocation()).isEqualTo("Remote");
    }

    @Test
    void mergeWithAshbyPreference_prefersAshbyLocationForAshbyHosts() {
        JobListingScraperService service = scraperService();

        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("Software Engineer, Real Time");
        parsed.setCompany("OpenAI");
        parsed.setLocation("CA, US");
        parsed.setSalary("USD 185000 - 385000 / YEAR");

        ScrapedJobListingDto ashbyParsed = new ScrapedJobListingDto();
        ashbyParsed.setTitle("Software Engineer, Real Time");
        ashbyParsed.setCompany("OpenAI");
        ashbyParsed.setLocation("Seattle");
        ashbyParsed.setSalary("$185K - $385K");

        ScrapedJobListingDto merged = service.mergeWithAshbyPreference(parsed, ashbyParsed, ASHBY_URI);

        assertThat(merged.getLocation()).isEqualTo("Seattle");
        assertThat(merged.getSalary()).isEqualTo("$185K - $385K");
    }
}
