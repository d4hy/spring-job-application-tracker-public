package com.example.jobtracker.feature.integration.service;

import com.example.jobtracker.core.mode.OfflineModeSupport;
import com.example.jobtracker.feature.auth.model.entity.User;
import com.example.jobtracker.feature.integration.model.dto.WorkdayAutofillRequest;
import com.example.jobtracker.feature.integration.model.dto.WorkdayAutofillResponse;
import com.example.jobtracker.feature.integration.model.dto.WorkdayProfileRequest;
import com.example.jobtracker.feature.integration.model.dto.WorkdayProfileResponse;
import com.example.jobtracker.feature.integration.model.entity.WorkdayProfileSettings;
import com.example.jobtracker.feature.integration.repository.WorkdayProfileSettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service-layer component for local job-form autofill workflows.
 * Centralizes business rules, validation, and orchestration across repositories and
 * external integrations so controllers remain focused on request/response handling.
 */
@Service
public class WorkdayAutofillService {
    private static final int MAX_RESUME_BYTES = 8 * 1024 * 1024;

    private final WorkdayProfileSettingsRepository profileRepository;
    private final OfflineModeSupport offlineModeSupport;
    private final ObjectMapper objectMapper;
    private final boolean workdayAutofillEnabled;
    private final String workdayAutofillNodeCommand;
    private final String workdayAutofillScriptPath;

    public WorkdayAutofillService(
            WorkdayProfileSettingsRepository profileRepository,
            OfflineModeSupport offlineModeSupport,
            ObjectMapper objectMapper,
            @Value("${app.workday.autofill.enabled:true}") boolean workdayAutofillEnabled,
            @Value("${app.workday.autofill.node-command:node}") String workdayAutofillNodeCommand,
            @Value("${app.workday.autofill.script-path:../frontend/scripts/workday-autofill.mjs}") String workdayAutofillScriptPath
    ) {
        this.profileRepository = profileRepository;
        this.offlineModeSupport = offlineModeSupport;
        this.objectMapper = objectMapper;
        this.workdayAutofillEnabled = workdayAutofillEnabled;
        this.workdayAutofillNodeCommand = workdayAutofillNodeCommand;
        this.workdayAutofillScriptPath = workdayAutofillScriptPath;
    }

    @Transactional(readOnly = true)
    public WorkdayProfileResponse getProfile(User user) {
        Optional<WorkdayProfileSettings> settings = profileRepository.findByUser(user);
        if (settings.isEmpty()) {
            WorkdayProfileResponse empty = new WorkdayProfileResponse();
            empty.setConfigured(false);
            return empty;
        }

        WorkdayProfileSettings profile = settings.get();
        WorkdayProfileResponse response = toResponse(profile);
        response.setConfigured(hasAnyProfileField(profile));
        return response;
    }

    @Transactional
    public WorkdayProfileResponse upsertProfile(User user, WorkdayProfileRequest request) {
        WorkdayProfileSettings profile = profileRepository.findByUser(user)
                .orElseGet(WorkdayProfileSettings::new);
        profile.setUser(user);
        applyRequest(profile, request);
        WorkdayProfileSettings saved = profileRepository.save(profile);

        WorkdayProfileResponse response = toResponse(saved);
        response.setConfigured(hasAnyProfileField(saved));
        return response;
    }

    @Transactional(readOnly = true)
    public WorkdayAutofillResponse startAutofill(User user, WorkdayAutofillRequest request) {
        offlineModeSupport.requireOnline("Job form autofill");

        if (!workdayAutofillEnabled) {
            throw new IllegalArgumentException("Job form autofill is disabled by configuration.");
        }

        String url = request == null ? "" : normalize(request.getUrl());
        if (isBlank(url)) {
            throw new IllegalArgumentException("Job application URL is required.");
        }

        URI uri = toUri(url);

        WorkdayProfileSettings profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Save your autofill profile first in Integrations."));
        if (!hasAnyProfileField(profile)) {
            throw new IllegalArgumentException("Save your autofill profile first in Integrations.");
        }

        Path scriptPath = resolveScriptPath(workdayAutofillScriptPath);
        if (!Files.exists(scriptPath)) {
            throw new IllegalArgumentException("Job form autofill script not found at: " + scriptPath);
        }

        Path resumePath = null;
        Path payloadPath = null;
        String resumeFileName = request == null ? "" : normalize(request.getResumeFileName());
        try {
            resumePath = createResumeTempFileIfPresent(request);
            payloadPath = createPayloadFile(uri.toString(), profile, user.getUsername(), resumePath, resumeFileName);
            launchAutofillProcess(scriptPath, payloadPath, resumePath);
        } catch (RuntimeException e) {
            cleanupTempFile(payloadPath);
            cleanupTempFile(resumePath);
            throw e;
        }

        return new WorkdayAutofillResponse(
                true,
                "Job form autofill launched. Complete login/review in the opened browser and submit manually."
        );
    }

