package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class JobListingScraperServiceSourceNormalizationTest {
    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void normalizeSourceForInputUri_forSimplifyInputForcesCanonicalSource() {
        JobListingScraperService service = scraperService();
        URI simplifyUri = URI.create(
                "https://simplify.jobs/p/5711bd5c-516c-44be-b81a-58c2679c41bc/Software-Engineer-1"
        );

        String normalized = service.normalizeSourceForInputUri("smx", simplifyUri);

        assertThat(normalized).isEqualTo("simplify");
    }

    @Test
    void normalizeOriginalLinkForInputUri_forSimplifyInputPreservesOriginalLink() {
        JobListingScraperService service = scraperService();
        URI simplifyUri = URI.create(
                "https://simplify.jobs/p/5711bd5c-516c-44be-b81a-58c2679c41bc/Software-Engineer-1"
        );

        String normalized = service.normalizeOriginalLinkForInputUri("https://smx.jobs/foo", simplifyUri);

        assertThat(normalized).isEqualTo(simplifyUri.toString());
    }

    @Test
    void normalizeSourceForInputUri_keepsExistingNonSimplifySource() {
        JobListingScraperService service = scraperService();
        URI workdayUri = URI.create("https://acme.wd5.myworkdayjobs.com/en-US/careers/job/123");

        String normalized = service.normalizeSourceForInputUri("acme-tracking", workdayUri);

        assertThat(normalized).isEqualTo("acme-tracking");
    }

    @Test
    void normalizeSourceForInputUri_forWellfoundInputForcesCanonicalSource() {
        JobListingScraperService service = scraperService();
        URI wellfoundUri = URI.create("https://wellfound.com/jobs/3324973-software-engineer");

        String normalized = service.normalizeSourceForInputUri("wellfound.com", wellfoundUri);

        assertThat(normalized).isEqualTo("wellfound");
    }

    @Test
    void normalizeOriginalLinkForInputUri_forWellfoundInputPreservesOriginalLink() {
        JobListingScraperService service = scraperService();
        URI wellfoundUri = URI.create("https://wellfound.com/company/blue-yonder/jobs/3324973-software-engineer");

        String normalized = service.normalizeOriginalLinkForInputUri("https://wellfound.com/jobs/3324973-software-engineer", wellfoundUri);

        assertThat(normalized).isEqualTo(wellfoundUri.toString());
    }
}
