package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class JobListingScraperServiceWorkdayFallbackTest {
    private static final URI WORKDAY_URI = URI.create(
            "https://jda.wd5.myworkdayjobs.com/en-US/JDA_Careers/job/BYDS-Dallas/Software-Engineer-I_253929"
    );

    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void applyWorkdayUriFallback_populatesMissingFieldsFromUri() {
        JobListingScraperService service = scraperService();

        ScrapedJobListingDto response = service.applyWorkdayUriFallback(null, WORKDAY_URI);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Software Engineer I");
        assertThat(response.getCompany()).isEqualTo("JDA Careers");
        assertThat(response.getLocation()).isEqualTo("Dallas");
        assertThat(response.getOriginalLink()).isEqualTo(WORKDAY_URI.toString());
        assertThat(response.getSource()).isEqualTo("jda.wd5.myworkdayjobs.com");
    }

    @Test
    void applyWorkdayUriFallback_keepsExistingParsedValues() {
        JobListingScraperService service = scraperService();
        ScrapedJobListingDto parsed = new ScrapedJobListingDto();
        parsed.setTitle("Existing Title");
        parsed.setCompany("Existing Company");
        parsed.setLocation("Existing Location");
        parsed.setSource("existing.source");
        parsed.setOriginalLink("https://example.com/job");

        ScrapedJobListingDto response = service.applyWorkdayUriFallback(parsed, WORKDAY_URI);

        assertThat(response).isSameAs(parsed);
        assertThat(response.getTitle()).isEqualTo("Existing Title");
        assertThat(response.getCompany()).isEqualTo("Existing Company");
        assertThat(response.getLocation()).isEqualTo("Existing Location");
        assertThat(response.getSource()).isEqualTo("existing.source");
        assertThat(response.getOriginalLink()).isEqualTo("https://example.com/job");
    }

    @Test
    void applyWorkdayUriFallback_ignoresNonWorkdayLinks() {
        JobListingScraperService service = scraperService();
        URI nonWorkdayUri = URI.create("https://www.linkedin.com/jobs/view/1234567890");

        ScrapedJobListingDto response = service.applyWorkdayUriFallback(null, nonWorkdayUri);

        assertThat(response).isNull();
    }
}
