package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class JobListingScraperServiceGenericFallbackTest {
    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void parseFromGenericPage_extractsTitleLocationAndSalaryFromVisibleContent() {
        JobListingScraperService service = scraperService();
        URI uri = URI.create("https://jobs.exampleco.com/open-roles/123");
        String html = """
                <html>
                  <head><title>Software Engineer | Careers</title></head>
                  <body>
                    <h1>Software Engineer</h1>
                    <p>Location: Seattle, WA</p>
                    <p>Compensation: $120,000 - $140,000 / year</p>
                  </body>
                </html>
                """;

        ScrapedJobListingDto response = service.parseFromGenericPage(html, uri);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Software Engineer");
        assertThat(response.getCompany()).isEqualTo("Exampleco");
        assertThat(response.getLocation()).isEqualTo("Seattle, WA");
        assertThat(response.getSalary()).contains("$120,000");
        assertThat(response.getSource()).isEqualTo("jobs.exampleco.com");
    }

    @Test
    void parseFromGenericPage_extractsFieldsFromEmbeddedJsonScripts() {
        JobListingScraperService service = scraperService();
        URI uri = URI.create("https://careers.acme.com/jobs/42");
        String html = """
                <html>
                  <head>
                    <meta property="og:title" content="Backend Platform Engineer">
                  </head>
                  <body>
                    <script>
                      window.__INITIAL_STATE__ = {
                        "jobTitle":"Backend Platform Engineer",
                        "companyName":"Acme Labs",
                        "location":"San Francisco, CA",
                        "salaryRange":"$150,000 - $190,000 / year"
                      };
                    </script>
                  </body>
                </html>
                """;

        ScrapedJobListingDto response = service.parseFromGenericPage(html, uri);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Backend Platform Engineer");
        assertThat(response.getCompany()).isEqualTo("Acme Labs");
        assertThat(response.getLocation()).isEqualTo("San Francisco, CA");
        assertThat(response.getSalary()).contains("$150,000");
    }

    @Test
    void parseFromGenericPage_extractsCompanyFromTitleWhenMissingEverywhereElse() {
        JobListingScraperService service = scraperService();
        URI uri = URI.create("https://apply.randomjobs.net/opening/777");
        String html = """
                <html>
                  <body>
                    <h1>Data Engineer at OpenAI</h1>
                    <p>Based in San Francisco, CA</p>
                  </body>
                </html>
                """;

        ScrapedJobListingDto response = service.parseFromGenericPage(html, uri);

        assertThat(response).isNotNull();
        assertThat(response.getCompany()).isEqualTo("OpenAI");
        assertThat(response.getLocation()).isEqualTo("San Francisco, CA");
    }

    @Test
    void parseFromGenericPage_extractsAmazonStyleCountryStateCityAndUsdSalaryRange() {
        JobListingScraperService service = scraperService();
        URI uri = URI.create("https://www.amazon.jobs/en/jobs/3144341/software-development-engineer-amazon-leo-us");
        String html = """
                <html>
                  <head>
                    <meta property="og:title" content="Software Development Engineer - Amazon Leo (US)">
                    <meta property="og:site_name" content="Amazon.jobs">
                  </head>
                  <body>
                    <h1>Software Development Engineer - Amazon Leo (US)</h1>
                    <span class="jobGeoLocation">Redmond, WA</span>
                    <ul class="associations">
                      <li>USA, CA, Northridge - 122,600.00 - 170,000.00 USD annually</li>
                      <li>USA, WA, Redmond - 110,500.00 - 160,000.00 USD annually</li>
                    </ul>
                  </body>
                </html>
                """;

        ScrapedJobListingDto response = service.parseFromGenericPage(html, uri);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Software Development Engineer - Amazon Leo (US)");
        assertThat(response.getCompany()).isEqualTo("Amazon");
        assertThat(response.getLocation()).isEqualTo("Redmond, WA");
        assertThat(response.getSalary()).contains("110,500.00 - 160,000.00 USD annually");
        assertThat(response.getSalary()).doesNotContain("122,600.00");
    }

    @Test
    void parseFromGenericPage_prefersHumanReadableCompanyOverInternalCodeAndParsesSalaryWithNbsp() {
        JobListingScraperService service = scraperService();
        URI uri = URI.create("https://jobs.psegliny.com/LI/job/Bethpage-Associate-SAP-GRC-Analyst-NY-11804/1348904200/");
        String html = """
                <html>
                  <head>
                    <meta property="og:title" content="Associate SAP GRC Analyst">
                  </head>
                  <body>
                    <h1 id="job-title">Associate SAP GRC Analyst</h1>
                    <p id="job-company"><strong>Company:</strong><span>LIPAPRD</span></p>
                    <p><span><strong>PSEG Company</strong>: PSEG Long Island</span></p>
                    <p><span><strong>Salary Range</strong>: $&nbsp;67,200<img src="x" alt=""> - $&nbsp;106,400</span></p>
                    <p><span class="jobGeoLocation">Bethpage, NY, US</span></p>
                  </body>
                </html>
                """;

        ScrapedJobListingDto response = service.parseFromGenericPage(html, uri);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Associate SAP GRC Analyst");
        assertThat(response.getCompany()).isEqualTo("PSEG Long Island");
        assertThat(response.getLocation()).isEqualTo("Bethpage, NY");
        assertThat(response.getSalary()).contains("$ 67,200");
        assertThat(response.getSalary()).contains("106,400");
    }

    @Test
    void parseFromGenericPage_extractsShortSalaryRangeAndBasedInLocationWithCount() {
        JobListingScraperService service = scraperService();
        URI uri = URI.create("https://app.joinhandshake.com/stu/jobs/999999999");
        String html = """
                <html>
                  <head>
                    <title>Agentic AI Engineer at Tata Consultancy Services | Handshake</title>
                    <meta property="og:site_name" content="Tata Consultancy Services">
                  </head>
                  <body>
                    <h1>Agentic AI Engineer</h1>
                    <p>Tata Consultancy Services</p>
                    <p>At a glance</p>
                    <p>$71\u201388K/yr</p>
                    <p>Onsite, based in Santa Clara, CA, Cincinnati, OH, +2</p>
                    <p>Work in person from one of the locations</p>
                  </body>
                </html>
                """;

        ScrapedJobListingDto response = service.parseFromGenericPage(html, uri);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Agentic AI Engineer");
        assertThat(response.getCompany()).isEqualTo("Tata Consultancy Services");
        assertThat(response.getLocation()).isEqualTo("Santa Clara, CA, Cincinnati, OH, +2");
        assertThat(response.getSalary()).isEqualTo("$71\u201388K/yr");
    }
}
