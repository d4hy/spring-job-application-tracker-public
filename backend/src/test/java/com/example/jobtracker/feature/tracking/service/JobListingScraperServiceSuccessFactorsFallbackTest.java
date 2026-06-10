package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class JobListingScraperServiceSuccessFactorsFallbackTest {
    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void parseFromGenericPage_extractsFieldsFromSuccessFactorsStyleMarkup() {
        JobListingScraperService service = scraperService();
        URI uri = URI.create("https://jobs.psegliny.com/LI/job/Bethpage-Associate-SAP-GRC-Analyst-NY-11804/1348904200/");
        String html = """
                <html>
                  <head>
                    <meta property="og:title" content="Associate SAP GRC Analyst">
                  </head>
                  <body>
                    <div class="jobDisplayShell" itemscope="itemscope" itemtype="http://schema.org/JobPosting">
                      <span itemprop="jobLocation" itemscope="itemscope" itemtype="http://schema.org/Place">
                        <span itemprop="address" itemscope="itemscope" itemtype="http://schema.org/PostalAddress">
                          <meta itemprop="addressLocality" content="Bethpage">
                          <meta itemprop="addressRegion" content="NY">
                          <meta itemprop="addressCountry" content="US">
                        </span>
                      </span>
                      <h1 id="job-title" itemprop="title">Associate SAP GRC Analyst</h1>
                      <p id="job-location"><strong>Location:</strong> <span class="jobGeoLocation">Bethpage, NY, US</span></p>
                      <p><strong>PSEG Company</strong>: PSEG Long Island</p>
                    </div>
                  </body>
                </html>
                """;

        ScrapedJobListingDto response = service.parseFromGenericPage(html, uri);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Associate SAP GRC Analyst");
        assertThat(response.getCompany()).isEqualTo("PSEG Long Island");
        assertThat(response.getLocation()).isEqualTo("Bethpage, NY");
        assertThat(response.getSource()).isEqualTo("jobs.psegliny.com");
        assertThat(response.getOriginalLink()).isEqualTo(uri.toString());
    }

    @Test
    void parseFromGenericPage_extractsLabelValueFallbacksForCompanyAndLocation() {
        JobListingScraperService service = scraperService();
        URI uri = URI.create("https://jobs.example.com/openings/42");
        String html = """
                <html>
                  <body>
                    <h1 itemprop="title">Platform Engineer</h1>
                    <p><strong>Employer</strong>: Example Labs</p>
                    <p><strong>Work Location:</strong> <span>Austin, TX, US</span></p>
                  </body>
                </html>
                """;

        ScrapedJobListingDto response = service.parseFromGenericPage(html, uri);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Platform Engineer");
        assertThat(response.getCompany()).isEqualTo("Example Labs");
        assertThat(response.getLocation()).isEqualTo("Austin, TX");
    }
}
