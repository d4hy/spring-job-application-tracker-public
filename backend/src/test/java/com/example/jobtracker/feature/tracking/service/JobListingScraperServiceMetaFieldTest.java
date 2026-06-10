package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class JobListingScraperServiceMetaFieldTest {
    private JobListingScraperService scraperService() {
        return new JobListingScraperService(new ObjectMapper(), new OfflineModeSupport(false));
    }

    @Test
    void extractMetaField_doesNotBleedAcrossMetaTags() throws Exception {
        String html = """
                <!DOCTYPE html>
                <html lang="en-US">
                <head>
                    <title></title>
                    <meta http-equiv="X-UA-Compatible" content="chrome=1;IE=EDGE"/>
                    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
                    <meta name="viewport" content="width=device-width">
                    <meta name="title" property="og:title">
                </head>
                </html>
                """;

        JobListingScraperService service = scraperService();
        Method method = JobListingScraperService.class.getDeclaredMethod("extractMetaField", String.class, String.class);
        method.setAccessible(true);

        String extracted = (String) method.invoke(service, html, "title");
        assertThat(extracted).isNull();
    }
}
