package com.example.jobtracker.feature.tracking.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.tracking.model.dto.ScrapedJobListingDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service-layer component for Job Listing Scraper workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class JobListingScraperService {
    private static final String REMOTIVE_SEARCH_URL = "https://remotive.com/api/remote-jobs?search=%s";
    private static final String ARBEITNOW_SEARCH_URL = "https://www.arbeitnow.com/api/job-board-api?search=%s";
    private static final String LINKEDIN_GUEST_JOB_URL = "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/%s";
    private static final String UPTIMECREW_API_BASE_URL = "https://704k2n7od3.execute-api.us-east-1.amazonaws.com/prod";
    private static final String UPTIMECREW_SCRAPE_FAILED_MESSAGE =
            "Could not scrape job details from this Uptime Crew link right now. "
                    + "Please retry in a moment or paste details manually.";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String RANGE_SEPARATOR_REGEX = "(?:-|to|\\u2013|\\u2014|\\u2212)";
    private static final String MONEY_SYMBOL_REGEX = "(?:US\\$|CA\\$|A\\$|C\\$|[$\\u20AC\\u00A3])";
    private static final String MONEY_AMOUNT_REGEX = "\\d[\\d,.]*(?:\\.\\d+)?\\s*(?:[kKmM])?";
    private static final String PAY_PERIOD_REGEX = "(?:hour|hr|year|yr|month|week)";
    private static final String CURRENCY_CODE_REGEX = "(?:USD|CAD|EUR|GBP|AUD|NZD)";
    private static final String PAY_CADENCE_REGEX =
            "(?:annually|yearly|monthly|weekly|hourly|per\\s+year|per\\s+month|per\\s+week|per\\s+hour|/\\s*(?:year|yr|month|week|hour|hr))";
    private static final int FULL_TIME_HOURS_PER_YEAR = 2080;
    private static final Pattern HOURLY_CADENCE_PATTERN = Pattern.compile(
            "(?i)(?:\\bhourly\\b|\\bper\\s+hour\\b|/\\s*(?:hour|hr)\\b)"
    );
    private static final Pattern HOURLY_AMOUNT_PATTERN = Pattern.compile(
            "(?i)(" + MONEY_SYMBOL_REGEX + ")?\\s*(" + MONEY_AMOUNT_REGEX + ")"
    );
    private static final Pattern CURRENCY_SYMBOL_PATTERN = Pattern.compile(MONEY_SYMBOL_REGEX, Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("(?i)\\b" + CURRENCY_CODE_REGEX + "\\b");
    private static final Pattern LINKEDIN_VIEW_ID_PATTERN = Pattern.compile("/jobs/view/(?:[^/]*-)?(\\d+)");
    private static final Pattern LINKEDIN_GENERIC_ID_PATTERN = Pattern.compile("(\\d{6,})");
    private static final Pattern LINKEDIN_COMPANY_LINK_PATTERN = Pattern.compile(
            "<a[^>]*href\\s*=\\s*['\\\"][^'\\\"]*/company/[^'\\\"]*['\\\"][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern LINKEDIN_PAY_RANGE_PATTERN = Pattern.compile(
            "(" + MONEY_SYMBOL_REGEX + "\\s?" + MONEY_AMOUNT_REGEX
                    + "(?:\\s*" + RANGE_SEPARATOR_REGEX + "\\s*(?:" + MONEY_SYMBOL_REGEX + "\\s?)?" + MONEY_AMOUNT_REGEX + ")?"
                    + "(?:\\s*(?:/|per)\\s*" + PAY_PERIOD_REGEX + ")?"
                    + ")",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JSON_LD_PATTERN = Pattern.compile(
            "<script[^>]*type\\s*=\\s*['\\\"]application/ld\\+json['\\\"][^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ASHBY_APP_DATA_PATTERN = Pattern.compile(
            "window\\.__appData\\s*=\\s*(\\{.*?\\})\\s*;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern META_TAG_PATTERN = Pattern.compile(
            "<meta\\s+[^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "([a-zA-Z_:.-]+)\\s*=\\s*(['\\\"])(.*?)\\2",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile(
            "<script(?![^>]*\\bsrc=)[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern H1_TAG_PATTERN = Pattern.compile(
            "<h1[^>]*>(.*?)</h1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern H2_TAG_PATTERN = Pattern.compile(
            "<h2[^>]*>(.*?)</h2>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern WORKDAY_DESCRIPTION_LOCATION_PATTERN = Pattern.compile(
            "Location\\s*:\\s*(.+?)(?:\\s+To\\s+qualify|\\s+About\\s+|\\.|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern GENERIC_TITLE_COMPANY_PATTERN = Pattern.compile(
            "^(.+?)\\s+at\\s+(.+?)(?:\\s*(?:\\||-|\\u2022)\\s*.*)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LOCATION_LABEL_PATTERN = Pattern.compile(
            "(?i)\\b(?:location|job\\s*location|work\\s*location)\\s*[:\\-]\\s*([A-Za-z][A-Za-z0-9 .,'/()&-]{2,90})"
    );
    private static final Pattern BASED_IN_LOCATION_PATTERN = Pattern.compile(
            "(?i)\\bbased\\s+in\\s+(.+?)(?=\\s*(?:\\||$|work\\s+from\\s+home\\b|work\\s+in\\s+person\\b|job\\b|full-time\\b|part-time\\b|apply\\b|job\\s+description\\b|what\\s+this\\s+job\\s+offers\\b))"
    );
    private static final Pattern CITY_STATE_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Za-z.'-]*(?:\\s+[A-Z][A-Za-z.'-]*){0,3},\\s*[A-Z]{2})\\b"
    );
    private static final Pattern COUNTRY_STATE_CITY_PATTERN = Pattern.compile(
            "(?i)\\b(?:us|usa|united\\s+states),\\s*([A-Z]{2}),\\s*([A-Za-z][A-Za-z0-9.'-]*(?:\\s+[A-Za-z][A-Za-z0-9.'-]*){0,3})\\b"
    );
    private static final Pattern REMOTE_HINT_PATTERN = Pattern.compile(
            "(?i)\\b(remote|hybrid|on\\s*-?site|onsite)\\b"
    );
    private static final Pattern ITEMPROP_TAG_PATTERN = Pattern.compile(
            "<([a-zA-Z0-9]+)[^>]*itemprop\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*>(.*?)</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern STRONG_LABEL_VALUE_PATTERN = Pattern.compile(
            "<(?:strong|b)[^>]*>\\s*([^<]{2,80})\\s*</(?:strong|b)>\\s*[:\\-]?\\s*(?:<[^>]+>\\s*)*([^<\\r\\n]{2,140})",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern HANDSHAKE_SUFFIX_PATTERN = Pattern.compile(
            "(?i)\\s*(?:\\||-)\\s*Handshake.*$"
    );
    private static final Pattern HANDSHAKE_REVIEW_SUFFIX_PATTERN = Pattern.compile(
            "(?i)\\s*:\\s*Read\\s+reviews\\s+and\\s+ask\\s+questions\\s*$"
    );
    private static final Pattern HANDSHAKE_TITLE_COMPANY_PATTERN = Pattern.compile(
            "^(.+?)\\s+at\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HANDSHAKE_TEXT_LOGO_LINE_PATTERN = Pattern.compile(
            "(?i)^(.+?)\\s+logo$"
    );
    private static final Pattern COMPANY_LOGO_LINE_PATTERN = Pattern.compile(
            "(?i)^(.+?)\\s+company\\s+logo$"
    );
    private static final Pattern ABOUT_LINE_PATTERN = Pattern.compile(
            "(?i)^about\\s+(.+)$"
    );
    private static final Pattern HANDSHAKE_TEXT_TITLE_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(engineer|developer|analyst|manager|intern|consultant|specialist|designer|scientist|architect|administrator|coordinator|officer|director|lead|technician|associate|product|marketing|operations|finance|accounting|recruiter)\\b"
    );
    private static final Pattern HANDSHAKE_INDUSTRY_LINE_PATTERN = Pattern.compile(
            "(?i)^(management consulting|software development|financial services|information technology and services|higher education|staffing and recruiting|hospital & health care|non-profit organization management)$"
    );
    
    private static final Pattern HANDSHAKE_EMPLOYER_ID_SUFFIX_PATTERN = Pattern.compile(
            "-\\d+$"
    );
    private static final Pattern WELLFOUND_JOB_ID_PREFIX_PATTERN = Pattern.compile(
            "^\\d+(?:[-_]+)?"
    );
    private static final Pattern WELLFOUND_SLUG_ID_SUFFIX_PATTERN = Pattern.compile(
            "-\\d+$"
    );
    private static final Pattern UPTIMECREW_JOB_ID_PATTERN = Pattern.compile(
            "(?i)/jobs/(?:senior/)?([a-z0-9]+)"
    );
    private static final Pattern HANDSHAKE_JOB_PATH_PATTERN = Pattern.compile(
            "(?i).*/(?:job-search/\\d+|(?:stu/)?jobs?/\\d+).*"
    );
    private static final Pattern HANDSHAKE_JOB_ID_PATH_PATTERN = Pattern.compile(
            "(?i).*/(?:job-search|(?:stu/)?jobs?)/(\\d+).*"
    );
    private static final Pattern SALARY_CODE_RANGE_PATTERN = Pattern.compile(
            "\\b(" + MONEY_AMOUNT_REGEX + "\\s*" + RANGE_SEPARATOR_REGEX + "\\s*" + MONEY_AMOUNT_REGEX + "\\s*"
                    + CURRENCY_CODE_REGEX
                    + "(?:\\s*" + PAY_CADENCE_REGEX + ")?)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GEO_SALARY_ROW_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:usa|us|united\\s+states)\\b\\s*,\\s*)?([A-Z]{2})\\s*,\\s*"
                    + "([A-Za-z][A-Za-z0-9.'-]*(?:\\s+[A-Za-z][A-Za-z0-9.'-]*){0,4})\\s*[-:]\\s*"
                    + "(" + MONEY_AMOUNT_REGEX + "\\s*" + RANGE_SEPARATOR_REGEX + "\\s*" + MONEY_AMOUNT_REGEX + "\\s*"
                    + CURRENCY_CODE_REGEX
                    + "(?:\\s*" + PAY_CADENCE_REGEX + ")?)"
    );
    private static final String HANDSHAKE_LOGIN_REQUIRED_MESSAGE =
            "Could not scrape this Handshake link. Handshake returned a login/challenge page, "
                    + "so job details are hidden from server-side scraping. Open the posting while signed in, "
                    + "copy the job details manually, then save with the original link.";
    private static final String WELLFOUND_BLOCKED_MESSAGE =
            "Could not scrape full details from this Wellfound link. "
                    + "Wellfound is blocking server-side scraping for this URL. "
                    + "Open the posting in your browser, copy the visible job text, then use Handshake Text to save it.";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OfflineModeSupport offlineModeSupport;

    public JobListingScraperService(ObjectMapper objectMapper, OfflineModeSupport offlineModeSupport) {
        this.objectMapper = objectMapper;
        this.offlineModeSupport = offlineModeSupport;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                
                .build();
    }

    
    
    public List<ScrapedJobListingDto> scrapeJobs(String query, int limit) {
        
        offlineModeSupport.requireOnline("Job scraping");

        int safeLimit = Math.max(1, Math.min(limit, 100));
        
        String safeQuery = query == null ? "" : query.trim();

        Map<String, ScrapedJobListingDto> deduped = new LinkedHashMap<>();
        mergeUnique(deduped, fetchFromRemotive(safeQuery));
        mergeUnique(deduped, fetchFromArbeitnow(safeQuery));

        return deduped.values().stream()
                .limit(safeLimit)
                
                .toList();
    }

    
    
    public ScrapedJobListingDto scrapeJobFromLink(String url) {
        
        offlineModeSupport.requireOnline("Job link scraping");

        if (isBlank(url)) {
            throw new IllegalArgumentException("url is required");
        }

        
        URI uri = normalizeHttpUri(url);

        ScrapedJobListingDto parsed = null;

        if (isUptimeCrewHost(uri.getHost())) {
            
            parsed = scrapeUptimeCrewJob(uri);
            if (parsed == null) {
                
                parsed = parseFromWebPage(uri);
            }
        } else if (isLinkedInHost(uri.getHost())) {
            
            parsed = scrapeLinkedInJob(uri);
            if (parsed == null) {
                
                parsed = parseFromWebPage(uri);
            }
        } else if (isWellfoundHost(uri.getHost())) {
            
            
            parsed = applyWellfoundUriFallback(null, uri);
        } else {
            
            parsed = parseFromWebPage(uri);
        }

        
        parsed = applyWorkdayUriFallback(parsed, uri);
        
        parsed = applyHandshakeUriFallback(parsed, uri);
        
        parsed = applyWellfoundUriFallback(parsed, uri);
        
        parsed = sanitizeScrapedListing(parsed);
        
        parsed = applyWorkdayUriFallback(parsed, uri);
        
        parsed = applyHandshakeUriFallback(parsed, uri);
        
        parsed = applyWellfoundUriFallback(parsed, uri);

        if (shouldRejectHandshakeJobScrape(parsed, uri)) {
            throw new IllegalArgumentException(HANDSHAKE_LOGIN_REQUIRED_MESSAGE);
        }
        if (shouldRejectUptimeCrewShellScrape(parsed, uri)) {
            throw new IllegalArgumentException(UPTIMECREW_SCRAPE_FAILED_MESSAGE);
        }
        if (shouldRejectWellfoundShellScrape(parsed, uri)) {
            throw new IllegalArgumentException(WELLFOUND_BLOCKED_MESSAGE);
        }

        if (parsed == null) {
            
            parsed = new ScrapedJobListingDto();
            parsed.setSource(hostToSource(uri));
            parsed.setOriginalLink(uri.toString());
        }

        if (isBlank(parsed.getTitle())) {
            
            parsed.setTitle("Not found");
        }
        if (isBlank(parsed.getCompany())) {
            
            parsed.setCompany("Not found");
        }
        if (isBlank(parsed.getLocation())) {
            
            parsed.setLocation("Not found");
        }
        if (isBlank(parsed.getSalary())) {
            
            parsed.setSalary("Not listed");
        }
        if (isBlank(parsed.getOriginalLink())) {
            parsed.setOriginalLink(uri.toString());
        }
        if (isBlank(parsed.getSource())) {
            parsed.setSource(hostToSource(uri));
        }

        parsed.setOriginalLink(normalizeOriginalLinkForInputUri(parsed.getOriginalLink(), uri));
        parsed.setSource(normalizeSourceForInputUri(parsed.getSource(), uri));

        return parsed;
    }

    
    
    public ScrapedJobListingDto scrapeJobFromText(String text, String url) {
        if (isBlank(text)) {
            throw new IllegalArgumentException("text is required");
        }

        URI uri = null;
        if (!isBlank(url)) {
            uri = normalizeHttpUri(url);
        }

        ScrapedJobListingDto parsed = parseFromPastedJobText(text, uri);
        parsed = applyWorkdayUriFallback(parsed, uri);
        parsed = applyHandshakeUriFallback(parsed, uri);
        parsed = applyWellfoundUriFallback(parsed, uri);
        parsed = sanitizeScrapedListing(parsed);
        parsed = applyWorkdayUriFallback(parsed, uri);
        parsed = applyHandshakeUriFallback(parsed, uri);
        parsed = applyWellfoundUriFallback(parsed, uri);

        if (parsed == null) {
            parsed = new ScrapedJobListingDto();
        }

        if (isBlank(parsed.getTitle())) {
            parsed.setTitle("Not found");
        }
        if (isBlank(parsed.getCompany())) {
            parsed.setCompany("Not found");
        }
        if (isBlank(parsed.getLocation())) {
            parsed.setLocation("Not found");
        }
        if (isBlank(parsed.getSalary())) {
            parsed.setSalary("Not listed");
        }
        if (isBlank(parsed.getOriginalLink()) && uri != null) {
            parsed.setOriginalLink(uri.toString());
        }
        if (isBlank(parsed.getSource())) {
            parsed.setSource(defaultTextSource(text, uri));
        }

        if (uri != null) {
            parsed.setOriginalLink(normalizeOriginalLinkForInputUri(parsed.getOriginalLink(), uri));
            parsed.setSource(normalizeSourceForInputUri(parsed.getSource(), uri));
        }

        return parsed;
    }

    
    
    private ScrapedJobListingDto sanitizeScrapedListing(ScrapedJobListingDto parsed) {
        if (parsed == null) {
            return null;
        }

        parsed.setTitle(sanitizeFieldValue(parsed.getTitle(), "not found"));
        parsed.setCompany(sanitizeCompanyCandidate(sanitizeFieldValue(parsed.getCompany(), "not found")));
        parsed.setLocation(sanitizeFieldValue(parsed.getLocation(), "not found"));
        String sanitizedSalary = sanitizeFieldValue(parsed.getSalary(), "not listed");
        parsed.setSalary(normalizeSalaryValue(sanitizedSalary));
        parsed.setOriginalLink(isBlank(parsed.getOriginalLink()) ? null : parsed.getOriginalLink().trim());
        parsed.setSource(isBlank(parsed.getSource()) ? null : parsed.getSource().trim());
        return parsed;
    }

    
    
    private String sanitizeFieldValue(String value, String placeholder) {
        if (isBlank(value)) {
            return null;
        }
        
        String trimmed = value.trim();
        if (!isBlank(placeholder) && placeholder.equalsIgnoreCase(trimmed)) {
            return null;
        }

        
        String lower = trimmed.toLowerCase();
        if (trimmed.contains("<")
                || trimmed.contains(">")
                || lower.contains("http-equiv")
                || lower.contains("charset=")
                || lower.contains("viewport")
                || lower.contains("open graph tags")) {
            return null;
        }

        if (trimmed.length() > 220) {
            return null;
        }

        return trimmed;
    }

    
    
    ScrapedJobListingDto applyWorkdayUriFallback(ScrapedJobListingDto parsed, URI uri) {
        if (uri == null || !isWorkdayHost(uri.getHost())) {
            return parsed;
        }

        ScrapedJobListingDto resolved = parsed == null ? new ScrapedJobListingDto() : parsed;
        resolved.setTitle(firstNonBlank(resolved.getTitle(), inferWorkdayTitleFromUri(uri)));
        resolved.setCompany(firstNonBlank(resolved.getCompany(), inferWorkdayCompanyFromUri(uri)));
        resolved.setLocation(firstNonBlank(resolved.getLocation(), inferWorkdayLocationFromUri(uri)));
        resolved.setOriginalLink(firstNonBlank(resolved.getOriginalLink(), uri.toString()));
        resolved.setSource(firstNonBlank(resolved.getSource(), hostToSource(uri)));
        return resolved;
    }

    
    
    ScrapedJobListingDto applyWellfoundUriFallback(ScrapedJobListingDto parsed, URI uri) {
        if (uri == null || !isWellfoundHost(uri.getHost())) {
            return parsed;
        }

        ScrapedJobListingDto resolved = parsed == null ? new ScrapedJobListingDto() : parsed;
        String inferredTitle = inferWellfoundTitleFromUri(uri);
        String inferredCompany = inferWellfoundCompanyFromUri(uri);

        String cleanedTitle = cleanText(resolved.getTitle());
        if (isBlank(cleanedTitle) || isWellfoundNoiseTitle(cleanedTitle)) {
            resolved.setTitle(inferredTitle);
        } else {
            resolved.setTitle(cleanedTitle);
        }

        String cleanedCompany = normalizeWellfoundCompanyCandidate(resolved.getCompany());
        resolved.setCompany(firstNonBlank(cleanedCompany, inferredCompany));
        resolved.setOriginalLink(firstNonBlank(resolved.getOriginalLink(), uri.toString()));
        resolved.setSource(firstNonBlank(normalizeWellfoundSource(resolved.getSource()), "wellfound"));
        return resolved;
    }

    
    
    private ScrapedJobListingDto scrapeUptimeCrewJob(URI uri) {
        
        String jobId = extractUptimeCrewJobId(uri);
        if (isBlank(jobId)) {
            return null;
        }

        for (String endpointPath : uptimeCrewApiPathsForUri(uri, jobId)) {
            
            JsonNode payload = fetchJson(UPTIMECREW_API_BASE_URL + endpointPath);
            
            ScrapedJobListingDto parsed = parseFromUptimeCrewApi(payload, uri);
            if (parsed != null) {
                return parsed;
            }
        }

        for (String endpointPath : uptimeCrewListApiPathsForUri(uri)) {
            
            JsonNode payload = fetchJson(UPTIMECREW_API_BASE_URL + endpointPath);
            
            JsonNode match = findUptimeCrewJobInList(payload, jobId);
            
            ScrapedJobListingDto parsed = parseFromUptimeCrewApi(match, uri);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    
    
    private List<String> uptimeCrewApiPathsForUri(URI uri, String jobId) {
        boolean preferStaffAug = uri != null
                && !isBlank(uri.getPath())
                && uri.getPath().toLowerCase(Locale.ROOT).contains("/jobs/senior/");
        if (preferStaffAug) {
            return List.of("/staffAug/jobs/" + jobId, "/jobs/" + jobId);
        }
        return List.of("/jobs/" + jobId, "/staffAug/jobs/" + jobId);
    }

    
    
    private JsonNode fetchJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            
            String body = response.body();
            if (isBlank(body)) {
                return null;
            }

            return objectMapper.readTree(body);
        
        
        } catch (Exception ignored) {
            return null;
        }
    }

    
    
    private List<String> uptimeCrewListApiPathsForUri(URI uri) {
        
        String publishingEntity = uptimeCrewPublishingEntity(uri);
        return List.of(
                "/jobs?publishingEntity=" + publishingEntity,
                "/jobs?active=true&publishingEntity=" + publishingEntity,
                "/staffAug/jobs?publishingEntity=" + publishingEntity,
                "/staffAug/jobs?active=true&publishingEntity=" + publishingEntity
        );
    }

    
    
    private String uptimeCrewPublishingEntity(URI uri) {
        
        String host = uri == null ? null : uri.getHost();
        if (!isBlank(host) && host.toLowerCase(Locale.ROOT).contains("smoothstack")) {
            return "SMOOTHSTACK";
        }
        return "UPTIMECREW";
    }

    
    
    private JsonNode findUptimeCrewJobInList(JsonNode payload, String jobId) {
        if (payload == null || payload.isNull() || isBlank(jobId)) {
            return null;
        }
        if (!payload.isArray()) {
            return null;
        }

        for (JsonNode node : payload) {
            if (node == null || node.isNull() || !node.isObject()) {
                continue;
            }
            
            String id = pickText(node, "Id", "Requisition__c", "Name", "Job_ID__c");
            if (!isBlank(id) && jobId.equalsIgnoreCase(id.trim())) {
                return node;
            }
        }
        return null;
    }

    
    
    ScrapedJobListingDto parseFromUptimeCrewApi(JsonNode payload, URI uri) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            return null;
        }

        
        String title = pickText(payload, "Job_Title__c", "Name");
        String company = firstNonBlank(
                normalizeUptimeCrewCompany(pickText(payload, "Publishing_Entity__c")),
                inferUptimeCrewCompanyFromHost(uri)
        );
        
        String location = extractUptimeCrewLocation(payload);
        
        String salary = extractUptimeCrewSalary(payload);

        if (isBlank(title) && isBlank(company) && isBlank(location) && isBlank(salary)) {
            return null;
        }

        
        ScrapedJobListingDto response = new ScrapedJobListingDto();
        response.setTitle(cleanText(title));
        response.setCompany(cleanText(company));
        response.setLocation(cleanText(location));
        response.setSalary(cleanText(salary));
        response.setOriginalLink(uri == null ? null : uri.toString());
        response.setSource(hostToSource(uri));
        return response;
    }

    
    
    private String extractUptimeCrewLocation(JsonNode payload) {
        JsonNode records = payload.path("Job_Locations__r").path("records");
        if (records.isArray() && records.size() > 0) {
            List<String> locations = new ArrayList<>();
            for (JsonNode record : records) {
                JsonNode address = record.path("Site_Location__r").path("Address__c");
                String location = joinNonBlank(", ",
                        pickText(address, "city"),
                        firstNonBlank(pickText(address, "stateCode"), pickText(address, "state"))
                );
                if (isBlank(location)) {
                    location = pickText(record.path("Site_Location__r"), "Name");
                }
                if (!isBlank(location) && !locations.contains(location)) {
                    
                    locations.add(location);
                }
            }

            if (locations.size() == 1) {
                return locations.get(0);
            }
            if (locations.size() > 1) {
                return locations.get(0) + " +" + (locations.size() - 1) + " more";
            }
        }

        String detailsLocation = extractUptimeCrewLocationFromDetails(pickText(payload, "Job_Details_JSON__c"));
        if (!isBlank(detailsLocation)) {
            return detailsLocation;
        }
        return pickText(payload, "Job_Location__c");
    }

    
    
    private String extractUptimeCrewSalary(JsonNode payload) {
        String salaryFromDetails = extractSalaryFromText(pickText(payload, "Job_Details_JSON__c"));
        if (!isBlank(salaryFromDetails)) {
            return salaryFromDetails;
        }

        
        JsonNode yearOneSalary = payload.get("Year_1_Salary__c");
        if (yearOneSalary != null && yearOneSalary.isNumber()) {
            
            long value = yearOneSalary.asLong();
            if (value > 0) {
                return "$" + String.format(Locale.US, "%,d", value) + " / YEAR";
            }
        }
        return null;
    }

    
    
    private String normalizeUptimeCrewCompany(String publishingEntity) {
        if (isBlank(publishingEntity)) {
            return null;
        }
        String normalized = publishingEntity.trim().toLowerCase(Locale.ROOT);
        if ("uptimecrew".equals(normalized)) {
            return "Uptime Crew";
        }
        if ("smoothstack".equals(normalized)) {
            return "Smoothstack";
        }
        return toTitleCaseWords(publishingEntity.replace('_', ' ').replace('-', ' '));
    }

    
    
    private String inferUptimeCrewCompanyFromHost(URI uri) {
        if (uri == null || isBlank(uri.getHost())) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.contains("smoothstack")) {
            return "Smoothstack";
        }
        if (host.contains("uptimecrew")) {
            return "Uptime Crew";
        }
        return null;
    }

    
    
    private String extractUptimeCrewLocationFromDetails(String detailsJson) {
        if (isBlank(detailsJson)) {
            return null;
        }

        List<String> locations = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            collectUptimeCrewLocations(root, locations);
        } catch (Exception ignored) {
            collectUptimeCrewLocationsFromText(detailsJson, locations);
        }

        if (locations.isEmpty()) {
            return null;
        }
        if (locations.size() == 1) {
            return locations.get(0);
        }
        return locations.get(0) + " +" + (locations.size() - 1) + " more";
    }

    
    
    private void collectUptimeCrewLocations(JsonNode node, List<String> locations) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectUptimeCrewLocations(child, locations);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        JsonNode contents = node.get("contents");
        if (contents != null && contents.isArray()) {
            for (JsonNode entry : contents) {
                if (!entry.isTextual()) {
                    continue;
                }
                String value = cleanText(entry.asText());
                if (!looksLikeUptimeCrewLocation(value)) {
                    continue;
                }
                if (!locations.contains(value)) {
                    locations.add(value);
                }
            }
        }

        for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
            collectUptimeCrewLocations(it.next(), locations);
        }
    }

    
    
    private void collectUptimeCrewLocationsFromText(String text, List<String> locations) {
        Pattern pattern = Pattern.compile("([A-Za-z][A-Za-z .'-]+,\\s*[A-Z]{2})");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = cleanText(matcher.group(1));
            if (!looksLikeUptimeCrewLocation(value)) {
                continue;
            }
            if (!locations.contains(value)) {
                locations.add(value);
            }
        }
    }

    
    
    private boolean looksLikeUptimeCrewLocation(String value) {
        if (isBlank(value)) {
            return false;
        }
        String cleaned = cleanText(value);
        if (isBlank(cleaned) || cleaned.length() > 80) {
            return false;
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.contains("$")
                || lower.contains("salary")
                || lower.contains("year")
                || lower.contains("week")
                || lower.contains("benefit")
                || lower.contains("experience")) {
            return false;
        }
        if (cleaned.matches(".*\\d{4,}.*")) {
            return false;
        }
        return cleaned.contains(",");
    }

    
    
    private String extractUptimeCrewJobId(URI uri) {
        if (uri == null || isBlank(uri.getPath())) {
            return null;
        }
        Matcher matcher = UPTIMECREW_JOB_ID_PATTERN.matcher(uri.getPath());
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    
    
    boolean shouldRejectUptimeCrewShellScrape(ScrapedJobListingDto parsed, URI uri) {
        if (uri == null || !isUptimeCrewHost(uri.getHost()) || isBlank(uri.getPath())) {
            return false;
        }
        if (!uri.getPath().toLowerCase(Locale.ROOT).contains("/jobs/")) {
            return false;
        }
        if (parsed == null) {
            return true;
        }

        String title = normalizeText(parsed.getTitle());
        String company = normalizeText(parsed.getCompany());
        String location = normalizeText(parsed.getLocation());

        boolean titleMissing = isBlank(parsed.getTitle()) || title.equals("not found");
        boolean genericTitle = title.equals("uptime crew")
                || title.equals("smoothstack")
                || title.equals("jobs");
        boolean companyMissing = isBlank(parsed.getCompany()) || company.equals("not found");
        boolean genericCompany = company.equals("uptime crew")
                || company.equals("smoothstack")
                || company.equals("jobs.uptimecrew.com")
                || company.equals("jobs.smoothstack.com");
        boolean locationMissing = isBlank(parsed.getLocation()) || location.equals("not found");

        return (genericTitle && (companyMissing || genericCompany || locationMissing))
                || (titleMissing && (companyMissing || genericCompany) && locationMissing);
    }

    
    
    boolean shouldRejectWellfoundShellScrape(ScrapedJobListingDto parsed, URI uri) {
        if (uri == null || !isWellfoundHost(uri.getHost())) {
            return false;
        }

        if (parsed == null) {
            return true;
        }

        String company = normalizeWellfoundCompanyCandidate(parsed.getCompany());
        String location = sanitizeFieldValue(parsed.getLocation(), "not found");
        String salary = sanitizeFieldValue(parsed.getSalary(), "not listed");
        return isBlank(company) && isBlank(location) && isBlank(salary);
    }

    
    
    ScrapedJobListingDto applyHandshakeUriFallback(ScrapedJobListingDto parsed, URI uri) {
        if (uri == null || !isHandshakeHost(uri.getHost())) {
            return parsed;
        }

        
        String inferredTitle = inferHandshakeTitleFromUri(uri);
        String inferredCompany = inferHandshakeCompanyFromUri(uri);
        if (parsed == null && isBlank(inferredCompany) && isBlank(inferredTitle)) {
            return null;
        }

        ScrapedJobListingDto resolved = parsed == null ? new ScrapedJobListingDto() : parsed;
        HandshakeTitleCompany titleAndCompany = parseHandshakeTitleAndCompany(resolved.getTitle());

        
        String normalizedTitle = titleAndCompany.title();
        if (isHandshakeNoiseTitle(normalizedTitle)) {
            normalizedTitle = null;
        }
        normalizedTitle = firstNonBlank(normalizedTitle, inferredTitle);

        String normalizedCompany = firstNonBlank(
                normalizeHandshakeCompany(resolved.getCompany()),
                normalizeHandshakeCompany(titleAndCompany.company()),
                inferredCompany
        );

        if (!isBlank(normalizedTitle)) {
            
            resolved.setTitle(normalizedTitle);
        } else if (isHandshakeNoiseTitle(resolved.getTitle())) {
            
            resolved.setTitle(null);
        }
        
        resolved.setCompany(normalizedCompany);
        resolved.setSource(firstNonBlank(resolved.getSource(), hostToSource(uri)));
        resolved.setOriginalLink(firstNonBlank(resolved.getOriginalLink(), uri.toString()));
        return resolved;
    }

    
    
    boolean shouldRejectHandshakeJobScrape(ScrapedJobListingDto parsed, URI uri) {
        if (uri == null || !isHandshakeHost(uri.getHost()) || !isHandshakeJobPath(uri)) {
            return false;
        }

        if (parsed == null) {
            return true;
        }

        String normalizedTitle = cleanHandshakeTitle(parsed.getTitle());
        return isBlank(normalizedTitle) || isHandshakeNoiseTitle(normalizedTitle);
    }

    
    
    private boolean isHandshakeJobPath(URI uri) {
        if (uri == null || isBlank(uri.getPath())) {
            return false;
        }
        return HANDSHAKE_JOB_PATH_PATTERN.matcher(uri.getPath()).matches();
    }

    
    
    private ScrapedJobListingDto parseFromWebPage(URI uri) {
        try {
            PageFetchResult page = fetchHtml(uri.toString());
            ScrapedJobListingDto parsed = null;
            parsed = parseFromJsonLd(page.html(), page.finalUri());
            if (parsed == null) {
                parsed = parseFromMetaTags(page.html(), page.finalUri());
            }

            ScrapedJobListingDto ashbyParsed = parseFromAshbyPage(page.html(), page.finalUri());
            parsed = mergeWithAshbyPreference(parsed, ashbyParsed, page.finalUri());

            if (isWorkdayHost(page.finalUri().getHost())) {
                ScrapedJobListingDto workdayParsed = parseFromWorkdayPage(page.html(), page.finalUri());
                parsed = mergeScrapedResults(parsed, workdayParsed, page.finalUri());
            }
            ScrapedJobListingDto genericParsed = parseFromGenericPage(page.html(), page.finalUri());
            parsed = mergeScrapedResults(parsed, genericParsed, page.finalUri());
            return parsed;
        
        
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    
    
    private ScrapedJobListingDto parseFromPastedJobText(String rawText, URI uri) {
        if (isBlank(rawText)) {
            return null;
        }

        String flattenedText = cleanText(rawText);
        if (isBlank(flattenedText)) {
            return null;
        }

        List<String> lines = splitPastedTextLines(rawText);
        String title = extractTitleFromPastedText(lines);
        String company = firstNonBlank(
                extractCompanyFromPastedText(lines, title),
                isWellfoundHost(uri == null ? null : uri.getHost()) ? inferWellfoundCompanyFromUri(uri) : null,
                isHandshakeHost(uri == null ? null : uri.getHost()) ? inferHandshakeCompanyFromUri(uri) : null
        );

        String locationSearchText = lines.isEmpty() ? flattenedText : String.join(" | ", lines);
        String location = firstNonBlank(
                extractLocationFromPastedText(lines),
                normalizeLocationCandidate(extractLocationFromVisibleText(locationSearchText))
        );
        String normalizedText = normalizeText(flattenedText);
        if (isBlank(location) && normalizedText.contains("remote")) {
            location = "Remote";
        } else if (!isBlank(location)
                && normalizedText.contains("remote")
                && !normalizeText(location).contains("remote")) {
            location = "Remote (" + location + ")";
        }

        String salary = firstNonBlank(
                extractSalaryFromPastedText(lines),
                extractSalaryFromText(flattenedText)
        );

        if (isBlank(title) && isBlank(company) && isBlank(location) && isBlank(salary)) {
            return null;
        }

        ScrapedJobListingDto response = new ScrapedJobListingDto();
        response.setTitle(cleanText(title));
        response.setCompany(cleanText(company));
        response.setLocation(cleanText(location));
        response.setSalary(cleanText(salary));
        response.setOriginalLink(uri == null ? null : uri.toString());
        response.setSource(defaultTextSource(rawText, uri));
        return response;
    }

    
    
    private List<String> splitPastedTextLines(String rawText) {
        if (isBlank(rawText)) {
            return List.of();
        }

        return Stream.of(rawText.split("\\R"))
                .map(this::cleanText)
                .map(line -> line == null ? null : line.replaceFirst("^[\\u2022*\\-\\s]+", "").trim())
                .filter(line -> !isBlank(line))
                .limit(300)
                .toList();
    }

    
    
    private String extractTitleFromPastedText(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        int jobDescriptionIndex = findPastedTextLineIndex(lines, "job description");
        if (jobDescriptionIndex >= 0) {
            for (int index = jobDescriptionIndex + 1; index < Math.min(lines.size(), jobDescriptionIndex + 7); index++) {
                String candidate = normalizePastedTitle(lines.get(index));
                if (isLikelyPastedTitleLine(candidate)) {
                    return candidate;
                }
            }
        }

        for (String line : lines) {
            String candidate = normalizePastedTitle(line);
            if (isLikelyPastedTitleLine(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    
    
    private String extractCompanyFromPastedText(List<String> lines, String title) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        String labeledCompany = sanitizeCompanyCandidate(
                extractLabeledValueFromPastedText(lines, "company", "employer", "organization", "hiring organization")
        );
        if (!isBlank(labeledCompany)) {
            return labeledCompany;
        }

        String companyFromAboutSection = extractCompanyFromAboutSection(lines, title);
        if (!isBlank(companyFromAboutSection)) {
            return companyFromAboutSection;
        }

        int topScanLimit = Math.min(lines.size(), 12);
        for (int index = 0; index < topScanLimit; index++) {
            Matcher logoMatcher = HANDSHAKE_TEXT_LOGO_LINE_PATTERN.matcher(lines.get(index));
            if (!logoMatcher.matches()) {
                continue;
            }
            String companyFromLogo = normalizeHandshakeCompany(logoMatcher.group(1));
            String cleaned = sanitizeCompanyCandidate(companyFromLogo);
            if (!isBlank(cleaned)) {
                return cleaned;
            }
        }

        int topLineLimit = Math.min(lines.size(), 15);
        for (int index = 0; index < topLineLimit; index++) {
            String line = lines.get(index);
            if (isHandshakePastedTextNoiseLine(normalizeText(line))) {
                continue;
            }
            HandshakeTitleCompany titleAndCompany = parseHandshakeTitleAndCompany(line);
            String companyFromTitleLine = normalizeHandshakeCompany(titleAndCompany.company());
            if (!isBlank(companyFromTitleLine)) {
                return sanitizeCompanyCandidate(companyFromTitleLine);
            }
        }

        int titleIndex = findPastedTextLineIndex(lines, title);
        if (titleIndex > 0) {
            for (int index = titleIndex - 1; index >= Math.max(0, titleIndex - 4); index--) {
                String candidate = normalizeCompanyFromPastedLine(lines.get(index), title);
                if (!isBlank(candidate)) {
                    return candidate;
                }
            }
        }
        if (titleIndex >= 0) {
            for (int index = titleIndex + 1; index <= Math.min(lines.size() - 1, titleIndex + 6); index++) {
                String candidate = normalizeCompanyFromPastedLine(lines.get(index), title);
                if (!isBlank(candidate)) {
                    return candidate;
                }
            }
        }

        for (int index = 0; index < Math.min(lines.size(), 20); index++) {
            String candidate = normalizeCompanyFromPastedLine(lines.get(index), title);
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    
    
    private String extractCompanyFromAboutSection(List<String> lines, String title) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        int aboutCompanyIndex = findPastedTextLineIndex(lines, "About the company");
        if (aboutCompanyIndex >= 0) {
            int scanEnd = Math.min(lines.size(), aboutCompanyIndex + 12);
            for (int index = aboutCompanyIndex + 1; index < scanEnd; index++) {
                String line = cleanText(lines.get(index));
                if (isBlank(line)) {
                    continue;
                }

                Matcher companyLogoMatcher = COMPANY_LOGO_LINE_PATTERN.matcher(line);
                if (companyLogoMatcher.matches()) {
                    String companyFromLogo = sanitizeCompanyCandidate(companyLogoMatcher.group(1));
                    if (!isBlank(companyFromLogo)) {
                        return companyFromLogo;
                    }
                }

                String candidate = normalizeCompanyFromPastedLine(line, title);
                if (!isBlank(candidate)) {
                    return candidate;
                }
            }
        }

        int scanLimit = Math.min(lines.size(), 160);
        for (int index = 0; index < scanLimit; index++) {
            String line = cleanText(lines.get(index));
            if (isBlank(line)) {
                continue;
            }

            Matcher aboutMatcher = ABOUT_LINE_PATTERN.matcher(line);
            if (!aboutMatcher.matches()) {
                continue;
            }

            String aboutValue = cleanText(aboutMatcher.group(1));
            if (isBlank(aboutValue)) {
                continue;
            }

            String normalizedAboutValue = normalizeText(aboutValue);
            if (normalizedAboutValue.equals("the company")
                    || normalizedAboutValue.equals("company")
                    || normalizedAboutValue.equals("the job")
                    || normalizedAboutValue.equals("job")
                    || normalizedAboutValue.equals("the role")
                    || normalizedAboutValue.equals("role")
                    || normalizedAboutValue.equals("this job")
                    || normalizedAboutValue.equals("this role")
                    || normalizedAboutValue.equals("us")) {
                continue;
            }

            String candidate = sanitizeCompanyCandidate(aboutValue);
            if (isBlank(candidate)) {
                continue;
            }
            if (isLikelyPastedTitleLine(candidate) && !normalizeText(candidate).equals(normalizeText(title))) {
                continue;
            }
            return candidate;
        }

        return null;
    }

    
    
    private String extractLocationFromPastedText(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        String labeledLocation = normalizeLocationCandidate(
                extractLabeledValueFromPastedText(lines, "location", "job location", "work location")
        );
        if (!isBlank(labeledLocation)) {
            return labeledLocation;
        }

        String basedIn = normalizeLocationCandidate(
                extractLabeledValueFromPastedText(lines, "based in")
        );
        if (!isBlank(basedIn)) {
            return basedIn;
        }

        int lineLimit = Math.min(lines.size(), 60);
        for (int index = 0; index < lineLimit; index++) {
            String line = cleanText(lines.get(index));
            if (isBlank(line)) {
                continue;
            }
            String normalized = normalizeText(line);
            if (!normalized.startsWith("remote")) {
                continue;
            }

            Matcher basedInMatcher = Pattern.compile("(?i)^remote\\s*(?:,\\s*)?based\\s+in\\s+(.+)$").matcher(line);
            if (basedInMatcher.matches()) {
                String scopedLocation = normalizeLocationCandidate(basedInMatcher.group(1));
                if (!isBlank(scopedLocation)) {
                    return "Remote (" + scopedLocation + ")";
                }
            }

            Matcher parenthesizedMatcher = Pattern.compile("(?i)^remote\\s*\\((.+)\\)$").matcher(line);
            if (parenthesizedMatcher.matches()) {
                String scopedLocation = normalizeLocationCandidate(parenthesizedMatcher.group(1));
                if (!isBlank(scopedLocation)) {
                    return "Remote (" + scopedLocation + ")";
                }
            }

            return "Remote";
        }

        return null;
    }

    
    
    private String extractSalaryFromPastedText(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        String labeledSalary = extractLabeledValueFromPastedText(
                lines,
                "salary",
                "salary range",
                "compensation",
                "pay",
                "pay range"
        );
        if (!isBlank(labeledSalary)) {
            String extracted = extractSalaryFromText(labeledSalary);
            return firstNonBlank(extracted, cleanText(labeledSalary));
        }

        for (String line : lines) {
            if (isBlank(line) || line.length() > 120) {
                continue;
            }
            String extracted = extractSalaryFromText(line);
            if (!isBlank(extracted)) {
                return extracted;
            }
        }
        return null;
    }

    
    
    private String extractLabeledValueFromPastedText(List<String> lines, String... labels) {
        if (lines == null || lines.isEmpty() || labels == null || labels.length == 0) {
            return null;
        }

        int lineLimit = Math.min(lines.size(), 120);
        for (int index = 0; index < lineLimit; index++) {
            String line = cleanText(lines.get(index));
            if (isBlank(line)) {
                continue;
            }
            for (String label : labels) {
                if (isBlank(label)) {
                    continue;
                }
                Pattern pattern = Pattern.compile(
                        "(?i)^" + Pattern.quote(label) + "\\s*(?:[:\\-]|\\u2013|\\u2014)\\s*(.+)$"
                );
                Matcher matcher = pattern.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                String value = cleanText(matcher.group(1));
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    
    
    private int findPastedTextLineIndex(List<String> lines, String value) {
        if (lines == null || lines.isEmpty() || isBlank(value)) {
            return -1;
        }

        String normalizedTarget = normalizeText(value);
        for (int index = 0; index < lines.size(); index++) {
            if (normalizedTarget.equals(normalizeText(lines.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    
    
    private String normalizePastedTitle(String line) {
        if (isBlank(line)) {
            return null;
        }

        String cleaned = cleanHandshakeTitle(line);
        cleaned = cleaned.replaceFirst("(?i)^(?:position|role|job title)\\s*[:\\-]\\s*", "");
        cleaned = cleaned.replaceFirst(
                "(?i)\\s*\\((?:full\\s*-?\\s*time|part\\s*-?\\s*time|internship|co\\s*-?op|contract|temporary)\\)\\s*$",
                ""
        );
        cleaned = cleanText(cleaned);
        if (isBlank(cleaned)) {
            return null;
        }

        if (cleaned.equals(cleaned.toUpperCase(Locale.ROOT)) && cleaned.matches(".*[A-Z].*")) {
            cleaned = toTitleCaseWords(cleaned);
        }
        return cleaned;
    }

    
    
    private boolean isLikelyPastedTitleLine(String value) {
        if (isBlank(value)) {
            return false;
        }

        String normalized = normalizeText(value);
        if (isHandshakeNoiseTitle(value) || isHandshakePastedTextNoiseLine(normalized)) {
            return false;
        }
        if (value.length() > 100 || value.split("\\s+").length > 10) {
            return false;
        }
        if (normalized.contains("http://") || normalized.contains("https://")) {
            return false;
        }
        return HANDSHAKE_TEXT_TITLE_KEYWORD_PATTERN.matcher(value).find();
    }

    
    
    private String normalizeCompanyFromPastedLine(String line, String title) {
        if (isBlank(line)) {
            return null;
        }

        String cleaned = cleanText(line).replaceFirst("(?i)^company\\s*[:\\-]\\s*", "").trim();
        if (isBlank(cleaned)) {
            return null;
        }

        String normalized = normalizeText(cleaned);
        if (normalized.contains(" based in ")) {
            return null;
        }
        if (isHandshakePastedTextNoiseLine(normalized)) {
            return null;
        }
        if (!isBlank(title) && normalized.equals(normalizeText(title))) {
            return null;
        }
        if (isLikelyPastedTitleLine(cleaned)) {
            return null;
        }
        if (HANDSHAKE_INDUSTRY_LINE_PATTERN.matcher(cleaned).matches()) {
            return null;
        }
        if (normalized.contains("$")
                || normalized.contains("/yr")
                || normalized.contains("/year")
                || normalized.contains("/hr")
                || normalized.contains("/hour")
                || normalized.contains("salary")
                || normalized.contains("compensation")
                || normalized.contains("pay range")
                || normalized.contains("location")) {
            return null;
        }
        if (cleaned.split("\\s+").length > 6) {
            return null;
        }

        return sanitizeCompanyCandidate(normalizeHandshakeCompany(cleaned));
    }

    
    
    private boolean isHandshakePastedTextNoiseLine(String normalizedLine) {
        if (isBlank(normalizedLine)) {
            return true;
        }

        if (normalizedLine.endsWith(" logo")
                || normalizedLine.equals("save")
                || normalizedLine.equals("share")
                || normalizedLine.equals("apply")
                || normalizedLine.equals("paid")
                || normalizedLine.equals("job")
                || normalizedLine.equals("full-time")
                || normalizedLine.equals("part-time")
                || normalizedLine.equals("remote")
                || normalizedLine.equals("hybrid")
                || normalizedLine.equals("work from home")
                || normalizedLine.equals("at a glance")
                || normalizedLine.equals("job description")
                || normalizedLine.equals("the role")
                || normalizedLine.equals("key responsibilities")
                || normalizedLine.equals("what you'll learn")
                || normalizedLine.equals("training & onboarding")
                || normalizedLine.equals("compensation")
                || normalizedLine.equals("job details")
                || normalizedLine.equals("interested?")) {
            return true;
        }

        return normalizedLine.startsWith("posted ")
                || normalizedLine.startsWith("apply by ")
                || normalizedLine.startsWith("connect with ")
                || normalizedLine.matches("^[0-9]+\\.?\\s+.*");
    }

    
    
    private String defaultTextSource(String text, URI uri) {
        if (uri != null) {
            return hostToSource(uri);
        }
        String normalized = normalizeText(text);
        if (normalized.contains("joinhandshake")
                || normalized.contains("handshake")
                || (normalized.contains("at a glance") && normalized.contains("apply"))
                || (normalized.contains("work from home") && normalized.contains("posted "))) {
            return "joinhandshake.com";
        }
        if (normalized.contains("wellfound")) {
            return "wellfound.com";
        }
        return "pasted-text";
    }

    
    
    private List<ScrapedJobListingDto> fetchFromRemotive(String query) {
        
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        
        String url = REMOTIVE_SEARCH_URL.formatted(encoded);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            
            JsonNode jobs = root.path("jobs");
            if (!jobs.isArray()) {
                return List.of();
            }

            List<ScrapedJobListingDto> listings = new ArrayList<>();
            for (JsonNode job : jobs) {
                
                String title = pickText(job, "title", "job_title");
                
                String company = pickText(job, "company_name", "company");
                
                String location = pickText(job, "candidate_required_location", "location", "region");
                
                String salary = pickText(job, "salary", "compensation", "salary_range");
                
                String originalLink = pickText(job, "url", "job_url", "apply_url");

                if (isBlank(title) || isBlank(company) || isBlank(originalLink)) {
                    continue;
                }
                if (isBlank(location)) {
                    location = "Remote";
                }
                salary = normalizeSalaryValue(salary);
                if (isBlank(salary)) {
                    salary = "Not listed";
                }

                listings.add(new ScrapedJobListingDto(
                        title,
                        company,
                        location,
                        salary,
                        originalLink,
                        "remotive"
                ));
            }
            return listings;
        
        
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        
        
        } catch (IOException e) {
            return List.of();
        }
    }

    
    
    private List<ScrapedJobListingDto> fetchFromArbeitnow(String query) {
        
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        
        String url = ARBEITNOW_SEARCH_URL.formatted(encoded);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            
            JsonNode jobs = root.path("data");
            if (!jobs.isArray()) {
                
                jobs = root.path("jobs");
            }
            if (!jobs.isArray()) {
                return List.of();
            }

            List<ScrapedJobListingDto> listings = new ArrayList<>();
            for (JsonNode job : jobs) {
                
                String title = pickText(job, "title", "job_title");
                
                String company = pickText(job, "company_name", "company");
                
                String location = pickText(job, "location", "candidate_required_location");
                
                String salary = pickText(job, "salary", "salary_range", "compensation");
                
                String originalLink = pickText(job, "url", "job_url", "apply_url");

                if (isBlank(location)) {
                    location = "Remote";
                }
                salary = normalizeSalaryValue(salary);
                if (isBlank(salary)) {
                    salary = "Not listed";
                }
                if (isBlank(title) || isBlank(company) || isBlank(originalLink)) {
                    continue;
                }

                listings.add(new ScrapedJobListingDto(
                        title,
                        company,
                        location,
                        salary,
                        originalLink,
                        "arbeitnow"
                ));
            }
            return listings;
        
        
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        
        
        } catch (IOException e) {
            return List.of();
        }
    }

    
    
    private void mergeUnique(Map<String, ScrapedJobListingDto> target,
                             List<ScrapedJobListingDto> incoming) {
        for (ScrapedJobListingDto listing : incoming) {
            if (listing == null) {
                continue;
            }
            listing.setSalary(normalizeSalaryValue(listing.getSalary()));
            String key = !isBlank(listing.getOriginalLink())
                    ? listing.getOriginalLink()
                    : (listing.getSource() + "|" + listing.getCompany() + "|" + listing.getTitle() + "|" + listing.getLocation());
            
            target.putIfAbsent(key, listing);
        }
    }

    
    
    private String pickText(JsonNode node, String... fields) {
        for (String field : fields) {
            
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual()) {
                String text = value.asText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            } else if (value.isNumber() || value.isBoolean()) {
                return value.asText();
            } else if (value.isArray()) {
                String joined = StreamSupport.stream(value.spliterator(), false)
                        .map(JsonNode::asText)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(", "));
                if (!joined.isEmpty()) {
                    return joined;
                }
            }
        }
        return null;
    }

    
    
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    
    
    private URI normalizeHttpUri(String raw) {
        
        String normalized = raw.trim();
        if (!normalized.matches("^https?://.*")) {
            normalized = "https://" + normalized;
        }

        URI uri;
        try {
            
            uri = URI.create(normalized);
        
        
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid URL");
        }

        
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http/https URLs are supported");
        }
        if (isBlank(uri.getHost())) {
            throw new IllegalArgumentException("Invalid URL host");
        }
        return uri;
    }

    
    
    private PageFetchResult fetchHtml(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("Could not fetch URL (status " + response.statusCode() + ")");
            }
            return new PageFetchResult(response.body(), response.uri());
        
        
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Request interrupted");
        
        
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not fetch URL");
        }
    }

    
    
    private ScrapedJobListingDto scrapeLinkedInJob(URI uri) {
        
        String jobId = extractLinkedInJobId(uri);
        if (isBlank(jobId)) {
            return null;
        }

        
        String html = fetchLinkedInGuestJobHtml(jobId);
        if (isBlank(html)) {
            return null;
        }

        
        URI canonicalUri = URI.create("https://www.linkedin.com/jobs/view/" + jobId + "/");
        
        ScrapedJobListingDto jsonLd = parseFromJsonLd(html, canonicalUri);

        String title = firstNonBlank(
                extractTextByClassToken(html, "top-card-layout__title"),
                extractTextByClassToken(html, "topcard__title"),
                jsonLd == null ? null : jsonLd.getTitle()
        );
        String company = firstNonBlank(
                extractLinkedInCompany(html),
                extractTextByClassToken(html, "topcard__org-name-link"),
                jsonLd == null ? null : jsonLd.getCompany()
        );
        String location = firstNonBlank(
                extractTextByClassToken(html, "topcard__flavor--bullet"),
                extractTextByClassToken(html, "topcard__flavor"),
                jsonLd == null ? null : jsonLd.getLocation()
        );
        String salary = normalizeLinkedInSalary(firstNonBlank(
                extractLinkedInSalaryRange(html),
                jsonLd == null ? null : jsonLd.getSalary(),
                extractTextByClassToken(html, "compensation__salary")
        ));

        if (isBlank(title) && isBlank(company) && isBlank(location) && isBlank(salary)) {
            return null;
        }

        
        ScrapedJobListingDto response = new ScrapedJobListingDto();
        response.setTitle(cleanText(title));
        response.setCompany(cleanText(company));
        response.setLocation(cleanText(location));
        response.setSalary(cleanText(salary));
        response.setOriginalLink(canonicalUri.toString());
        
        response.setSource("linkedin");
        return response;
    }

    
    
    private String extractLinkedInJobId(URI uri) {
        
        String path = uri.getPath();
        if (!isBlank(path)) {
            
            Matcher viewMatcher = LINKEDIN_VIEW_ID_PATTERN.matcher(path);
            if (viewMatcher.find()) {
                return viewMatcher.group(1);
            }

            
            Matcher genericMatcher = LINKEDIN_GENERIC_ID_PATTERN.matcher(path);
            if (genericMatcher.find()) {
                return genericMatcher.group(1);
            }
        }

        
        String query = uri.getQuery();
        if (!isBlank(query)) {
            for (String pair : query.split("&")) {
                if (isBlank(pair)) {
                    continue;
                }
                
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                String key = parts[0];
                String value = parts[1];
                if (("currentJobId".equalsIgnoreCase(key) || "jobId".equalsIgnoreCase(key))
                        && LINKEDIN_GENERIC_ID_PATTERN.matcher(value).matches()) {
                    return value;
                }
            }
        }

        return null;
    }

    
    
    private String fetchLinkedInGuestJobHtml(String jobId) {
        
        String url = LINKEDIN_GUEST_JOB_URL.formatted(jobId);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", "https://www.linkedin.com/jobs/search/")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return response.body();
        
        
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        
        
        } catch (IOException e) {
            return null;
        }
    }

    
    
    private String extractTextByClassToken(String html, String classToken) {
        Pattern pattern = Pattern.compile(
                "<[^>]*class\\s*=\\s*['\\\"][^'\\\"]*\\b" + Pattern.quote(classToken) + "\\b[^'\\\"]*['\\\"][^>]*>(.*?)</[^>]+>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return cleanText(stripTags(matcher.group(1)));
        }
        return null;
    }

    
    
    private String extractTextByDataAutomationId(String html, String automationId) {
        Pattern pattern = Pattern.compile(
                "<[^>]*data-automation-id\\s*=\\s*['\\\"]" + Pattern.quote(automationId)
                        + "['\\\"][^>]*>(.*?)</[^>]+>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return cleanText(stripTags(matcher.group(1)));
        }
        return null;
    }

    
    
    private String extractLinkedInCompany(String html) {
        
        Matcher matcher = LINKEDIN_COMPANY_LINK_PATTERN.matcher(html);
        if (matcher.find()) {
            return cleanText(stripTags(matcher.group(1)));
        }
        return null;
    }

    
    
    private String extractLinkedInSalaryRange(String html) {
        
        Matcher matcher = LINKEDIN_PAY_RANGE_PATTERN.matcher(html);
        String best = null;
        int bestScore = Integer.MIN_VALUE;

        while (matcher.find()) {
            String candidate = cleanText(matcher.group(1));
            if (isBlank(candidate)) {
                continue;
            }

            
            int score = candidate.length();
            
            String lower = candidate.toLowerCase();
            if (candidate.contains("-") || candidate.contains("to") || candidate.contains("\u2013") || candidate.contains("\u2014")) {
                score += 20;
            }
            if (lower.contains("/yr") || lower.contains("/year") || lower.contains("per year")
                    || lower.contains("/hr") || lower.contains("/hour")) {
                score += 10;
            }

            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }

        if (!isBlank(best)) {
            return best;
        }
        return null;
    }

    
    
    private String normalizeLinkedInSalary(String salary) {
        if (isBlank(salary)) {
            return null;
        }

        
        String cleaned = cleanText(salary);
        
        String lower = cleaned.toLowerCase();

        if ("base pay range".equals(lower) || "pay range".equals(lower) || "salary".equals(lower)) {
            return null;
        }
        if (!lower.matches(".*\\d.*") && (lower.contains("pay range") || lower.contains("salary"))) {
            return null;
        }
        return cleaned;
    }

    private String normalizeSalaryValue(String salary) {
        if (isBlank(salary)) {
            return null;
        }

        String cleaned = cleanText(salary);
        if (isBlank(cleaned)) {
            return null;
        }

        if (!HOURLY_CADENCE_PATTERN.matcher(cleaned).find()) {
            return cleaned;
        }

        String currencySymbol = extractCurrencySymbol(cleaned);
        String currencyCode = extractCurrencyCode(cleaned);
        if (isBlank(currencySymbol) && isBlank(currencyCode)) {
            return cleaned;
        }

        List<Double> hourlyAmounts = new ArrayList<>();
        Matcher amountMatcher = HOURLY_AMOUNT_PATTERN.matcher(cleaned);
        while (amountMatcher.find()) {
            Double parsedAmount = parseMoneyAmount(amountMatcher.group(2));
            if (parsedAmount == null || parsedAmount <= 0) {
                continue;
            }
            hourlyAmounts.add(parsedAmount);
            if (hourlyAmounts.size() == 2) {
                break;
            }
        }

        if (hourlyAmounts.isEmpty()) {
            return cleaned;
        }

        long annualFirst = Math.round(hourlyAmounts.get(0) * FULL_TIME_HOURS_PER_YEAR);
        if (hourlyAmounts.size() == 1) {
            return formatAnnualSalary(annualFirst, currencySymbol, currencyCode);
        }

        long annualSecond = Math.round(hourlyAmounts.get(1) * FULL_TIME_HOURS_PER_YEAR);
        long annualMin = Math.min(annualFirst, annualSecond);
        long annualMax = Math.max(annualFirst, annualSecond);
        return formatAnnualSalaryRange(annualMin, annualMax, currencySymbol, currencyCode);
    }

    private String extractCurrencySymbol(String salary) {
        Matcher matcher = CURRENCY_SYMBOL_PATTERN.matcher(salary);
        if (!matcher.find()) {
            return null;
        }
        return cleanText(matcher.group());
    }

    private String extractCurrencyCode(String salary) {
        Matcher matcher = CURRENCY_CODE_PATTERN.matcher(salary);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group().toUpperCase(Locale.ROOT);
    }

    private Double parseMoneyAmount(String amountToken) {
        if (isBlank(amountToken)) {
            return null;
        }

        String normalized = amountToken.replace(",", "").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        double multiplier = 1d;
        char suffix = Character.toLowerCase(normalized.charAt(normalized.length() - 1));
        if (suffix == 'k' || suffix == 'm') {
            multiplier = suffix == 'k' ? 1_000d : 1_000_000d;
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        if (normalized.isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(normalized) * multiplier;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatAnnualSalary(long annualValue, String currencySymbol, String currencyCode) {
        return formatCurrencyAmount(annualValue, currencySymbol, currencyCode) + " / year";
    }

    private String formatAnnualSalaryRange(long annualMin, long annualMax, String currencySymbol, String currencyCode) {
        return formatCurrencyAmount(annualMin, currencySymbol, currencyCode)
                + " - "
                + formatCurrencyAmount(annualMax, currencySymbol, currencyCode)
                + " / year";
    }

    private String formatCurrencyAmount(long amount, String currencySymbol, String currencyCode) {
        String formatted = String.format(Locale.US, "%,d", amount);
        if (!isBlank(currencySymbol)) {
            return currencySymbol + formatted;
        }
        if (!isBlank(currencyCode)) {
            return currencyCode + " " + formatted;
        }
        return formatted;
    }

    
    
    private String stripTags(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("<[^>]+>", " ");
    }

    
    
    private boolean isLinkedInHost(String host) {
        if (isBlank(host)) {
            return false;
        }
        
        String normalized = host.toLowerCase();
        return normalized.equals("linkedin.com") || normalized.endsWith(".linkedin.com");
    }

    
    
    private boolean isWorkdayHost(String host) {
        if (isBlank(host)) {
            return false;
        }
        
        String normalized = host.toLowerCase();
        return normalized.contains("workdayjobs.com")
                || normalized.contains("myworkdayjobs.com")
                || normalized.contains("myworkdaysite.com")
                
                || normalized.contains(".wd");
    }

    private boolean isSimplifyHost(String host) {
        if (isBlank(host)) {
            return false;
        }

        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("simplify.jobs")
                || normalized.endsWith(".simplify.jobs");
    }

    private boolean isWellfoundHost(String host) {
        if (isBlank(host)) {
            return false;
        }

        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("wellfound.com")
                || normalized.endsWith(".wellfound.com");
    }

    String normalizeOriginalLinkForInputUri(String originalLink, URI inputUri) {
        if (inputUri != null && (isSimplifyHost(inputUri.getHost()) || isWellfoundHost(inputUri.getHost()))) {
            return inputUri.toString();
        }
        if (isBlank(originalLink)) {
            return inputUri == null ? null : inputUri.toString();
        }
        return originalLink.trim();
    }

    String normalizeSourceForInputUri(String source, URI inputUri) {
        if (inputUri != null && isSimplifyHost(inputUri.getHost())) {
            return "simplify";
        }
        if (inputUri != null && isWellfoundHost(inputUri.getHost())) {
            return "wellfound";
        }
        if (isBlank(source)) {
            return hostToSource(inputUri);
        }
        return source.trim();
    }

    
    
    private boolean isHandshakeHost(String host) {
        if (isBlank(host)) {
            return false;
        }
        
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.endsWith("joinhandshake.com")
                || normalized.endsWith(".joinhandshake.com")
                || normalized.equals("handshake.com")
                
                || normalized.endsWith(".handshake.com");
    }

    
    
    private boolean isUptimeCrewHost(String host) {
        if (isBlank(host)) {
            return false;
        }
        
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("jobs.uptimecrew.com")
                || normalized.equals("jobs.smoothstack.com")
                || normalized.endsWith(".uptimecrew.com")
                
                || normalized.endsWith(".smoothstack.com");
    }

    
    
    private boolean isAshbyHost(String host) {
        if (isBlank(host)) {
            return false;
        }
        
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("ashbyhq.com")
                || normalized.endsWith(".ashbyhq.com")
                || normalized.equals("ashbyprd.com")
                
                || normalized.endsWith(".ashbyprd.com");
    }

    
    
    private ScrapedJobListingDto parseFromWorkdayPage(String html, URI uri) {
        if (isBlank(html)) {
            return null;
        }

        
        Map<String, String> meta = extractMetaTags(html);
        String description = firstNonBlank(
                meta.get("og:description"),
                meta.get("description"),
                extractMetaField(html, "og:description"),
                extractMetaField(html, "description")
        );
        
        String workdayTitleFromUri = inferWorkdayTitleFromUri(uri);
        
        String workdayLocationFromUri = inferWorkdayLocationFromUri(uri);

        String title = firstNonBlank(
                extractTextByDataAutomationId(html, "jobPostingHeader"),
                extractTextByDataAutomationId(html, "jobPostingTitle"),
                extractTextByDataAutomationId(html, "jobTitle"),
                extractTextByDataAutomationId(html, "requisitionTitle"),
                meta.get("og:title"),
                meta.get("twitter:title"),
                extractMetaField(html, "og:title"),
                extractMetaField(html, "title"),
                extractTitleTag(html),
                workdayTitleFromUri
        );
        String company = firstNonBlank(
                extractTextByDataAutomationId(html, "companyName"),
                extractTextByDataAutomationId(html, "jobPostingCompany"),
                extractTextByDataAutomationId(html, "hiringOrganization"),
                meta.get("og:site_name"),
                inferWorkdayCompanyFromUri(uri)
        );
        String location = firstNonBlank(
                extractTextByDataAutomationId(html, "locations"),
                extractTextByDataAutomationId(html, "location"),
                extractTextByDataAutomationId(html, "jobPostingLocation"),
                extractTextByDataAutomationId(html, "primaryLocation"),
                extractWorkdayLocationFromDescription(description),
                workdayLocationFromUri
        );
        String salary = firstNonBlank(
                extractTextByDataAutomationId(html, "compensation"),
                extractTextByDataAutomationId(html, "jobPostingCompensation"),
                extractTextByDataAutomationId(html, "payRange"),
                extractSalaryFromText(description)
        );

        if (isBlank(title) && isBlank(company) && isBlank(location) && isBlank(salary)) {
            return null;
        }

        
        ScrapedJobListingDto response = new ScrapedJobListingDto();
        response.setTitle(cleanText(title));
        response.setCompany(cleanText(company));
        response.setLocation(cleanText(location));
        response.setSalary(cleanText(salary));
        response.setOriginalLink(uri.toString());
        response.setSource(hostToSource(uri));
        return response;
    }

    
    
    private ScrapedJobListingDto mergeScrapedResults(ScrapedJobListingDto primary,
                                                          ScrapedJobListingDto fallback,
                                                          URI uri) {
        if (primary == null && fallback == null) {
            return null;
        }
        if (primary == null) {
            
            primary = new ScrapedJobListingDto();
        }

        
        String inferredCompany = inferWorkdayCompanyFromUri(uri);
        primary.setTitle(firstNonBlank(primary.getTitle(), fallback == null ? null : fallback.getTitle()));
        primary.setCompany(firstNonBlank(primary.getCompany(), fallback == null ? null : fallback.getCompany(), inferredCompany));
        primary.setLocation(firstNonBlank(primary.getLocation(), fallback == null ? null : fallback.getLocation()));
        primary.setSalary(firstNonBlank(primary.getSalary(), fallback == null ? null : fallback.getSalary()));
        primary.setOriginalLink(firstNonBlank(primary.getOriginalLink(), fallback == null ? null : fallback.getOriginalLink(), uri.toString()));
        primary.setSource(firstNonBlank(primary.getSource(), fallback == null ? null : fallback.getSource(), hostToSource(uri)));
        return primary;
    }

    
    
    ScrapedJobListingDto mergeWithAshbyPreference(ScrapedJobListingDto parsed,
                                                       ScrapedJobListingDto ashbyParsed,
                                                       URI uri) {
        if (ashbyParsed == null) {
            return parsed;
        }
        if (uri != null && isAshbyHost(uri.getHost())) {
            return mergeScrapedResults(ashbyParsed, parsed, uri);
        }
        return mergeScrapedResults(parsed, ashbyParsed, uri);
    }

    
    
    private String extractWorkdayLocationFromDescription(String description) {
        if (isBlank(description)) {
            return null;
        }

        
        Matcher matcher = WORKDAY_DESCRIPTION_LOCATION_PATTERN.matcher(description);
        if (!matcher.find()) {
            return null;
        }

        String location = cleanText(matcher.group(1));
        if (isBlank(location)) {
            return null;
        }
        return location;
    }

    
    
    private String extractSalaryFromText(String text) {
        if (isBlank(text)) {
            return null;
        }

        String cleanedText = cleanText(text);
        if (isBlank(cleanedText)) {
            return null;
        }

        
        Matcher matcher = LINKEDIN_PAY_RANGE_PATTERN.matcher(cleanedText);
        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }

        Matcher salaryCodeMatcher = SALARY_CODE_RANGE_PATTERN.matcher(cleanedText);
        if (salaryCodeMatcher.find()) {
            return cleanText(salaryCodeMatcher.group(1));
        }
        return null;
    }

    
    
    private String extractSalaryForLocation(String text, String location) {
        if (isBlank(text) || isBlank(location)) {
            return null;
        }

        String normalizedLocation = normalizeLocationCandidate(location);
        if (isBlank(normalizedLocation)) {
            return null;
        }

        String[] locationParts = normalizedLocation.split(",");
        if (locationParts.length < 2) {
            return null;
        }

        String city = cleanText(locationParts[0]);
        String state = cleanText(locationParts[1]).toUpperCase(Locale.ROOT);
        if (isBlank(city) || isBlank(state)) {
            return null;
        }

        Matcher matcher = GEO_SALARY_ROW_PATTERN.matcher(text);
        while (matcher.find()) {
            String rowState = cleanText(matcher.group(1));
            String rowCity = cleanText(matcher.group(2));
            String rowSalary = cleanText(matcher.group(3));
            if (isBlank(rowState) || isBlank(rowCity) || isBlank(rowSalary)) {
                continue;
            }

            if (state.equalsIgnoreCase(rowState)
                    && normalizeText(city).equals(normalizeText(rowCity))) {
                return rowSalary;
            }
        }
        return null;
    }

    
    
    ScrapedJobListingDto parseFromGenericPage(String html, URI uri) {
        if (isBlank(html)) {
            return null;
        }

        Map<String, String> meta = extractMetaTags(html);
        String visibleText = cleanText(stripTags(html));
        String description = firstNonBlank(
                meta.get("description"),
                meta.get("og:description"),
                extractMetaField(html, "description"),
                extractMetaField(html, "og:description")
        );

        String title = cleanGenericTitle(firstNonBlank(
                extractMetaField(html, "og:title"),
                extractMetaField(html, "twitter:title"),
                extractJsonFieldFromScripts(html, "jobTitle", "postingTitle", "positionTitle", "title", "name"),
                extractItempropTextField(html, "title", "name", "headline"),
                extractTextByTagPattern(html, H1_TAG_PATTERN),
                extractTextByTagPattern(html, H2_TAG_PATTERN),
                extractTitleTag(html)
        ));

        String company = firstNonBlank(
                sanitizeCompanyCandidate(extractMetaField(html, "og:site_name")),
                sanitizeCompanyCandidate(extractMetaField(html, "application-name")),
                sanitizeCompanyCandidate(extractLabeledFieldValue(html, "company", "employer", "organization", "hiring organization")),
                sanitizeCompanyCandidate(extractJsonFieldFromScripts(html, "companyName", "company", "organizationName", "employerName")),
                sanitizeCompanyCandidate(extractCompanyFromTitle(title)),
                sanitizeCompanyCandidate(inferCompanyFromHost(uri))
        );

        String location = firstNonBlank(
                normalizeLocationCandidate(extractMetaField(html, "job:location")),
                normalizeLocationCandidate(extractMetaField(html, "geo.placename")),
                normalizeLocationCandidate(extractSchemaOrgLocation(html)),
                normalizeLocationCandidate(extractTextByClassToken(html, "jobGeoLocation")),
                normalizeLocationCandidate(extractLabeledFieldValue(html, "location", "job location", "work location")),
                normalizeLocationCandidate(extractJsonFieldFromScripts(html, "location", "locationName", "jobLocation", "workLocation")),
                normalizeLocationCandidate(extractLocationFromVisibleText(visibleText)),
                normalizeLocationCandidate(extractLocationFromVisibleText(description))
        );

        String salary = firstNonBlank(
                extractMetaField(html, "job:salary"),
                extractMetaField(html, "salary"),
                extractJsonFieldFromScripts(html, "salaryRange", "payRange", "salary", "compensation"),
                extractSalaryForLocation(visibleText, location),
                extractSalaryFromText(description),
                extractSalaryFromText(visibleText),
                extractLabeledFieldValue(html, "salary range", "salary", "compensation", "pay range", "pay")
        );

        if (isBlank(title) && isBlank(company) && isBlank(location) && isBlank(salary)) {
            return null;
        }

        ScrapedJobListingDto response = new ScrapedJobListingDto();
        response.setTitle(cleanText(title));
        response.setCompany(cleanText(company));
        response.setLocation(cleanText(location));
        response.setSalary(cleanText(salary));
        response.setOriginalLink(uri == null ? null : uri.toString());
        response.setSource(hostToSource(uri));
        return response;
    }

    
    
    private String extractTextByTagPattern(String html, Pattern tagPattern) {
        if (isBlank(html) || tagPattern == null) {
            return null;
        }
        Matcher matcher = tagPattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        return cleanText(stripTags(matcher.group(1)));
    }

    
    
    private String extractItempropTextField(String html, String... itemprops) {
        if (isBlank(html) || itemprops == null || itemprops.length == 0) {
            return null;
        }

        Matcher matcher = ITEMPROP_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String itemprop = cleanText(matcher.group(2));
            if (isBlank(itemprop)) {
                continue;
            }
            for (String wanted : itemprops) {
                if (isBlank(wanted) || !itemprop.equalsIgnoreCase(wanted)) {
                    continue;
                }
                String value = cleanText(stripTags(matcher.group(3)));
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    
    
    private String extractSchemaOrgLocation(String html) {
        if (isBlank(html)) {
            return null;
        }

        String locality = extractItempropMetaField(html, "addressLocality");
        String region = extractItempropMetaField(html, "addressRegion");
        String country = extractItempropMetaField(html, "addressCountry");
        String combined = joinNonBlank(", ", locality, region, country);
        if (!isBlank(combined)) {
            return combined;
        }

        return extractItempropMetaField(html, "jobLocation");
    }

    
    
    private String extractItempropMetaField(String html, String itempropKey) {
        if (isBlank(html) || isBlank(itempropKey)) {
            return null;
        }

        String wantedKey = itempropKey.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            Map<String, String> attrs = parseTagAttributes(matcher.group());
            String itemprop = attrs.get("itemprop");
            if (isBlank(itemprop) || !wantedKey.equals(itemprop.toLowerCase(Locale.ROOT))) {
                continue;
            }

            String value = firstNonBlank(attrs.get("content"), attrs.get("value"));
            if (!isBlank(value)) {
                return cleanText(value);
            }
        }
        return null;
    }

    
    
    private String extractLabeledFieldValue(String html, String... labelHints) {
        if (isBlank(html) || labelHints == null || labelHints.length == 0) {
            return null;
        }

        String firstMatch = null;
        String bestHumanMatch = null;
        Matcher matcher = STRONG_LABEL_VALUE_PATTERN.matcher(html);
        while (matcher.find()) {
            String label = cleanText(stripTags(matcher.group(1)));
            String value = cleanText(stripTags(matcher.group(2)));
            if (isBlank(label) || isBlank(value)) {
                continue;
            }
            if (labelMatchesAnyHint(label, labelHints)) {
                if (firstMatch == null) {
                    firstMatch = value;
                }
                if (!looksLikeInternalCompanyCode(value)
                        && (bestHumanMatch == null || value.length() > bestHumanMatch.length())) {
                    bestHumanMatch = value;
                }
            }
        }
        return firstNonBlank(bestHumanMatch, firstMatch);
    }

    
    
    private boolean labelMatchesAnyHint(String label, String... labelHints) {
        if (isBlank(label) || labelHints == null || labelHints.length == 0) {
            return false;
        }

        String normalizedLabel = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (isBlank(normalizedLabel)) {
            return false;
        }

        for (String hint : labelHints) {
            if (isBlank(hint)) {
                continue;
            }
            String normalizedHint = hint.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            if (!isBlank(normalizedHint) && normalizedLabel.contains(normalizedHint)) {
                return true;
            }
        }
        return false;
    }

    
    
    private String extractJsonFieldFromScripts(String html, String... keys) {
        if (isBlank(html) || keys == null || keys.length == 0) {
            return null;
        }

        Matcher scriptMatcher = SCRIPT_TAG_PATTERN.matcher(html);
        while (scriptMatcher.find()) {
            String script = scriptMatcher.group(1);
            if (isBlank(script) || script.length() > 400_000) {
                continue;
            }

            String scriptLower = script.toLowerCase(Locale.ROOT);
            if (!scriptLower.contains("job")
                    && !scriptLower.contains("posting")
                    && !scriptLower.contains("position")
                    && !scriptLower.contains("location")
                    && !scriptLower.contains("salary")) {
                continue;
            }

            for (String key : keys) {
                String value = extractJsonLikeStringValue(script, key);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    
    
    private String extractJsonLikeStringValue(String text, String key) {
        if (isBlank(text) || isBlank(key)) {
            return null;
        }

        Pattern pattern = Pattern.compile(
                "(?:\\\"|')" + Pattern.quote(key) + "(?:\\\"|')\\s*:\\s*(?:\\\"|')([^\\\"'\\r\\n]{1,220})(?:\\\"|')",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String value = matcher.group(1);
        if (isBlank(value)) {
            return null;
        }

        String unescaped = value
                .replace("\\\\/", "/")
                .replace("\\\\n", " ")
                .replace("\\\\t", " ")
                .replace("\\\\u0026", "&")
                .replace("\\\\u003c", "<")
                .replace("\\\\u003e", ">")
                .replace("\\\\u2013", "-")
                .replace("\\\\u2014", "-")
                .replace("\\\\\"", "\"")
                .replace("\\\\'", "'");
        String cleaned = cleanText(unescaped);
        if (isBlank(cleaned) || cleaned.contains("{") || cleaned.contains("}")) {
            return null;
        }
        return cleaned;
    }

    
    
    private String cleanGenericTitle(String title) {
        if (isBlank(title)) {
            return null;
        }
        String cleaned = cleanText(title);
        cleaned = cleaned.replaceFirst("(?i)\\s*[|\\-]\\s*(careers?|jobs?|job board|hiring).*$", "");
        return cleanText(cleaned);
    }

    
    
    private String extractCompanyFromTitle(String title) {
        if (isBlank(title)) {
            return null;
        }
        Matcher matcher = GENERIC_TITLE_COMPANY_PATTERN.matcher(title);
        if (!matcher.matches()) {
            return null;
        }
        String company = cleanText(matcher.group(2));
        if (isBlank(company)) {
            return null;
        }
        String normalized = company.toLowerCase(Locale.ROOT);
        if (normalized.equals("jobs")
                || normalized.equals("careers")
                || normalized.equals("job board")) {
            return null;
        }
        return company;
    }

    
    
    private String sanitizeCompanyCandidate(String value) {
        String cleaned = normalizeCompanyDisplay(cleanText(value));
        if (isBlank(cleaned) || looksLikeInternalCompanyCode(cleaned)) {
            return null;
        }
        return cleaned;
    }

    
    
    private String normalizeCompanyDisplay(String value) {
        if (isBlank(value)) {
            return null;
        }

        String cleaned = cleanText(value);
        if (isBlank(cleaned)) {
            return null;
        }

        if (!cleaned.contains(" ") && cleaned.toLowerCase(Locale.ROOT).endsWith(".jobs")) {
            String withoutSuffix = cleaned.substring(0, cleaned.length() - ".jobs".length());
            return toTitleCaseWords(withoutSuffix);
        }
        return cleaned;
    }

    
    
    private boolean looksLikeInternalCompanyCode(String value) {
        if (isBlank(value)) {
            return false;
        }

        String cleaned = cleanText(value);
        if (isBlank(cleaned) || cleaned.contains(" ")) {
            return false;
        }

        String alnum = cleaned.replaceAll("[^A-Za-z0-9]", "");
        if (alnum.length() < 5 || !alnum.equals(alnum.toUpperCase(Locale.ROOT)) || !alnum.matches("[A-Z0-9]+")) {
            return false;
        }

        return alnum.matches(".*\\d.*")
                || alnum.matches(".*(?:PRD|PROD|DEV|TEST|QA|UAT|STG|INT)\\d*$")
                || alnum.length() >= 9;
    }

    
    
    private String inferCompanyFromHost(URI uri) {
        if (uri == null || isBlank(uri.getHost())) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        String[] labels = host.split("\\.");
        if (labels.length == 0) {
            return null;
        }

        String candidate;
        if (labels.length >= 3 && isGenericHostLabel(labels[0])) {
            candidate = labels[1];
        } else if (labels.length >= 2) {
            candidate = labels[labels.length - 2];
        } else {
            candidate = labels[0];
        }

        if (isGenericHostLabel(candidate)) {
            return null;
        }
        String cleaned = candidate.replaceAll("[^a-zA-Z0-9]+", " ").trim();
        if (isBlank(cleaned) || cleaned.length() < 2) {
            return null;
        }
        return toTitleCaseWords(cleaned);
    }

    
    
    private boolean isGenericHostLabel(String value) {
        if (isBlank(value)) {
            return true;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("www")
                || normalized.equals("jobs")
                || normalized.equals("careers")
                || normalized.equals("apply")
                || normalized.equals("hiring")
                || normalized.equals("boards")
                || normalized.equals("app")
                || normalized.equals("localhost");
    }

    
    
    private String extractLocationFromVisibleText(String text) {
        if (isBlank(text)) {
            return null;
        }

        Matcher labelMatcher = LOCATION_LABEL_PATTERN.matcher(text);
        if (labelMatcher.find()) {
            String location = normalizeLocationCandidate(labelMatcher.group(1));
            if (!isBlank(location)) {
                return location;
            }
        }

        Matcher basedInMatcher = BASED_IN_LOCATION_PATTERN.matcher(text);
        if (basedInMatcher.find()) {
            String location = normalizeLocationCandidate(basedInMatcher.group(1));
            if (!isBlank(location)) {
                return location;
            }
        }

        Matcher countryStateCityMatcher = COUNTRY_STATE_CITY_PATTERN.matcher(text);
        if (countryStateCityMatcher.find()) {
            String state = cleanText(countryStateCityMatcher.group(1));
            String city = cleanText(countryStateCityMatcher.group(2));
            String location = normalizeLocationCandidate(joinNonBlank(", ", city, state));
            if (!isBlank(location)) {
                return location;
            }
        }

        Matcher cityStateMatcher = CITY_STATE_PATTERN.matcher(text);
        if (cityStateMatcher.find()) {
            String location = normalizeLocationCandidate(cityStateMatcher.group(1));
            if (!isBlank(location) && !looksLikeCountryStatePair(location)) {
                return location;
            }
        }

        Matcher remoteMatcher = REMOTE_HINT_PATTERN.matcher(text);
        if (remoteMatcher.find()) {
            String hint = cleanText(remoteMatcher.group(1));
            if (isBlank(hint)) {
                return null;
            }
            if (hint.equalsIgnoreCase("on-site") || hint.equalsIgnoreCase("onsite")) {
                return "Onsite";
            }
            return toTitleCaseWords(hint);
        }
        return null;
    }

    
    
    private boolean looksLikeCountryStatePair(String value) {
        if (isBlank(value)) {
            return false;
        }

        String[] parts = value.split(",");
        if (parts.length < 2) {
            return false;
        }

        String first = parts[0].trim().toLowerCase(Locale.ROOT);
        return first.equals("us")
                || first.equals("usa")
                || first.equals("uk")
                || first.equals("eu");
    }

    
    
    private String normalizeLocationCandidate(String candidate) {
        if (isBlank(candidate)) {
            return null;
        }
        String cleaned = cleanText(candidate).replaceAll("[,;:\\-\\s]+$", "");
        cleaned = cleaned.replaceAll("(?i),\\s*(us|usa|united states)$", "").trim();
        if (isBlank(cleaned) || cleaned.length() > 90) {
            return null;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.contains("salary")
                || lower.contains("compensation")
                || lower.contains("benefit")
                || lower.contains("experience")
                || lower.contains("responsibilit")) {
            return null;
        }
        return cleaned;
    }

    
    
    private String inferWorkdayCompanyFromUri(URI uri) {
        if (uri == null) {
            return null;
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String[] segments = Stream.of((uri.getPath() == null ? "" : uri.getPath()).split("/"))
                .filter(segment -> !isBlank(segment))
                
                .toArray(String[]::new);

        if (segments.length >= 2 && "recruiting".equalsIgnoreCase(segments[0])) {
            
            String fromPath = normalizeWorkdayCompanyToken(segments[1]);
            if (!isBlank(fromPath)) {
                return fromPath;
            }
        }

        if (segments.length >= 2 && segments[0].matches("(?i)[a-z]{2}-[a-z]{2}")) {
            
            String fromPath = normalizeWorkdayCompanyToken(segments[1]);
            if (!isBlank(fromPath)) {
                return fromPath;
            }
        }

        if (host.endsWith(".myworkdayjobs.com")) {
            String hostPrefix = host.substring(0, host.indexOf(".myworkdayjobs.com"));
            String firstLabel = hostPrefix.split("\\.")[0];
            
            String fromFirstLabel = normalizeWorkdayCompanyToken(firstLabel);
            if (!isBlank(fromFirstLabel)) {
                return fromFirstLabel;
            }
            return normalizeWorkdayCompanyToken(hostPrefix);
        }

        return null;
    }

    
    
    private String inferWorkdayTitleFromUri(URI uri) {
        
        String[] workdayJobSegments = workdayJobPathSegments(uri);
        if (workdayJobSegments.length == 0) {
            return null;
        }

        String titleSegment = workdayJobSegments[workdayJobSegments.length - 1];
        if (isBlank(titleSegment)) {
            return null;
        }

        
        String stripped = titleSegment.replaceAll("_[0-9]+$", "");
        return normalizeWorkdayPathToken(stripped);
    }

    
    
    private String inferWorkdayLocationFromUri(URI uri) {
        
        String[] workdayJobSegments = workdayJobPathSegments(uri);
        if (workdayJobSegments.length < 2) {
            return null;
        }

        
        String locationSegment = normalizeWorkdayPathToken(workdayJobSegments[workdayJobSegments.length - 2]);
        if (isBlank(locationSegment)) {
            return null;
        }

        String withoutPrefix = locationSegment.replaceFirst("^[A-Za-z]{2,6}\\s+", "").trim();
        return isBlank(withoutPrefix) ? locationSegment : withoutPrefix;
    }

    
    
    private String inferWellfoundTitleFromUri(URI uri) {
        if (uri == null || isBlank(uri.getPath())) {
            return null;
        }

        String[] segments = Stream.of(uri.getPath().split("/"))
                .filter(segment -> !isBlank(segment))
                
                .toArray(String[]::new);

        for (int index = 0; index < segments.length; index++) {
            if ("jobs".equalsIgnoreCase(segments[index]) && index + 1 < segments.length) {
                return normalizeWellfoundTitleToken(segments[index + 1]);
            }
        }

        return null;
    }

    
    
    private String normalizeWellfoundTitleToken(String token) {
        if (isBlank(token)) {
            return null;
        }

        String decoded = URLDecoder.decode(token, StandardCharsets.UTF_8);
        String withoutId = WELLFOUND_JOB_ID_PREFIX_PATTERN.matcher(decoded).replaceFirst("");
        String cleaned = withoutId
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ")
                
                .trim();
        if (isBlank(cleaned) || !cleaned.matches(".*[A-Za-z].*")) {
            return null;
        }
        return toTitleCaseWords(cleaned);
    }

    
    
    private String inferWellfoundCompanyFromUri(URI uri) {
        if (uri == null || isBlank(uri.getPath())) {
            return null;
        }

        String[] segments = Stream.of(uri.getPath().split("/"))
                .filter(segment -> !isBlank(segment))
                
                .toArray(String[]::new);

        for (int index = 0; index < segments.length; index++) {
            if ("company".equalsIgnoreCase(segments[index]) || "companies".equalsIgnoreCase(segments[index])) {
                if (index + 1 < segments.length) {
                    return normalizeWellfoundCompanyToken(segments[index + 1]);
                }
            }
        }

        return null;
    }

    
    
    private String normalizeWellfoundCompanyToken(String token) {
        if (isBlank(token)) {
            return null;
        }

        String decoded = URLDecoder.decode(token, StandardCharsets.UTF_8);
        String withoutId = WELLFOUND_SLUG_ID_SUFFIX_PATTERN.matcher(decoded).replaceFirst("");
        String cleaned = withoutId
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ")
                
                .trim();
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (isBlank(cleaned)
                || normalized.equals("company")
                || normalized.equals("companies")
                || normalized.equals("job")
                || normalized.equals("jobs")) {
            return null;
        }
        return toTitleCaseWords(cleaned);
    }

    
    
    private boolean isWellfoundNoiseTitle(String title) {
        if (isBlank(title)) {
            return false;
        }

        String normalized = title.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("wellfound")
                || normalized.equals("wellfound.com")
                || normalized.equals("startup job search")
                || normalized.equals("wellfound - startup job search")
                || normalized.equals("jobs")
                || normalized.equals("job");
    }

    
    
    private String normalizeWellfoundCompanyCandidate(String company) {
        String cleaned = sanitizeCompanyCandidate(company);
        if (isBlank(cleaned)) {
            return null;
        }

        String normalized = cleaned.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("wellfound") || normalized.equals("wellfound.com")) {
            return null;
        }
        return cleaned;
    }

    
    
    private String normalizeWellfoundSource(String source) {
        if (isBlank(source)) {
            return null;
        }

        String cleaned = source.trim();
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (normalized.equals("wellfound")
                || normalized.equals("wellfound.com")
                || normalized.equals("www.wellfound.com")) {
            return "wellfound";
        }
        return cleaned;
    }

    
    
    private HandshakeTitleCompany parseHandshakeTitleAndCompany(String rawTitle) {
        
        String cleaned = cleanHandshakeTitle(rawTitle);
        if (isBlank(cleaned)) {
            return new HandshakeTitleCompany(null, null);
        }

        
        Matcher matcher = HANDSHAKE_TITLE_COMPANY_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            String title = cleanText(matcher.group(1));
            String company = normalizeHandshakeCompany(matcher.group(2));
            return new HandshakeTitleCompany(title, company);
        }

        return new HandshakeTitleCompany(cleaned, null);
    }

    
    
    private String cleanHandshakeTitle(String rawTitle) {
        if (isBlank(rawTitle)) {
            return null;
        }

        
        String cleaned = cleanText(rawTitle);
        cleaned = HANDSHAKE_SUFFIX_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = HANDSHAKE_REVIEW_SUFFIX_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = cleaned.replaceFirst("(?i)^jobs?\\s*[-:]\\s*", "");
        
        cleaned = cleanText(cleaned);
        return isBlank(cleaned) ? null : cleaned;
    }

    
    
    private boolean isHandshakeNoiseTitle(String title) {
        if (isBlank(title)) {
            return false;
        }
        String normalized = title.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("log in")
                || normalized.equals("login")
                || normalized.equals("sign in")
                || normalized.equals("sign up")
                || normalized.equals("join handshake")
                || normalized.equals("handshake")
                || normalized.equals("job search")
                || normalized.equals("job listing")
                || normalized.equals("job listings")
                
                || normalized.equals("jobs");
    }

    
    
    private String normalizeHandshakeCompany(String value) {
        if (isBlank(value)) {
            return null;
        }

        String cleaned = cleanText(value).replaceFirst("^@", "").trim();
        
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (normalized.equals("handshake")
                || normalized.equals("joinhandshake")
                || normalized.equals("join handshake")
                || normalized.equals("app.joinhandshake.com")) {
            return null;
        }
        return cleaned;
    }

    
    
    private String inferHandshakeCompanyFromUri(URI uri) {
        if (uri == null || isBlank(uri.getPath())) {
            return null;
        }

        String[] segments = Stream.of(uri.getPath().split("/"))
                .filter(segment -> !isBlank(segment))
                
                .toArray(String[]::new);

        for (int index = 0; index < segments.length; index++) {
            if ("employers".equalsIgnoreCase(segments[index]) || "employer".equalsIgnoreCase(segments[index])) {
                if (index + 1 < segments.length) {
                    return normalizeHandshakeCompanyToken(segments[index + 1]);
                }
            }
        }

        return null;
    }

    private String inferHandshakeTitleFromUri(URI uri) {
        if (uri == null || isBlank(uri.getPath())) {
            return null;
        }

        Matcher matcher = HANDSHAKE_JOB_ID_PATH_PATTERN.matcher(uri.getPath());
        if (!matcher.matches()) {
            return null;
        }

        String jobId = cleanText(matcher.group(1));
        if (isBlank(jobId)) {
            return null;
        }
        return "Handshake Job " + jobId;
    }

    
    
    private String normalizeHandshakeCompanyToken(String token) {
        if (isBlank(token)) {
            return null;
        }

        
        String decoded = URLDecoder.decode(token, StandardCharsets.UTF_8);
        String withoutId = HANDSHAKE_EMPLOYER_ID_SUFFIX_PATTERN.matcher(decoded).replaceFirst("");
        String cleaned = withoutId
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ")
                
                .trim();

        
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (isBlank(cleaned)
                || normalized.equals("jobs")
                || normalized.equals("job")
                || normalized.equals("employers")
                || normalized.equals("employer")) {
            return null;
        }

        return toTitleCaseWords(cleaned);
    }

    
    
    private String toTitleCaseWords(String value) {
        if (isBlank(value)) {
            return value;
        }
        return Stream.of(value.split("\\s+"))
                .filter(part -> !part.isEmpty())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT)
                        + (part.length() > 1 ? part.substring(1).toLowerCase(Locale.ROOT) : ""))
                .collect(Collectors.joining(" "));
    }

    
    
    private String[] workdayJobPathSegments(URI uri) {
        if (uri == null || isBlank(uri.getPath())) {
            return new String[0];
        }

        String[] segments = Stream.of(uri.getPath().split("/"))
                .filter(segment -> !isBlank(segment))
                
                .toArray(String[]::new);

        for (int index = 0; index < segments.length; index++) {
            if ("job".equalsIgnoreCase(segments[index])) {
                if (index + 1 >= segments.length) {
                    return new String[0];
                }
                return Stream.of(segments)
                        .skip(index + 1L)
                        
                        .toArray(String[]::new);
            }
        }

        return new String[0];
    }

    
    
    private String normalizeWorkdayCompanyToken(String token) {
        if (isBlank(token)) {
            return null;
        }

        
        String decoded = URLDecoder.decode(token, StandardCharsets.UTF_8);
        String cleaned = decoded
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ")
                
                .trim();

        
        String lower = cleaned.toLowerCase();
        if (isBlank(cleaned)
                || "external".equals(lower)
                || "internal".equals(lower)
                || lower.matches("wd\\d+")) {
            return null;
        }
        return cleaned;
    }

    
    
    private String normalizeWorkdayPathToken(String token) {
        if (isBlank(token)) {
            return null;
        }

        
        String decoded = URLDecoder.decode(token, StandardCharsets.UTF_8);
        return decoded
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ")
                
                .trim();
    }

    
    
    private ScrapedJobListingDto parseFromJsonLd(String html, URI uri) {
        
        Matcher matcher = JSON_LD_PATTERN.matcher(html);
        while (matcher.find()) {
            
            String candidate = matcher.group(1);
            if (isBlank(candidate)) {
                continue;
            }

            JsonNode root;
            try {
                
                root = objectMapper.readTree(candidate);
            
            
            } catch (Exception ignored) {
                continue;
            }

            
            JsonNode jobPosting = findJobPostingNode(root);
            if (jobPosting == null || jobPosting.isMissingNode()) {
                continue;
            }

            
            String title = pickText(jobPosting, "title", "name", "headline");
            
            String company = extractCompany(jobPosting);
            
            String location = extractLocation(jobPosting);
            
            String salary = extractSalary(jobPosting);
            
            String originalLink = pickText(jobPosting, "url", "mainEntityOfPage");

            
            ScrapedJobListingDto response = new ScrapedJobListingDto();
            response.setTitle(cleanText(title));
            response.setCompany(cleanText(company));
            response.setLocation(cleanText(location));
            response.setSalary(cleanText(salary));
            response.setOriginalLink(cleanText(originalLink));
            response.setSource(hostToSource(uri));
            return response;
        }
        return null;
    }

    
    
    ScrapedJobListingDto parseFromAshbyPage(String html, URI uri) {
        if (isBlank(html)) {
            return null;
        }

        boolean looksLikeAshby = (uri != null && isAshbyHost(uri.getHost())) || html.contains("window.__appData");
        if (!looksLikeAshby) {
            return null;
        }

        
        Matcher appDataMatcher = ASHBY_APP_DATA_PATTERN.matcher(html);
        if (!appDataMatcher.find()) {
            return null;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(appDataMatcher.group(1));
        
        
        } catch (Exception ignored) {
            return null;
        }

        
        JsonNode posting = root.path("posting");
        if (posting == null || posting.isMissingNode() || posting.isNull()) {
            return null;
        }

        
        JsonNode linkedData = posting.path("linkedData");

        String title = firstNonBlank(
                pickText(posting, "title"),
                pickText(linkedData, "title", "name"),
                extractMetaField(html, "og:title"),
                extractTitleTag(html)
        );
        String company = firstNonBlank(
                pickText(root.path("organization"), "name"),
                extractCompany(linkedData),
                pickText(posting, "legalEntityNameForTextingConsent"),
                extractMetaField(html, "og:site_name")
        );
        String location = firstNonBlank(
                pickText(posting, "locationExternalName", "locationName"),
                pickText(posting, "secondaryLocationNames"),
                extractLocation(linkedData),
                extractMetaField(html, "job:location"),
                extractMetaField(html, "geo.placename")
        );
        if (isBlank(location) && posting.path("isRemote").asBoolean(false)) {
            location = "Remote";
        }

        String salary = firstNonBlank(
                pickText(posting, "scrapeableCompensationSalarySummary", "compensationTierSummary"),
                extractSalary(linkedData),
                extractMetaField(html, "job:salary"),
                extractMetaField(html, "salary")
        );
        String originalLink = firstNonBlank(
                extractMetaField(html, "og:url"),
                uri == null ? null : uri.toString()
        );

        if (isBlank(title) && isBlank(company) && isBlank(location) && isBlank(salary)) {
            return null;
        }

        
        ScrapedJobListingDto response = new ScrapedJobListingDto();
        response.setTitle(cleanText(title));
        response.setCompany(cleanText(company));
        response.setLocation(cleanText(location));
        response.setSalary(cleanText(salary));
        response.setOriginalLink(cleanText(originalLink));
        response.setSource(hostToSource(uri));
        return response;
    }

    
    
    private ScrapedJobListingDto parseFromMetaTags(String html, URI uri) {
        
        Map<String, String> meta = extractMetaTags(html);

        String title = firstNonBlank(
                meta.get("og:title"),
                meta.get("twitter:title"),
                extractTitleTag(html)
        );
        String company = firstNonBlank(
                meta.get("og:site_name"),
                meta.get("twitter:site")
        );
        String location = firstNonBlank(
                meta.get("job:location"),
                meta.get("geo.placename"),
                meta.get("og:locality")
        );
        String salary = firstNonBlank(
                meta.get("job:salary"),
                meta.get("salary")
        );
        String originalLink = firstNonBlank(
                meta.get("og:url"),
                uri.toString()
        );

        if (isBlank(title) && isBlank(company) && isBlank(location) && isBlank(salary)) {
            return null;
        }

        
        ScrapedJobListingDto response = new ScrapedJobListingDto();
        response.setTitle(cleanText(title));
        response.setCompany(cleanText(company));
        response.setLocation(cleanText(location));
        response.setSalary(cleanText(salary));
        response.setOriginalLink(cleanText(originalLink));
        response.setSource(hostToSource(uri));
        return response;
    }

    
    
    private JsonNode findJobPostingNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (isJobPostingType(node)) {
            return node;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                
                JsonNode found = findJobPostingNode(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node.isObject()) {
            
            JsonNode graph = node.get("@graph");
            if (graph != null) {
                
                JsonNode found = findJobPostingNode(graph);
                if (found != null) {
                    return found;
                }
            }
            for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                JsonNode found = findJobPostingNode(it.next());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    
    
    private boolean isJobPostingType(JsonNode node) {
        
        JsonNode typeNode = node.get("@type");
        if (typeNode == null || typeNode.isNull()) {
            return false;
        }
        if (typeNode.isTextual()) {
            return typeNode.asText().toLowerCase().contains("jobposting");
        }
        if (typeNode.isArray()) {
            for (JsonNode child : typeNode) {
                if (child.isTextual() && child.asText().toLowerCase().contains("jobposting")) {
                    return true;
                }
            }
        }
        return false;
    }

    
    
    private String extractCompany(JsonNode node) {
        
        JsonNode hiringOrg = node.get("hiringOrganization");
        if (hiringOrg == null || hiringOrg.isNull()) {
            return pickText(node, "company", "employer");
        }
        if (hiringOrg.isTextual()) {
            return hiringOrg.asText();
        }
        if (hiringOrg.isObject()) {
            return pickText(hiringOrg, "name", "legalName");
        }
        return null;
    }

    
    
    private String extractLocation(JsonNode node) {
        
        JsonNode jobLocation = node.get("jobLocation");
        if (jobLocation == null || jobLocation.isNull()) {
            return pickText(node, "location", "jobLocationType");
        }
        if (jobLocation.isTextual()) {
            return jobLocation.asText();
        }
        JsonNode first = jobLocation;
        if (jobLocation.isArray() && jobLocation.size() > 0) {
            
            first = jobLocation.get(0);
        }
        if (first == null || first.isNull()) {
            return null;
        }
        if (first.isTextual()) {
            return first.asText();
        }
        
        JsonNode address = first.get("address");
        if (address != null && address.isObject()) {
            
            String locality = pickText(address, "addressLocality");
            
            String region = pickText(address, "addressRegion");
            
            String country = pickText(address, "addressCountry");
            return joinNonBlank(", ", locality, region, country);
        }
        return pickText(first, "name");
    }

    
    
    private String extractSalary(JsonNode node) {
        
        JsonNode baseSalary = node.get("baseSalary");
        if (baseSalary == null || baseSalary.isNull()) {
            return pickText(node, "salary", "estimatedSalary");
        }
        if (baseSalary.isTextual() || baseSalary.isNumber()) {
            return baseSalary.asText();
        }
        if (baseSalary.isObject()) {
            
            String currency = pickText(baseSalary, "currency");
            
            JsonNode valueNode = baseSalary.get("value");
            if (valueNode != null && valueNode.isObject()) {
                
                String min = pickText(valueNode, "minValue");
                
                String max = pickText(valueNode, "maxValue");
                
                String value = pickText(valueNode, "value");
                
                String unit = pickText(valueNode, "unitText");

                String range = null;
                if (!isBlank(min) && !isBlank(max)) {
                    range = min + " - " + max;
                } else if (!isBlank(value)) {
                    range = value;
                } else if (!isBlank(min)) {
                    range = min;
                } else if (!isBlank(max)) {
                    range = max;
                }

                if (!isBlank(range) && !isBlank(currency)) {
                    range = currency + " " + range;
                }
                if (!isBlank(range) && !isBlank(unit)) {
                    range = range + " / " + unit;
                }
                if (!isBlank(range)) {
                    return range;
                }
            }
            
            String fallback = pickText(baseSalary, "value");
            if (!isBlank(fallback) && !isBlank(currency)) {
                return currency + " " + fallback;
            }
            return fallback;
        }
        return null;
    }

    
    
    private Map<String, String> extractMetaTags(String html) {
        Map<String, String> meta = new HashMap<>();
        
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            
            String tag = matcher.group();
            
            Map<String, String> attrs = parseTagAttributes(tag);
            String key = firstNonBlank(attrs.get("property"), attrs.get("name"), attrs.get("itemprop"));
            
            String value = attrs.get("content");
            if (!isBlank(key) && !isBlank(value)) {
                meta.put(key.toLowerCase(), cleanText(value));
            }
        }
        return meta;
    }

    
    
    private Map<String, String> parseTagAttributes(String tag) {
        Map<String, String> attrs = new HashMap<>();
        
        Matcher matcher = ATTR_PATTERN.matcher(tag);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase();
            
            String value = matcher.group(3);
            
            attrs.put(key, value);
        }
        return attrs;
    }

    
    
    private String extractMetaField(String html, String key) {
        if (isBlank(html) || isBlank(key)) {
            return null;
        }

        String wantedKey = key.trim().toLowerCase();
        
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            
            String tag = matcher.group();
            
            Map<String, String> attrs = parseTagAttributes(tag);
            String metaKey = firstNonBlank(attrs.get("property"), attrs.get("name"), attrs.get("itemprop"));
            if (isBlank(metaKey) || !wantedKey.equals(metaKey.toLowerCase())) {
                continue;
            }
            
            String value = attrs.get("content");
            if (!isBlank(value)) {
                return cleanText(value);
            }
        }

        return null;
    }

    
    
    private String extractTitleTag(String html) {
        
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }
        return null;
    }

    
    
    private String hostToSource(URI uri) {
        
        String host = uri.getHost();
        if (isBlank(host)) {
            return "unknown";
        }
        
        host = host.toLowerCase();
        if (host.startsWith("www.")) {
            
            host = host.substring(4);
        }
        return host;
    }

    
    
    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replaceAll("\\s+", " ")
                
                .trim();
    }

    
    private String normalizeText(String value) {
        String cleaned = cleanText(value);
        if (cleaned == null) {
            return "";
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    
    
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    
    
    private String joinNonBlank(String delimiter, String... parts) {
        return Stream.of(parts)
                .filter(part -> !isBlank(part))
                .collect(Collectors.joining(delimiter));
    }

    private record HandshakeTitleCompany(String title, String company) {
    }

    private record PageFetchResult(String html, URI finalUri) {
    }
}