    private void launchAutofillProcess(Path scriptPath, Path payloadPath, Path resumePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    normalize(workdayAutofillNodeCommand),
                    scriptPath.toString(),
                    "--payload",
                    payloadPath.toString()
            );
            processBuilder.directory(new File("."));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Avoid process output buffering deadlock by draining logs asynchronously.
            Thread logDrainer = new Thread(() -> {
                try (var input = process.getInputStream()) {
                    input.transferTo(System.out);
                } catch (Exception ignored) {
                    // Best effort only.
                }
            }, "workday-autofill-log-drainer");
            logDrainer.setDaemon(true);
            logDrainer.start();

            Thread fileCleanup = new Thread(() -> {
                try {
                    process.waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    cleanupTempFile(payloadPath);
                    cleanupTempFile(resumePath);
                }
            }, "workday-autofill-payload-cleanup");
            fileCleanup.setDaemon(true);
            fileCleanup.start();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not launch job form autofill process. Verify Node.js and Playwright setup.", e);
        }
    }

    private Path createPayloadFile(
            String url,
            WorkdayProfileSettings profile,
            String username,
            Path resumePath,
            String resumeFileName
    ) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("url", url);
            payload.put("requestedAt", LocalDateTime.now().toString());
            payload.put("username", username);
            payload.put("profile", profileToMap(profile));
            if (resumePath != null) {
                payload.put("resumePath", resumePath.toString());
            }
            if (!isBlank(resumeFileName)) {
                payload.put("resumeFileName", normalize(resumeFileName));
            }

            Path tempFile = Files.createTempFile("workday-autofill-", ".json");
            objectMapper.writeValue(tempFile.toFile(), payload);
            return tempFile;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not prepare job form autofill payload.", e);
        }
    }

    private Path createResumeTempFileIfPresent(WorkdayAutofillRequest request) {
        String encoded = request == null ? "" : normalize(request.getResumeContentBase64());
        if (isBlank(encoded)) {
            return null;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Resume file upload is invalid.");
        }

        if (decoded.length == 0) {
            throw new IllegalArgumentException("Resume file is empty.");
        }
        if (decoded.length > MAX_RESUME_BYTES) {
            throw new IllegalArgumentException("Resume file is too large. Keep it under 8 MB.");
        }

        String extension = resolveResumeExtension(
                request == null ? "" : request.getResumeFileName(),
                request == null ? "" : request.getResumeMimeType()
        );
        try {
            Path tempFile = Files.createTempFile("workday-resume-", extension);
            Files.write(tempFile, decoded);
            return tempFile;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not prepare resume file for job form autofill.", e);
        }
    }

    private String resolveResumeExtension(String fileName, String mimeType) {
        String extensionFromName = extractExtensionFromFileName(fileName);
        if (!isBlank(extensionFromName)) {
            return extensionFromName;
        }

        String normalizedMime = normalize(mimeType).toLowerCase(Locale.ROOT);
        return switch (normalizedMime) {
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/rtf", "text/rtf" -> ".rtf";
            case "text/plain" -> ".txt";
            default -> ".pdf";
        };
    }

    private String extractExtensionFromFileName(String fileName) {
        String normalized = normalize(fileName);
        if (isBlank(normalized)) {
            return "";
        }

        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= normalized.length() - 1) {
            return "";
        }

        String extension = normalized.substring(dotIndex).toLowerCase(Locale.ROOT);
        if (!extension.matches("\\.[a-z0-9]{1,8}")) {
            return "";
        }
        return extension;
    }

    private void cleanupTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best effort cleanup only.
        }
    }

    private Map<String, String> profileToMap(WorkdayProfileSettings profile) {
        Map<String, String> values = new HashMap<>();
        values.put("firstName", normalize(profile.getFirstName()));
        values.put("lastName", normalize(profile.getLastName()));
        values.put("email", normalize(profile.getEmail()));
        values.put("phone", normalize(profile.getPhone()));
        values.put("addressLine1", normalize(profile.getAddressLine1()));
        values.put("addressLine2", normalize(profile.getAddressLine2()));
        values.put("city", normalize(profile.getCity()));
        values.put("stateRegion", normalize(profile.getStateRegion()));
        values.put("postalCode", normalize(profile.getPostalCode()));
        values.put("country", normalize(profile.getCountry()));
        values.put("linkedinUrl", normalize(profile.getLinkedinUrl()));
        values.put("websiteUrl", normalize(profile.getWebsiteUrl()));
        values.put("workAuthorization", normalize(profile.getWorkAuthorization()));
        return values;
    }

    private URI toUri(String value) {
        try {
            URI uri = URI.create(value);
            if (isBlank(uri.getScheme()) || isBlank(uri.getHost())) {
                throw new IllegalArgumentException("Invalid URL.");
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("URL must start with http:// or https://");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL.");
        }
    }

    private Path resolveScriptPath(String scriptPath) {
        Path path = Path.of(normalize(scriptPath));
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(".").toAbsolutePath().normalize().resolve(path).normalize();
    }

    private WorkdayProfileResponse toResponse(WorkdayProfileSettings profile) {
        WorkdayProfileResponse response = new WorkdayProfileResponse();
        response.setFirstName(normalize(profile.getFirstName()));
        response.setLastName(normalize(profile.getLastName()));
        response.setEmail(normalize(profile.getEmail()));
        response.setPhone(normalize(profile.getPhone()));
        response.setAddressLine1(normalize(profile.getAddressLine1()));
        response.setAddressLine2(normalize(profile.getAddressLine2()));
        response.setCity(normalize(profile.getCity()));
        response.setStateRegion(normalize(profile.getStateRegion()));
        response.setPostalCode(normalize(profile.getPostalCode()));
        response.setCountry(normalize(profile.getCountry()));
        response.setLinkedinUrl(normalize(profile.getLinkedinUrl()));
        response.setWebsiteUrl(normalize(profile.getWebsiteUrl()));
        response.setWorkAuthorization(normalize(profile.getWorkAuthorization()));
        return response;
    }

    private void applyRequest(WorkdayProfileSettings profile, WorkdayProfileRequest request) {
        profile.setFirstName(normalize(request.getFirstName()));
        profile.setLastName(normalize(request.getLastName()));
        profile.setEmail(normalize(request.getEmail()));
        profile.setPhone(normalize(request.getPhone()));
        profile.setAddressLine1(normalize(request.getAddressLine1()));
        profile.setAddressLine2(normalize(request.getAddressLine2()));
        profile.setCity(normalize(request.getCity()));
        profile.setStateRegion(normalize(request.getStateRegion()));
        profile.setPostalCode(normalize(request.getPostalCode()));
        profile.setCountry(normalize(request.getCountry()));
        profile.setLinkedinUrl(normalize(request.getLinkedinUrl()));
        profile.setWebsiteUrl(normalize(request.getWebsiteUrl()));
        profile.setWorkAuthorization(normalize(request.getWorkAuthorization()));
    }

    private boolean hasAnyProfileField(WorkdayProfileSettings profile) {
        return !isBlank(profile.getFirstName())
                || !isBlank(profile.getLastName())
                || !isBlank(profile.getEmail())
                || !isBlank(profile.getPhone())
                || !isBlank(profile.getAddressLine1())
                || !isBlank(profile.getAddressLine2())
                || !isBlank(profile.getCity())
                || !isBlank(profile.getStateRegion())
                || !isBlank(profile.getPostalCode())
                || !isBlank(profile.getCountry())
                || !isBlank(profile.getLinkedinUrl())
                || !isBlank(profile.getWebsiteUrl())
                || !isBlank(profile.getWorkAuthorization());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return isBlank(value) ? "" : value.trim();
    }
}
