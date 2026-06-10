package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class JobListingScraperServiceUptimeCrewFallbackTest {
    private static final URI UPTIME_JOB_URI = URI.create("https://jobs.uptimecrew.com/jobs/a11Jw000005ngcjIAA");

    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void parseFromUptimeCrewApi_extractsCoreFieldsFromApiPayload() {
        JobListingScraperService service = scraperService();
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode payload = mapper.createObjectNode();
        payload.put("Job_Title__c", "Entry Level AI Software Engineer");
        payload.put("Publishing_Entity__c", "UPTIMECREW");
        payload.put("Year_1_Salary__c", 110000);

        ObjectNode relatedLocations = payload.putObject("Job_Locations__r");
        ArrayNode records = relatedLocations.putArray("records");

        ObjectNode first = records.addObject();
        first.putObject("Site_Location__r")
                .putObject("Address__c")
                .put("city", "Atlanta")
                .put("stateCode", "GA");

        ObjectNode second = records.addObject();
        second.putObject("Site_Location__r")
                .putObject("Address__c")
                .put("city", "New York")
                .put("stateCode", "NY");

        ScrapedJobListingDto response = service.parseFromUptimeCrewApi(payload, UPTIME_JOB_URI);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Entry Level AI Software Engineer");
        assertThat(response.getCompany()).isEqualTo("Uptime Crew");
        assertThat(response.getLocation()).isEqualTo("Atlanta, GA +1 more");
        assertThat(response.getSalary()).isEqualTo("$110,000 / YEAR");
        assertThat(response.getOriginalLink()).isEqualTo(UPTIME_JOB_URI.toString());
        assertThat(response.getSource()).isEqualTo("jobs.uptimecrew.com");
    }

    @Test
    void parseFromUptimeCrewApi_fallsBackToTopLevelLocationWhenLocationRecordsMissing() {
        JobListingScraperService service = scraperService();
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode payload = mapper.createObjectNode();
        payload.put("Job_Title__c", "Entry Level AI Software Engineer");
        payload.put("Publishing_Entity__c", "UPTIMECREW");
        payload.put("Job_Location__c", "Onsite");

        ScrapedJobListingDto response = service.parseFromUptimeCrewApi(payload, UPTIME_JOB_URI);

        assertThat(response).isNotNull();
        assertThat(response.getLocation()).isEqualTo("Onsite");
    }

    @Test
    void parseFromUptimeCrewApi_extractsDetailedLocationsFromJobDetailsJsonWhenTopLevelIsGeneric() {
        JobListingScraperService service = scraperService();
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode payload = mapper.createObjectNode();
        payload.put("Job_Title__c", "Entry Level AI Software Engineer");
        payload.put("Publishing_Entity__c", "UPTIMECREW");
        payload.put("Job_Location__c", "Onsite");
        payload.put(
                "Job_Details_JSON__c",
                "{\"sections\":[[{\"contentType\":\"LIST\",\"title\":\"Client Locations\",\"contents\":[\"Mountain View, CA\",\"San Diego, CA\",\"Atlanta, GA\"]}]]}"
        );

        ScrapedJobListingDto response = service.parseFromUptimeCrewApi(payload, UPTIME_JOB_URI);

        assertThat(response).isNotNull();
        assertThat(response.getLocation()).isEqualTo("Mountain View, CA +2 more");
    }

    @Test
    void parseFromUptimeCrewApi_fallsBackToHostBasedCompanyWhenPublishingEntityMissing() {
        JobListingScraperService service = scraperService();
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode payload = mapper.createObjectNode();
        payload.put("Job_Title__c", "Entry Level AI Software Engineer");
        payload.put("Job_Location__c", "Seattle, WA");

        ScrapedJobListingDto response = service.parseFromUptimeCrewApi(payload, UPTIME_JOB_URI);

        assertThat(response).isNotNull();
        assertThat(response.getCompany()).isEqualTo("Uptime Crew");
    }

    @Test
    void shouldRejectUptimeCrewShellScrape_rejectsGenericShellMetadata() {
        JobListingScraperService service = scraperService();
        ScrapedJobListingDto shell = new ScrapedJobListingDto();
        shell.setTitle("UPTIME CREW");
        shell.setCompany("UPTIME CREW");
        shell.setLocation("Not found");

        boolean rejected = service.shouldRejectUptimeCrewShellScrape(shell, UPTIME_JOB_URI);

        assertThat(rejected).isTrue();
    }
}
