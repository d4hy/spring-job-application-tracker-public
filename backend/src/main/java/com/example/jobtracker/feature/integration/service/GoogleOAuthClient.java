package com.example.jobtracker.feature.integration.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP client wrapper for Google OAuth, Gmail, and Sheets APIs.
 * Builds outbound requests, handles JSON parsing, and translates remote failures into
 * domain-friendly exceptions for the integration service layer.
 */
@Service
public class GoogleOAuthClient {
    private static final String GOOGLE_AUTH_BASE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String GMAIL_MESSAGES_LIST_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    private static final String GMAIL_MESSAGE_GET_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/%s?format=metadata&metadataHeaders=Subject";
    private static final String GMAIL_SCOPES = "openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/spreadsheets";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private final OfflineModeSupport offlineModeSupport;

    public GoogleOAuthClient(ObjectMapper objectMapper,
                             @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
                             @Value("${spring.security.oauth2.client.registration.google.client-secret}") String googleClientSecret,
                             @Value("${app.google.oauth.redirect-uri}") String googleRedirectUri,
                             OfflineModeSupport offlineModeSupport) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                
                .build();
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = googleRedirectUri;
        this.offlineModeSupport = offlineModeSupport;
    }

    
    
    public String buildAuthorizationUrl(String stateToken) {
        
        offlineModeSupport.requireOnline("Google OAuth integration");
        validateClientConfiguration();

        
        StringBuilder url = new StringBuilder(GOOGLE_AUTH_BASE);
        url.append("?client_id=").append(encode(googleClientId));
        url.append("&redirect_uri=").append(encode(googleRedirectUri));
        
        url.append("&response_type=code");
        url.append("&scope=").append(encode(GMAIL_SCOPES));
        
        url.append("&access_type=offline");
        
        url.append("&prompt=consent");
        
        url.append("&include_granted_scopes=true");
        url.append("&state=").append(encode(stateToken));
        return url.toString();
    }

    
    
    public OAuthTokenResponse exchangeAuthorizationCode(String code) {
        
        offlineModeSupport.requireOnline("Google OAuth integration");
        validateClientConfiguration();
        String body = "client_id=" + encode(googleClientId)
                + "&client_secret=" + encode(googleClientSecret)
                + "&code=" + encode(code)
                + "&grant_type=authorization_code"
                
                + "&redirect_uri=" + encode(googleRedirectUri);

        
        JsonNode json = sendFormPost(GOOGLE_TOKEN_URL, body, "Google code exchange failed");
        return new OAuthTokenResponse(
                textOrNull(json, "access_token"),
                textOrNull(json, "refresh_token"),
                textOrNull(json, "scope"),
                textOrNull(json, "token_type"),
                longOrNull(json, "expires_in")
        );
    }

    
    
    public String refreshAccessToken(String refreshToken) {
        
        offlineModeSupport.requireOnline("Google OAuth integration");
        validateClientConfiguration();
        String body = "client_id=" + encode(googleClientId)
                + "&client_secret=" + encode(googleClientSecret)
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";

        
        JsonNode json = sendFormPost(GOOGLE_TOKEN_URL, body, "Google token refresh failed");
        
        String accessToken = textOrNull(json, "access_token");
        if (isBlank(accessToken)) {
            throw new IllegalArgumentException("Google token refresh did not return an access token");
        }
        return accessToken;
    }

    
    
    public String fetchUserEmail(String accessToken) {
        
        offlineModeSupport.requireOnline("Google OAuth integration");
        
        JsonNode json = sendBearerGet(GOOGLE_USERINFO_URL, accessToken, "Could not load Google user info");
        
        String email = textOrNull(json, "email");
        if (isBlank(email)) {
            throw new IllegalArgumentException("Google user info did not include an email");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    
    
    public List<GmailMessageSnippet> fetchRecentMessageSnippets(String accessToken, int maxMessages) {
        
        offlineModeSupport.requireOnline("Gmail integration");
        
        String listUrl = GMAIL_MESSAGES_LIST_URL + "?maxResults=" + Math.max(1, maxMessages);
        
        JsonNode listJson = sendBearerGet(listUrl, accessToken, "Could not list Gmail messages");
        
        JsonNode messages = listJson.path("messages");

        List<GmailMessageSnippet> results = new ArrayList<>();
        if (!messages.isArray()) {
            return results;
        }

        for (JsonNode message : messages) {
            
            String id = textOrNull(message, "id");
            if (isBlank(id)) {
                continue;
            }
            String getUrl = String.format(GMAIL_MESSAGE_GET_URL, encodePathSegment(id));
            
            JsonNode messageJson = sendBearerGet(getUrl, accessToken, "Could not load Gmail message");
            
            String subject = extractSubject(messageJson);
            
            String snippet = textOrNull(messageJson, "snippet");
            results.add(new GmailMessageSnippet(
                    subject == null ? "" : subject,
                    snippet == null ? "" : snippet
            ));
        }
        return results;
    }

    
    
    public void replaceSheetValues(String accessToken,
                                   String spreadsheetId,
                                   String clearRange,
                                   String writeRange,
                                   List<List<String>> rows) {
        
        offlineModeSupport.requireOnline("Google Sheets sync");
        if (isBlank(accessToken)) {
            throw new IllegalArgumentException("Google access token is missing");
        }
        if (isBlank(spreadsheetId)) {
            throw new IllegalArgumentException("Google spreadsheet ID is missing");
        }
        if (isBlank(clearRange) || isBlank(writeRange)) {
            throw new IllegalArgumentException("Google sheet range is missing");
        }

        try {
            
            String encodedSpreadsheetId = encodePathSegment(spreadsheetId);
            
            String encodedClearRange = encodePathSegment(clearRange);
            
            String encodedWriteRange = encodePathSegment(writeRange);

            String clearUrl = "https://sheets.googleapis.com/v4/spreadsheets/" + encodedSpreadsheetId
                    + "/values/" + encodedClearRange + ":clear";
            sendBearerPostJson(clearUrl, accessToken, "{}", "Could not clear Google Sheet range");

            String writeUrl = "https://sheets.googleapis.com/v4/spreadsheets/" + encodedSpreadsheetId
                    + "/values/" + encodedWriteRange + "?valueInputOption=RAW";
            String body = objectMapper.writeValueAsString(Map.of(
                    "majorDimension", "ROWS",
                    "values", rows
            ));
            sendBearerPutJson(writeUrl, accessToken, body, "Could not write rows to Google Sheet");
        
        
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Could not serialize Google Sheet payload", ex);
        }
    }

    
    
    private JsonNode sendFormPost(String url, String body, String errorPrefix) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                
                .build();
        return sendAndParseJson(request, errorPrefix);
    }

    
    
    private JsonNode sendBearerGet(String url, String accessToken, String errorPrefix) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                
                .build();
        return sendAndParseJson(request, errorPrefix);
    }

    
    
    private JsonNode sendBearerPostJson(String url, String accessToken, String body, String errorPrefix) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                
                .build();
        return sendAndParseJson(request, errorPrefix);
    }

    
    
    private JsonNode sendBearerPutJson(String url, String accessToken, String body, String errorPrefix) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                
                .build();
        return sendAndParseJson(request, errorPrefix);
    }

    
    
    private JsonNode sendAndParseJson(HttpRequest request, String errorPrefix) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = response.body() == null ? "" : response.body();
                throw new IllegalArgumentException(errorPrefix + ": HTTP " + response.statusCode() + " " + body);
            }
            
            String body = response.body();
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        
        
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(errorPrefix, e);
        
        
        } catch (IOException e) {
            throw new IllegalArgumentException(errorPrefix, e);
        }
    }

    
    
    private String extractSubject(JsonNode messageJson) {
        JsonNode headers = messageJson.path("payload").path("headers");
        if (!headers.isArray()) {
            return "";
        }
        for (JsonNode header : headers) {
            
            String name = textOrNull(header, "name");
            if ("subject".equalsIgnoreCase(name)) {
                
                String value = textOrNull(header, "value");
                return value == null ? "" : value;
            }
        }
        return "";
    }

    
    
    private void validateClientConfiguration() {
        if (isBlank(googleClientId) || googleClientId.contains("your-google-client-id")) {
            throw new IllegalArgumentException("Google OAuth client ID is not configured");
        }
        if (isBlank(googleClientSecret) || googleClientSecret.contains("your-google-client-secret")) {
            throw new IllegalArgumentException("Google OAuth client secret is not configured");
        }
    }

    
    
    private String textOrNull(JsonNode node, String fieldName) {
        
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        
        String value = child.asText();
        return value == null || value.isBlank() ? null : value;
    }

    
    
    private Long longOrNull(JsonNode node, String fieldName) {
        
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.asLong();
    }

    
    
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    
    
    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    
    
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Immutable value object representing tokens returned by Google OAuth endpoints.
     * Packages access/refresh token metadata in a strongly typed form used by integration
     * services to persist credentials and drive token refresh logic.
     */
    public record OAuthTokenResponse(String accessToken,
                                     String refreshToken,
                                     String scope,
                                     String tokenType,
                                     Long expiresInSeconds) {
    }

    /**
     * Immutable projection of key Gmail message preview fields.
     * Carries subject/snippet text needed for lightweight parsing and status detection
     * without exposing full message payload complexity to higher layers.
     */
    public record GmailMessageSnippet(String subject, String snippet) {
        
        
        public String searchText() {
            return (subject + " " + snippet).toLowerCase(Locale.ROOT);
        }
    }
}
