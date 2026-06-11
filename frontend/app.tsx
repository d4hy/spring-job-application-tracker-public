import { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { Moon, Pencil, Sun } from "lucide-react";

const TOKEN_KEY = "jobTracker.token";
const USER_KEY = "jobTracker.username";
const THEME_KEY = "jobTracker.theme";
const GOOGLE_SHEET_KEY = "jobTracker.googleSheetUrl";
const UNAUTHORIZED_MARKER = "__UNAUTHORIZED__";
const API_BASE_KEY = "jobTracker.apiBaseUrl";
const DEFAULT_API_BASE = window.location.port === "8080" ? "" : "http://localhost:8080";
const API_BASE = (localStorage.getItem(API_BASE_KEY) || DEFAULT_API_BASE).replace(/\/+$/, "");
const RESOLVED_API_BASE = API_BASE || window.location.origin;
const DEFAULT_JOB_BOARD_NAME = "Internships";
const JOB_BOARD_OPTIONS = ["Internships", "Full-Time Jobs"];
const EMPTY_SCRAPE_SAVE_INFO = {
  jobId: null,
  saveStatus: "",
  source: "",
  inputUrl: ""
};
const EMPTY_WORKDAY_PROFILE = {
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  stateRegion: "",
  postalCode: "",
  country: "",
  linkedinUrl: "",
  websiteUrl: "",
  workAuthorization: ""
};
const MAX_WORKDAY_RESUME_BYTES = 8 * 1024 * 1024;
const EMPTY_JOB_EDIT_DRAFT = {
  jobTitle: "",
  companyName: "",
  location: "",
  salary: "",
  jobUrl: "",
  notes: ""
};
const TRACKING_QUERY_KEYS = new Set([
  "fbclid",
  "gclid",
  "igshid",
  "li_fat_id",
  "mc_cid",
  "mc_eid",
  "msclkid",
  "ref",
  "ref_src",
  "source",
  "trk"
]);

function apiUrl(path) {
  return API_BASE ? `${API_BASE}${path}` : path;
}

function isNetworkFetchError(error) {
  if (!(error instanceof Error)) {
    return false;
  }
  const message = (error.message || "").toLowerCase();
  return message.includes("failed to fetch")
    || message.includes("networkerror")
    || message.includes("load failed")
    || message.includes("network request failed");
}

function formatNetworkError(action) {
  return `${action}: frontend could not reach backend at ${RESOLVED_API_BASE}. `
    + `Start backend and clear custom API URL with localStorage.removeItem("${API_BASE_KEY}") if needed.`;
}

function parseError(payload, fallbackMessage) {
  if (payload && typeof payload === "object") {
    if (typeof payload.message === "string" && payload.message.trim()) {
      return payload.message;
    }
    if (Array.isArray(payload.errors) && payload.errors.length > 0) {
      return payload.errors.map((err) => err.defaultMessage || err).join(", ");
    }
  }
  return fallbackMessage;
}

async function readResponsePayload(response) {
  const rawText = await response.text().catch(() => "");
  if (!rawText) {
    return { payload: {}, rawText: "" };
  }

  try {
    return { payload: JSON.parse(rawText), rawText };
  } catch (error) {
    return { payload: {}, rawText };
  }
}

function sanitizePastedUrl(value) {
  if (typeof value !== "string") {
    return "";
  }

  let candidate = value.trim();
  if (!candidate) {
    return "";
  }

  const markdownFileSuffix = candidate.indexOf("](file://");
  if (markdownFileSuffix > 0) {
    const attachmentTokenStart = candidate.lastIndexOf("[@", markdownFileSuffix);
    candidate = attachmentTokenStart > 0
      ? candidate.slice(0, attachmentTokenStart)
      : candidate.slice(0, markdownFileSuffix);
  }

  const attachmentToken = candidate.indexOf("[@");
  if (attachmentToken > 0) {
    candidate = candidate.slice(0, attachmentToken);
  }

  const firstUrlMatch = candidate.match(/https?:\/\/[^\s]+/i);
  if (firstUrlMatch && firstUrlMatch[0]) {
    candidate = firstUrlMatch[0];
  }

  return candidate.trim();
}

function normalizeJobFormLaunchUrl(value) {
  const candidate = sanitizePastedUrl(value);
  if (!candidate) {
    return "";
  }

  try {
    return new URL(candidate).toString();
  } catch (firstError) {
    try {
      return new URL(`https://${candidate}`).toString();
    } catch (secondError) {
      return candidate;
    }
  }
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  const chunkSize = 0x8000;
  let binary = "";
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return window.btoa(binary);
}

function getStoredAuth() {
  return {
    token: localStorage.getItem(TOKEN_KEY) || "",
    username: localStorage.getItem(USER_KEY) || ""
  };
}

function setStoredAuth(token, username) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, username);
}

function clearStoredAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

function getInitialTheme() {
  const savedTheme = localStorage.getItem(THEME_KEY);
  if (savedTheme === "dark" || savedTheme === "light") {
    return savedTheme;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function toWorkdayProfile(payload) {
  if (!payload || typeof payload !== "object") {
    return { ...EMPTY_WORKDAY_PROFILE, configured: false };
  }
  return {
    configured: Boolean(payload.configured),
    firstName: typeof payload.firstName === "string" ? payload.firstName : "",
    lastName: typeof payload.lastName === "string" ? payload.lastName : "",
    email: typeof payload.email === "string" ? payload.email : "",
    phone: typeof payload.phone === "string" ? payload.phone : "",
    addressLine1: typeof payload.addressLine1 === "string" ? payload.addressLine1 : "",
    addressLine2: typeof payload.addressLine2 === "string" ? payload.addressLine2 : "",
    city: typeof payload.city === "string" ? payload.city : "",
    stateRegion: typeof payload.stateRegion === "string" ? payload.stateRegion : "",
    postalCode: typeof payload.postalCode === "string" ? payload.postalCode : "",
    country: typeof payload.country === "string" ? payload.country : "",
    linkedinUrl: typeof payload.linkedinUrl === "string" ? payload.linkedinUrl : "",
    websiteUrl: typeof payload.websiteUrl === "string" ? payload.websiteUrl : "",
    workAuthorization: typeof payload.workAuthorization === "string" ? payload.workAuthorization : ""
  };
}

function toWorkdayProfileRequest(profile) {
  const request = {};
  for (const key of Object.keys(EMPTY_WORKDAY_PROFILE)) {
    request[key] = profile && typeof profile[key] === "string" ? profile[key] : "";
  }
  return request;
}

async function scrapeAndSaveByUrl(url, token, boardName) {
  const response = await fetch(apiUrl("/api/jobs/scrape-link-and-save"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify({ url, markApplied: true, boardName })
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not scrape and save link."));
  }
  return payload;
}

async function scrapeAndSaveByText(text, url, token, boardName) {
  const response = await fetch(apiUrl("/api/jobs/scrape-text-and-save"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify({ text, url, markApplied: true, boardName })
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not parse and save pasted text."));
  }
  return payload;
}

function formatScrapeSaveStatus(payload) {
  const boardName = typeof payload?.boardName === "string" && payload.boardName.trim()
    ? payload.boardName.trim()
    : "your board";
  const statusLaneName = typeof payload?.statusLaneName === "string" && payload.statusLaneName.trim()
    ? payload.statusLaneName.trim()
    : "Saved";
  const baseMessage = payload?.saveStatus === "duplicate"
    ? `Scrape complete. Already saved in ${boardName} > ${statusLaneName}.`
    : `Scrape complete and saved to ${boardName} > ${statusLaneName}.`;

  const priorCount = Number(payload?.previousApplicationCount);
  if (!Boolean(payload?.companyPreviouslyApplied) || !Number.isFinite(priorCount) || priorCount < 1) {
    return baseMessage;
  }

  const listingCompany = typeof payload?.listing?.company === "string"
    ? payload.listing.company.trim()
    : "";
  const companyLabel = listingCompany || "this company";
  const priorLabel = priorCount === 1 ? "1 previous application" : `${priorCount} previous applications`;

  return `${baseMessage} You have ${priorLabel} at ${companyLabel}.`;
}

async function fetchGmailStatus(token) {
  const response = await fetch(apiUrl("/api/integrations/google/status"), {
    method: "GET",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not load Gmail status."));
  }
  return payload;
}

async function fetchGoogleAuthorizeUrl(token) {
  const response = await fetch(apiUrl("/api/integrations/google/authorize"), {
    method: "GET",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not start Google OAuth."));
  }
  return payload;
}

async function updateGmailAutoSync(token, enabled) {
  const response = await fetch(apiUrl("/api/integrations/google/auto-sync"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify({ enabled: Boolean(enabled) })
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not update auto sync setting."));
  }
  return payload;
}

async function syncGmailNow(token) {
  const response = await fetch(apiUrl("/api/integrations/google/sync"), {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not sync Gmail now."));
  }
  return payload;
}

async function exportBackup(token) {
  const response = await fetch(apiUrl("/api/backups/export"), {
    method: "GET",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not export local backup."));
  }
  return payload;
}

async function importBackup(token, backupPayload) {
  const response = await fetch(apiUrl("/api/backups/import"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify(backupPayload)
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not import local backup."));
  }
  return payload;
}

async function fetchCloudBackupStatus(token) {
  const response = await fetch(apiUrl("/api/backups/cloud-status"), {
    method: "GET",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not load cloud backup status."));
  }
  return payload;
}

async function uploadBackupToCloud(token) {
  const response = await fetch(apiUrl("/api/backups/cloud-upload"), {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not upload backup to cloud."));
  }
  return payload;
}

async function uploadBackupToGoogleSheet(token, spreadsheet) {
  const response = await fetch(apiUrl("/api/backups/google-sheet-upload"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify({ spreadsheet })
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not export backup to Google Sheet."));
  }
  return payload;
}

async function fetchWorkdayProfile(token) {
  const response = await fetch(apiUrl("/api/integrations/job-form/profile"), {
    method: "GET",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not load autofill profile."));
  }
  return toWorkdayProfile(payload);
}

async function saveWorkdayProfile(token, profile) {
  const response = await fetch(apiUrl("/api/integrations/job-form/profile"), {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify(toWorkdayProfileRequest(profile))
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not save autofill profile."));
  }
  return toWorkdayProfile(payload);
}

async function launchWorkdayAutofill(token, url, resumeUpload) {
  const requestBody: {
    url: string;
    resumeFileName?: string;
    resumeMimeType?: string;
    resumeContentBase64?: string;
  } = { url };
  if (resumeUpload && typeof resumeUpload.contentBase64 === "string" && resumeUpload.contentBase64.trim()) {
    requestBody.resumeFileName = typeof resumeUpload.fileName === "string" ? resumeUpload.fileName : "";
    requestBody.resumeMimeType = typeof resumeUpload.mimeType === "string" ? resumeUpload.mimeType : "";
    requestBody.resumeContentBase64 = resumeUpload.contentBase64;
  }

  const response = await fetch(apiUrl("/api/integrations/job-form/autofill"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify(requestBody)
  });

  const { payload, rawText } = await readResponsePayload(response);
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    const fallback = "Could not launch job form autofill.";
    const message = parseError(payload, "");
    if (message) {
      throw new Error(message);
    }
    const rawMessage = rawText.trim();
    if (rawMessage) {
      throw new Error(`${fallback} (HTTP ${response.status}): ${rawMessage.slice(0, 280)}`);
    }
    throw new Error(`${fallback} (HTTP ${response.status})`);
  }
  return payload;
}

async function setJobStatus(token, jobId, status) {
  const normalizedStatus = typeof status === "string" ? status.trim().toLowerCase() : "";
  if (!["applied", "accepted", "rejected"].includes(normalizedStatus)) {
    throw new Error("Unsupported status update.");
  }

  const response = await fetch(apiUrl(`/api/jobs/${jobId}/set-status`), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify({ status: normalizedStatus })
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not update job status."));
  }
  return payload;
}

async function updateSavedJobDetails(token, jobId, updates) {
  const response = await fetch(apiUrl(`/api/jobs/${jobId}`), {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    body: JSON.stringify(updates)
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not update saved job details."));
  }
  return payload;
}

async function deleteSavedJob(token, jobId) {
  const response = await fetch(apiUrl(`/api/jobs/${jobId}`), {
    method: "DELETE",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not delete job."));
  }
  return payload;
}

async function fetchSavedJobs(token) {
  const response = await fetch(apiUrl("/api/jobs/saved"), {
    method: "GET",
    headers: {
      "Authorization": `Bearer ${token}`
    }
  });

  const payload = await response.json().catch(() => ([]));
  if (response.status === 401 || response.status === 403) {
    throw new Error(UNAUTHORIZED_MARKER);
  }
  if (!response.ok) {
    throw new Error(parseError(payload, "Could not load saved jobs."));
  }
  return Array.isArray(payload) ? payload : [];
}

function formatSavedAt(value) {
  if (!value) {
    return "N/A";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleDateString(undefined, {
    year: "numeric",
    month: "numeric",
    day: "numeric"
  });
}

function isSameLocalDay(leftDate, rightDate) {
  return leftDate.getFullYear() === rightDate.getFullYear()
    && leftDate.getMonth() === rightDate.getMonth()
    && leftDate.getDate() === rightDate.getDate();
}

const STATE_ABBREVIATIONS = {
  "alabama": "AL",
  "alaska": "AK",
  "arizona": "AZ",
  "arkansas": "AR",
  "california": "CA",
  "colorado": "CO",
  "connecticut": "CT",
  "delaware": "DE",
  "district of columbia": "DC",
  "florida": "FL",
  "georgia": "GA",
  "hawaii": "HI",
  "idaho": "ID",
  "illinois": "IL",
  "indiana": "IN",
  "iowa": "IA",
  "kansas": "KS",
  "kentucky": "KY",
  "louisiana": "LA",
  "maine": "ME",
  "maryland": "MD",
  "massachusetts": "MA",
  "michigan": "MI",
  "minnesota": "MN",
  "mississippi": "MS",
  "missouri": "MO",
  "montana": "MT",
  "nebraska": "NE",
  "nevada": "NV",
  "new hampshire": "NH",
  "new jersey": "NJ",
  "new mexico": "NM",
  "new york": "NY",
  "north carolina": "NC",
  "north dakota": "ND",
  "ohio": "OH",
  "oklahoma": "OK",
  "oregon": "OR",
  "pennsylvania": "PA",
  "rhode island": "RI",
  "south carolina": "SC",
  "south dakota": "SD",
  "tennessee": "TN",
  "texas": "TX",
  "utah": "UT",
  "vermont": "VT",
  "virginia": "VA",
  "washington": "WA",
  "west virginia": "WV",
  "wisconsin": "WI",
  "wyoming": "WY"
};

const COUNTRY_ABBREVIATIONS = {
  "united states": "US",
  "united states of america": "US",
  "usa": "US",
  "us": "US",
  "canada": "CA",
  "united kingdom": "UK",
  "great britain": "UK",
  "england": "UK",
  "australia": "AU",
  "india": "IN"
};

function normalizeLocationToken(value) {
  return value.toLowerCase().replace(/\./g, "").replace(/\s+/g, " ").trim();
}

function abbreviateLocationPart(part) {
  const trimmed = part.trim();
  if (!trimmed) {
    return "";
  }

  const parentheticalMatch = trimmed.match(/^([^()]+?)(\s*\(.+\))$/);
  const rawBase = parentheticalMatch ? parentheticalMatch[1].trim() : trimmed;
  const suffix = parentheticalMatch ? parentheticalMatch[2] : "";
  const normalizedBase = normalizeLocationToken(rawBase);
  const stateAbbreviation = STATE_ABBREVIATIONS[normalizedBase];
  if (stateAbbreviation) {
    return `${stateAbbreviation}${suffix}`;
  }
  const countryAbbreviation = COUNTRY_ABBREVIATIONS[normalizedBase];
  if (countryAbbreviation) {
    return `${countryAbbreviation}${suffix}`;
  }
  return trimmed;
}

function formatCompactLocation(value) {
  if (!value || typeof value !== "string") {
    return "Not found";
  }

  const compactValue = value.replace(/\s+/g, " ").trim();
  if (!compactValue) {
    return "Not found";
  }

  const parts = compactValue.split(",").map((part) => abbreviateLocationPart(part)).filter(Boolean);
  if (parts.length === 0) {
    return "Not found";
  }

  // Keep US locations tighter in the table: "City, ST, US" -> "City, ST".
  if (parts.length >= 2 && parts[parts.length - 1] === "US") {
    parts.pop();
  }

  return parts.join(", ");
}

function downloadJson(filename, payload) {
  const content = JSON.stringify(payload, null, 2);
  const blob = new Blob([content], { type: "application/json" });
  const downloadUrl = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = downloadUrl;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.URL.revokeObjectURL(downloadUrl);
}

function normalizeText(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function normalizePositionToken(value) {
  const normalized = normalizeText(value)
    .replace(/&/g, " and ")
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return normalized === "not found" || normalized === "unknown" ? "" : normalized;
}

function normalizePositionLocation(value) {
  return normalizePositionToken(formatCompactLocation(value));
}

function isTrackingJobQueryParameter(key) {
  const normalizedKey = normalizeText(key).replace(/\[\]$/, "");
  if (!normalizedKey) {
    return false;
  }
  return normalizedKey.startsWith("utm_")
    || TRACKING_QUERY_KEYS.has(normalizedKey)
    || normalizedKey.endsWith("_source")
    || normalizedKey.endsWith("source");
}

function shouldStripAllJobQueryParameters(host) {
  const normalizedHost = normalizeText(host);
  return normalizedHost.endsWith("lever.co")
    || normalizedHost === "handshake.com"
    || normalizedHost.endsWith(".handshake.com")
    || normalizedHost.endsWith("joinhandshake.com")
    || normalizedHost.endsWith(".joinhandshake.com");
}

function normalizeComparableJobUrl(value) {
  const candidate = sanitizePastedUrl(value);
  if (!candidate) {
    return "";
  }

  try {
    const url = new URL(candidate);
    url.hash = "";

    if (shouldStripAllJobQueryParameters(url.hostname)) {
      url.search = "";
      return url.toString();
    }

    for (const key of Array.from(url.searchParams.keys())) {
      if (isTrackingJobQueryParameter(key)) {
        url.searchParams.delete(key);
      }
    }
    if (!url.searchParams.toString()) {
      url.search = "";
    }
    return url.toString();
  } catch (error) {
    return candidate.trim();
  }
}

function findSavedJobByUrl(savedJobs, url, excludedJobId = null) {
  const normalizedUrl = normalizeComparableJobUrl(url);
  if (!normalizedUrl) {
    return null;
  }
  return savedJobs.find((job) => {
    if (excludedJobId != null && String(job.id) === String(excludedJobId)) {
      return false;
    }
    return normalizeComparableJobUrl(job.jobUrl) === normalizedUrl;
  }) || null;
}

function findSavedJobByPosition(savedJobs, listing, excludedJobId = null) {
  const listingTitle = normalizePositionToken(listing?.title);
  const listingCompany = normalizePositionToken(listing?.company);
  const listingLocation = normalizePositionLocation(listing?.location);
  if (!listingTitle || !listingCompany || !listingLocation) {
    return null;
  }

  return savedJobs.find((job) => {
    if (excludedJobId != null && String(job.id) === String(excludedJobId)) {
      return false;
    }
    return normalizePositionToken(job.jobTitle) === listingTitle
      && normalizePositionToken(job.companyName) === listingCompany
      && normalizePositionLocation(job.location) === listingLocation;
  }) || null;
}

function formatSavedJobMatch(job) {
  if (!job) {
    return "";
  }
  const title = job.jobTitle || "Not found";
  const company = job.companyName || "Not found";
  const board = job.boardName || "Unknown board";
  const status = displayStatusLaneLabel(job.statusLaneName);
  return `${title} at ${company} in ${board} > ${status}, saved ${formatSavedAt(job.createdAt)}.`;
}

function isStatusLaneForStatus(statusLaneName, status) {
  const normalizedStatusLane = normalizeText(statusLaneName);
  const normalizedStatus = normalizeText(status);
  if (normalizedStatus === "accepted") {
    return normalizedStatusLane === "offer" || normalizedStatusLane === "accepted";
  }
  return normalizedStatusLane === normalizedStatus;
}

function displayStatusLaneLabel(statusLaneName) {
  const normalizedStatusLane = normalizeText(statusLaneName);
  if (normalizedStatusLane === "offer" || normalizedStatusLane === "accepted") {
    return "Accepted";
  }
  if (normalizedStatusLane === "rejected") {
    return "Rejected";
  }
  if (normalizedStatusLane === "applied") {
    return "Applied";
  }
  if (normalizedStatusLane === "wish list") {
    return "Wish List";
  }
  if (normalizedStatusLane === "interviewing") {
    return "Interviewing";
  }
  return statusLaneName || "N/A";
}

function statusBadgeClass(statusLaneName) {
  const normalizedStatusLane = normalizeText(statusLaneName);
  const baseClass = "inline-flex rounded-full border px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.06em]";

  if (normalizedStatusLane === "offer" || normalizedStatusLane === "accepted") {
    return `${baseClass} border-[#2f6f4f] bg-[#d9f0df] text-[#1f5439] dark:border-[#3f8d63] dark:bg-[#264636] dark:text-[#b8e7cb]`;
  }
  if (normalizedStatusLane === "rejected") {
    return `${baseClass} border-[#8d242f] bg-[#f4dddf] text-[#7a1f2a] dark:border-[#9c3845] dark:bg-[#4b2b32] dark:text-[#f0c0c6]`;
  }
  if (normalizedStatusLane === "applied") {
    return `${baseClass} border-[#2f5a93] bg-[#dde8f7] text-[#213f69] dark:border-[#486da6] dark:bg-[#2a3a54] dark:text-[#c4d6f3]`;
  }
  if (normalizedStatusLane === "interviewing") {
    return `${baseClass} border-[#9b6724] bg-[#f6e8d1] text-[#7c531c] dark:border-[#b28345] dark:bg-[#4a3a28] dark:text-[#f1d6a8]`;
  }
  if (normalizedStatusLane === "wish list") {
    return `${baseClass} border-[#6b5f4a] bg-[#ece3d2] text-[#4f4738] dark:border-[#7b7464] dark:bg-[#3e3a33] dark:text-[#d6ccbc]`;
  }
  return `${baseClass} border-[#5f646d] bg-[#e4e6ea] text-[#374150] dark:border-[#707784] dark:bg-[#343a46] dark:text-[#c9d0dc]`;
}

function sourceFromUrl(url) {
  if (!url || typeof url !== "string") {
    return "N/A";
  }

  const raw = url.trim();
  if (!raw) {
    return "N/A";
  }

  try {
    return new URL(raw).hostname.replace(/^www\./i, "").toLowerCase();
  } catch (firstError) {
    try {
      return new URL(`https://${raw}`).hostname.replace(/^www\./i, "").toLowerCase();
    } catch (secondError) {
      return "Unknown";
    }
  }
}

function isJobFormUrl(url) {
  if (!url || typeof url !== "string") {
    return false;
  }

  const normalizedUrl = normalizeJobFormLaunchUrl(url);
  if (!normalizedUrl) {
    return false;
  }

  try {
    const parsedUrl = new URL(normalizedUrl);
    return parsedUrl.protocol === "http:" || parsedUrl.protocol === "https:";
  } catch (firstError) {
    try {
      const parsedUrl = new URL(`https://${normalizedUrl}`);
      return parsedUrl.protocol === "http:" || parsedUrl.protocol === "https:";
    } catch (secondError) {
      return false;
    }
  }
}

function App() {
  const initialAuth = useMemo(() => getStoredAuth(), []);
  const initialParams = useMemo(() => new URLSearchParams(window.location.search), []);
  const requestedView = initialParams.get("view");
  const initialRoute = requestedView === "integrations"
    ? "integrations"
    : requestedView === "saved"
      ? "saved"
      : requestedView === "scrape"
        ? "scrape"
        : (initialAuth.token ? "scrape" : "login");

  const [theme, setTheme] = useState(getInitialTheme());
  const [view, setView] = useState(initialRoute);
  const [auth, setAuth] = useState(initialAuth);
  const [authMode, setAuthMode] = useState("login");

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loginStatus, setLoginStatus] = useState("Not logged in.");
  const [loginError, setLoginError] = useState(false);
  const [loginBusy, setLoginBusy] = useState(false);

  const [jobUrl, setJobUrl] = useState("");
  const [jobBoardName, setJobBoardName] = useState(DEFAULT_JOB_BOARD_NAME);
  const [handshakeUrl, setHandshakeUrl] = useState("");
  const [handshakeText, setHandshakeText] = useState("");
  const [handshakeModalOpen, setHandshakeModalOpen] = useState(false);
  const [scrapeStatus, setScrapeStatus] = useState("Paste a link or click Handshake Text.");
  const [scrapeError, setScrapeError] = useState(false);
  const [scrapeBusy, setScrapeBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [lastScrapeSaveInfo, setLastScrapeSaveInfo] = useState(EMPTY_SCRAPE_SAVE_INFO);
  const [savedJobs, setSavedJobs] = useState([]);
  const [savedJobsSearch, setSavedJobsSearch] = useState("");
  const [savedJobsStatus, setSavedJobsStatus] = useState("Login to view saved jobs.");
  const [savedJobsError, setSavedJobsError] = useState(false);
  const [savedJobsBusy, setSavedJobsBusy] = useState(false);
  const [updatingAction, setUpdatingAction] = useState(null);
  const [editingJobId, setEditingJobId] = useState(null);
  const [editingJobDraft, setEditingJobDraft] = useState(EMPTY_JOB_EDIT_DRAFT);
  const [selectedJobIds, setSelectedJobIds] = useState([]);
  const [deletingSelectionBusy, setDeletingSelectionBusy] = useState(false);

  const [gmailStatus, setGmailStatus] = useState({
    connected: false,
    autoSyncEnabled: false,
    gmailAddress: "",
    lastSyncedAt: null
  });
  const [gmailConnectBusy, setGmailConnectBusy] = useState(false);
  const [gmailSyncBusy, setGmailSyncBusy] = useState(false);
  const [gmailAutoBusy, setGmailAutoBusy] = useState(false);
  const [gmailMessage, setGmailMessage] = useState("Connect Google OAuth to sync Gmail job updates.");
  const [gmailError, setGmailError] = useState(false);
  const [backupMessage, setBackupMessage] = useState("Download a local backup, then upload to cloud when ready.");
  const [backupError, setBackupError] = useState(false);
  const [backupDownloadBusy, setBackupDownloadBusy] = useState(false);
  const [backupImportBusy, setBackupImportBusy] = useState(false);
  const [backupUploadBusy, setBackupUploadBusy] = useState(false);
  const [backupSheetBusy, setBackupSheetBusy] = useState(false);
  const [googleSheetUrl, setGoogleSheetUrl] = useState(localStorage.getItem(GOOGLE_SHEET_KEY) || "");
  const [cloudBackupStatus, setCloudBackupStatus] = useState({
    configured: false,
    destination: ""
  });
  const [workdayProfile, setWorkdayProfile] = useState({ ...EMPTY_WORKDAY_PROFILE, configured: false });
  const [workdayUrl, setWorkdayUrl] = useState("");
  const [workdayResumeUpload, setWorkdayResumeUpload] = useState(null);
  const [workdayResumeBusy, setWorkdayResumeBusy] = useState(false);
  const [workdayProfileBusy, setWorkdayProfileBusy] = useState(false);
  const [workdayLaunchBusy, setWorkdayLaunchBusy] = useState(false);
  const [workdayMessage, setWorkdayMessage] = useState("");
  const [workdayError, setWorkdayError] = useState(false);
  const [workdayModalOpen, setWorkdayModalOpen] = useState(false);

  const setSavedJobsSummary = (rows) => {
    setSavedJobsStatus(
      rows.length === 0
        ? "No saved jobs yet."
        : `Showing ${rows.length} saved job${rows.length === 1 ? "" : "s"}.`
    );
  };

  const resetGmailState = () => {
    setGmailStatus({
      connected: false,
      autoSyncEnabled: false,
      gmailAddress: "",
      lastSyncedAt: null
    });
    setGmailMessage("Connect Google OAuth to sync Gmail job updates.");
    setGmailError(false);
    setGmailConnectBusy(false);
    setGmailSyncBusy(false);
    setGmailAutoBusy(false);
  };

  const resetBackupState = () => {
    setCloudBackupStatus({
      configured: false,
      destination: ""
    });
    setBackupMessage("Download a local backup, then upload to cloud when ready.");
    setBackupError(false);
    setBackupDownloadBusy(false);
    setBackupImportBusy(false);
    setBackupUploadBusy(false);
  };

  const resetWorkdayState = () => {
    setWorkdayProfile({ ...EMPTY_WORKDAY_PROFILE, configured: false });
    setWorkdayUrl("");
    setWorkdayResumeUpload(null);
    setWorkdayResumeBusy(false);
    setWorkdayProfileBusy(false);
    setWorkdayLaunchBusy(false);
    setWorkdayMessage("");
    setWorkdayError(false);
    setWorkdayModalOpen(false);
  };

  const handleSessionExpired = () => {
    clearStoredAuth();
    setAuth({ token: "", username: "" });
    setView("login");
    setLoginStatus("Session expired. Please login again.");
    setLoginError(true);
    setResult(null);
    setLastScrapeSaveInfo(EMPTY_SCRAPE_SAVE_INFO);
    setSavedJobs([]);
    setSavedJobsStatus("Session expired. Please login again.");
    setSavedJobsError(true);
    setEditingJobId(null);
    setEditingJobDraft(EMPTY_JOB_EDIT_DRAFT);
    setSelectedJobIds([]);
    setDeletingSelectionBusy(false);
    resetGmailState();
    resetBackupState();
    resetWorkdayState();
  };

  const refreshSavedJobs = async (token) => {
    const rows = await fetchSavedJobs(token);
    setSavedJobs(rows);
    setSavedJobsSummary(rows);
    setSavedJobsError(false);
    return rows;
  };

  const refreshGmailStatus = async (token) => {
    const status = await fetchGmailStatus(token);
    setGmailStatus(status);
    if (status.connected) {
      setGmailMessage("Google connected. You can sync manually or enable auto sync.");
      setGmailError(false);
    }
    return status;
  };

  const refreshCloudBackupStatus = async (token) => {
    const status = await fetchCloudBackupStatus(token);
    setCloudBackupStatus({
      configured: Boolean(status && status.configured),
      destination: status && status.destination ? status.destination : ""
    });
    return status;
  };

  const refreshWorkdayProfile = async (token) => {
    const profile = await fetchWorkdayProfile(token);
    setWorkdayProfile(profile);
    setWorkdayError(false);
    return profile;
  };

  useEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
    localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const integration = params.get("integration");
    const status = params.get("integration_status");
    const message = params.get("integration_message");
    const callbackView = params.get("view");

    if (callbackView === "integrations") {
      setView("integrations");
    }

    if (integration === "google" && status) {
      setGmailError(status !== "connected");
      setGmailMessage(message || (status === "connected"
        ? "Google integration connected."
        : "Google integration failed."));
    }
  }, []);

  useEffect(() => {
    const basePath = window.location.pathname.toLowerCase().endsWith("/index.html")
      ? "/index.html"
      : "/";
    const expectedSearch = view === "login" ? "" : `?view=${view}`;
    if (window.location.pathname !== basePath || window.location.search !== expectedSearch) {
      window.history.replaceState({}, "", `${basePath}${expectedSearch}`);
    }
  }, [view]);

  useEffect(() => {
    if (!auth.token && (view === "scrape" || view === "saved" || view === "integrations")) {
      setView("login");
      setLoginStatus("Login first to continue.");
      setLoginError(true);
    }
  }, [auth, view]);

  useEffect(() => {
    let cancelled = false;

    if (!auth.token) {
      setSavedJobs([]);
      setSavedJobsStatus("Login to view saved jobs.");
      setSavedJobsError(false);
      setSavedJobsBusy(false);
      setEditingJobId(null);
      setEditingJobDraft(EMPTY_JOB_EDIT_DRAFT);
      resetGmailState();
      resetBackupState();
      resetWorkdayState();
      return () => {
        cancelled = true;
      };
    }

    const loadUserData = async () => {
      setSavedJobsBusy(true);
      setSavedJobsError(false);
      try {
        const rows = await fetchSavedJobs(auth.token);
        if (cancelled) {
          return;
        }
        setSavedJobs(rows);
        setSavedJobsSummary(rows);
      } catch (error) {
        if (cancelled) {
          return;
        }
        const message = error instanceof Error ? error.message : "Could not load saved jobs.";
        if (message === UNAUTHORIZED_MARKER) {
          handleSessionExpired();
          return;
        }
        setSavedJobsStatus(message);
        setSavedJobsError(true);
      } finally {
        if (!cancelled) {
          setSavedJobsBusy(false);
        }
      }

      try {
        await refreshGmailStatus(auth.token);
      } catch (error) {
        if (cancelled) {
          return;
        }
        const message = error instanceof Error ? error.message : "Could not load Gmail status.";
        if (message === UNAUTHORIZED_MARKER) {
          handleSessionExpired();
          return;
        }
        setGmailMessage(message);
        setGmailError(true);
      }

      try {
        await refreshCloudBackupStatus(auth.token);
      } catch (error) {
        if (cancelled) {
          return;
        }
        const message = error instanceof Error ? error.message : "Could not load cloud backup status.";
        if (message === UNAUTHORIZED_MARKER) {
          handleSessionExpired();
          return;
        }
        setBackupMessage(message);
        setBackupError(true);
      }

      try {
        await refreshWorkdayProfile(auth.token);
      } catch (error) {
        if (cancelled) {
          return;
        }
        const message = error instanceof Error ? error.message : "Could not load autofill profile.";
        if (message === UNAUTHORIZED_MARKER) {
          handleSessionExpired();
          return;
        } else {
          // Keep integrations UI clean on load; show autofill errors only on explicit user actions.
          setWorkdayError(false);
          setWorkdayMessage("");
        }
      }
    };

    loadUserData();
    return () => {
      cancelled = true;
    };
  }, [auth.token]);

  useEffect(() => {
    setSelectedJobIds((current) => {
      if (current.length === 0) {
        return current;
      }
      const validIds = new Set(savedJobs.map((job) => job.id));
      const filtered = current.filter((jobId) => validIds.has(jobId));
      return filtered.length === current.length ? current : filtered;
    });
  }, [savedJobs]);

  useEffect(() => {
    if (editingJobId == null) {
      return;
    }
    if (savedJobs.some((job) => job.id === editingJobId)) {
      return;
    }
    setEditingJobId(null);
    setEditingJobDraft(EMPTY_JOB_EDIT_DRAFT);
  }, [savedJobs, editingJobId]);

  useEffect(() => {
    if (!workdayModalOpen) {
      return undefined;
    }

    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        setWorkdayModalOpen(false);
      }
    };

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [workdayModalOpen]);

  const handleToggleTheme = () => {
    setTheme((current) => (current === "dark" ? "light" : "dark"));
  };

  const handleLogout = () => {
    clearStoredAuth();
    setAuth({ token: "", username: "" });
    setAuthMode("login");
    setResult(null);
    setLastScrapeSaveInfo(EMPTY_SCRAPE_SAVE_INFO);
    setSavedJobs([]);
    setSavedJobsStatus("Login to view saved jobs.");
    setSavedJobsError(false);
    setEditingJobId(null);
    setEditingJobDraft(EMPTY_JOB_EDIT_DRAFT);
    setSelectedJobIds([]);
    setDeletingSelectionBusy(false);
    setJobUrl("");
    setHandshakeUrl("");
    setHandshakeText("");
    setHandshakeModalOpen(false);
    resetGmailState();
    resetBackupState();
    resetWorkdayState();
    setView("login");
    setLoginStatus("Logged out.");
    setLoginError(false);
  };

  const handleLoginSubmit = async (event) => {
    event.preventDefault();

    const isRegisterMode = authMode === "register";
    const safeUsername = username.trim();
    if (!safeUsername || !password) {
      setLoginStatus("Username and password are required.");
      setLoginError(true);
      return;
    }

    setLoginBusy(true);
    setLoginStatus(isRegisterMode ? "Creating account..." : "Logging in...");
    setLoginError(false);

    try {
      const response = await fetch(apiUrl(isRegisterMode ? "/api/auth/register" : "/api/auth/login"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ username: safeUsername, password })
      });

      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        clearStoredAuth();
        setAuth({ token: "", username: "" });
        setLoginStatus(parseError(payload, isRegisterMode ? "Sign up failed." : "Login failed."));
        setLoginError(true);
        return;
      }

      if (!payload.token) {
        clearStoredAuth();
        setAuth({ token: "", username: "" });
        setLoginStatus(isRegisterMode
          ? "Account created, but token was missing."
          : "Login succeeded, but token was missing.");
        setLoginError(true);
        return;
      }

      const resolvedUsername = payload.username || safeUsername;
      setStoredAuth(payload.token, resolvedUsername);
      setAuth({ token: payload.token, username: resolvedUsername });
      setLoginStatus(isRegisterMode
        ? `Account created and logged in as ${resolvedUsername}.`
        : `Logged in as ${resolvedUsername}.`);
      setLoginError(false);
      setView("scrape");
      setScrapeStatus("Paste a link or click Handshake Text.");
      setScrapeError(false);
    } catch (error) {
      clearStoredAuth();
      setAuth({ token: "", username: "" });
      setLoginStatus(isNetworkFetchError(error)
        ? formatNetworkError(isRegisterMode ? "Sign up failed" : "Login failed")
        : (isRegisterMode ? "Network error during sign up." : "Network error during login."));
      setLoginError(true);
    } finally {
      setLoginBusy(false);
    }
  };

  const handleJobUrlChange = (event) => {
    setJobUrl(event.target.value);
    if (result || lastScrapeSaveInfo.source) {
      setResult(null);
      setLastScrapeSaveInfo(EMPTY_SCRAPE_SAVE_INFO);
    }
  };

  const handleScrapeSubmit = async (event) => {
    event.preventDefault();

    if (!auth.token) {
      setScrapeStatus("Session missing. Please login again.");
      setScrapeError(true);
      setView("login");
      return;
    }

    const safeUrl = jobUrl.trim();
    if (!safeUrl) {
      setScrapeStatus("A job URL is required.");
      setScrapeError(true);
      return;
    }

    setScrapeBusy(true);
    setScrapeStatus("Scraping link and saving...");
    setScrapeError(false);
    setResult(null);
    setLastScrapeSaveInfo(EMPTY_SCRAPE_SAVE_INFO);

    try {
      const payload = await scrapeAndSaveByUrl(safeUrl, auth.token, jobBoardName);
      const listing = payload.listing || null;
      setResult(listing);
      setLastScrapeSaveInfo({
        jobId: payload.jobId ?? null,
        saveStatus: typeof payload.saveStatus === "string" ? payload.saveStatus : "",
        source: "url",
        inputUrl: safeUrl
      });

      setScrapeStatus(formatScrapeSaveStatus(payload));
      setScrapeError(false);

      try {
        await refreshSavedJobs(auth.token);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Could not load saved jobs.";
        if (message === UNAUTHORIZED_MARKER) {
          handleSessionExpired();
          setScrapeStatus("Session expired. Please login again.");
          setScrapeError(true);
          return;
        }
        setSavedJobsStatus(message);
        setSavedJobsError(true);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown error.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        setScrapeStatus("Session expired. Please login again.");
        setScrapeError(true);
        return;
      }
      setScrapeStatus(isNetworkFetchError(error)
        ? formatNetworkError("Scrape failed")
        : message);
      setScrapeError(true);
    } finally {
      setScrapeBusy(false);
    }
  };

  const handleOpenHandshakeModal = () => {
    if (!auth.token) {
      setScrapeStatus("Session missing. Please login again.");
      setScrapeError(true);
      setView("login");
      return;
    }
    setHandshakeModalOpen(true);
  };

  const handleHandshakeScrapeSubmit = async (event) => {
    event.preventDefault();

    if (!auth.token) {
      setHandshakeModalOpen(false);
      setScrapeStatus("Session missing. Please login again.");
      setScrapeError(true);
      setView("login");
      return;
    }

    const safeText = handshakeText.trim();
    const safeUrl = handshakeUrl.trim();
    if (!safeText) {
      setScrapeStatus("Paste Handshake text before scraping.");
      setScrapeError(true);
      return;
    }

    setScrapeBusy(true);
    setScrapeStatus("Scraping Handshake text and saving...");
    setScrapeError(false);
    setResult(null);
    setLastScrapeSaveInfo(EMPTY_SCRAPE_SAVE_INFO);

    try {
      const payload = await scrapeAndSaveByText(safeText, safeUrl, auth.token, jobBoardName);
      const listing = payload.listing || null;
      setResult(listing);
      setLastScrapeSaveInfo({
        jobId: payload.jobId ?? null,
        saveStatus: typeof payload.saveStatus === "string" ? payload.saveStatus : "",
        source: "text",
        inputUrl: safeUrl
      });

      setScrapeStatus(formatScrapeSaveStatus(payload));
      setScrapeError(false);
      setHandshakeModalOpen(false);
      setHandshakeText("");
      setHandshakeUrl("");

      try {
        await refreshSavedJobs(auth.token);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Could not load saved jobs.";
        if (message === UNAUTHORIZED_MARKER) {
          handleSessionExpired();
          setScrapeStatus("Session expired. Please login again.");
          setScrapeError(true);
          return;
        }
        setSavedJobsStatus(message);
        setSavedJobsError(true);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown error.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        setScrapeStatus("Session expired. Please login again.");
        setScrapeError(true);
        return;
      }
      setScrapeStatus(isNetworkFetchError(error)
        ? formatNetworkError("Scrape failed")
        : message);
      setScrapeError(true);
    } finally {
      setScrapeBusy(false);
    }
  };

  const handleConnectGmail = async () => {
    if (!auth.token) {
      setGmailMessage("Login first.");
      setGmailError(true);
      setView("login");
      return;
    }

    setGmailConnectBusy(true);
    setGmailError(false);
    setGmailMessage("Redirecting to Google OAuth...");
    try {
      const payload = await fetchGoogleAuthorizeUrl(auth.token);
      const authUrl = payload.authorizationUrl || "";
      if (!authUrl) {
        throw new Error("Google authorization URL was missing.");
      }
      window.location.href = authUrl;
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not start Google OAuth.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setGmailMessage(message);
      setGmailError(true);
      setGmailConnectBusy(false);
    } finally {
      // Redirect path keeps this true until navigation.
    }
  };

  const handleToggleAutoSync = async (event) => {
    const enabled = event.target.checked;
    if (!auth.token) {
      setView("login");
      return;
    }

    setGmailAutoBusy(true);
    try {
      const status = await updateGmailAutoSync(auth.token, enabled);
      setGmailStatus(status);
      setGmailMessage(enabled ? "Auto sync enabled." : "Auto sync disabled.");
      setGmailError(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not update auto sync.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setGmailMessage(message);
      setGmailError(true);
    } finally {
      setGmailAutoBusy(false);
    }
  };

  const handleSyncNow = async () => {
    if (!auth.token) {
      setView("login");
      return;
    }

    setGmailSyncBusy(true);
    setGmailError(false);
    setGmailMessage("Syncing Gmail statuses...");
    try {
      const syncResult = await syncGmailNow(auth.token);
      await refreshSavedJobs(auth.token);
      await refreshGmailStatus(auth.token);
      setGmailMessage(
        `Sync complete. Checked ${syncResult.checkedEmails} emails, updated ${syncResult.updatedJobs} jobs.`
      );
      setGmailError(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not sync Gmail.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setGmailMessage(message);
      setGmailError(true);
    } finally {
      setGmailSyncBusy(false);
    }
  };

  const handleDownloadBackup = async () => {
    if (!auth.token) {
      setView("login");
      return;
    }

    setBackupDownloadBusy(true);
    setBackupError(false);
    setBackupMessage("Preparing local backup...");
    try {
      const payload = await exportBackup(auth.token);
      const stamp = (payload && payload.exportedAt ? payload.exportedAt : new Date().toISOString())
        .replace(/[:.]/g, "-");
      const filename = `job-tracker-backup-${stamp}.json`;
      downloadJson(filename, payload);
      setBackupMessage("Local backup downloaded.");
      setBackupError(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not download backup.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setBackupMessage(message);
      setBackupError(true);
    } finally {
      setBackupDownloadBusy(false);
    }
  };

  const handleImportBackupFile = async (event) => {
    const file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
    event.target.value = "";
    if (!file) {
      return;
    }
    if (!auth.token) {
      setView("login");
      return;
    }

    setBackupImportBusy(true);
    setBackupError(false);
    setBackupMessage("Importing local backup...");
    try {
      const text = await file.text();
      let payload;
      try {
        payload = JSON.parse(text);
      } catch (parseError) {
        throw new Error("Selected file is not valid JSON.");
      }

      const response = await importBackup(auth.token, payload);
      await refreshSavedJobs(auth.token);
      const summary = `Imported backup: ${response.boardsImported || 0} boards, ${response.columnsImported || 0} columns, ${response.jobsImported || 0} jobs.`;
      setBackupMessage(summary);
      setBackupError(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not import backup.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setBackupMessage(message);
      setBackupError(true);
    } finally {
      setBackupImportBusy(false);
    }
  };

  const handleUploadBackupToCloud = async () => {
    if (!auth.token) {
      setView("login");
      return;
    }

    setBackupUploadBusy(true);
    setBackupError(false);
    setBackupMessage("Uploading backup to cloud...");
    try {
      const response = await uploadBackupToCloud(auth.token);
      setBackupMessage(response && response.message ? response.message : "Backup uploaded to cloud.");
      setBackupError(!(response && response.uploaded));
      await refreshCloudBackupStatus(auth.token);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not upload backup to cloud.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setBackupMessage(message);
      setBackupError(true);
    } finally {
      setBackupUploadBusy(false);
    }
  };

  const handleUploadBackupToGoogleSheet = async () => {
    if (!auth.token) {
      setView("login");
      return;
    }

    const spreadsheet = googleSheetUrl.trim();
    if (!spreadsheet) {
      setBackupError(true);
      setBackupMessage("Paste your Google Sheet URL first.");
      return;
    }

    setBackupSheetBusy(true);
    setBackupError(false);
    setBackupMessage("Syncing backup to Google Sheet...");
    try {
      const response = await uploadBackupToGoogleSheet(auth.token, spreadsheet);
      localStorage.setItem(GOOGLE_SHEET_KEY, spreadsheet);
      const rowCount = response && typeof response.rowsWritten === "number" ? response.rowsWritten : 0;
      setBackupMessage(response && response.message ? response.message : `Backup synced to Google Sheet (${rowCount} rows).`);
      setBackupError(!(response && response.uploaded));
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not export backup to Google Sheet.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setBackupMessage(message);
      setBackupError(true);
    } finally {
      setBackupSheetBusy(false);
    }
  };

  const handleWorkdayProfileFieldChange = (field, value) => {
    setWorkdayProfile((current) => ({
      ...current,
      [field]: value
    }));
  };

  const handleWorkdayResumeSelected = async (event) => {
    const file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
    event.target.value = "";
    if (!file) {
      return;
    }

    if (file.size <= 0) {
      setWorkdayMessage("Resume file is empty.");
      setWorkdayError(true);
      return;
    }
    if (file.size > MAX_WORKDAY_RESUME_BYTES) {
      setWorkdayMessage("Resume file is too large. Keep it under 8 MB.");
      setWorkdayError(true);
      return;
    }

    setWorkdayResumeBusy(true);
    try {
      const contentBase64 = arrayBufferToBase64(await file.arrayBuffer());
      setWorkdayResumeUpload({
        fileName: file.name || "resume",
        mimeType: file.type || "",
        contentBase64
      });
      setWorkdayError(false);
      setWorkdayMessage("");
    } catch (error) {
      setWorkdayMessage("Could not read resume file.");
      setWorkdayError(true);
    } finally {
      setWorkdayResumeBusy(false);
    }
  };

  const handleClearWorkdayResume = () => {
    setWorkdayResumeUpload(null);
  };

  const handleSaveWorkdayProfile = async () => {
    if (!auth.token) {
      setView("login");
      return;
    }

    setWorkdayProfileBusy(true);
    setWorkdayError(false);
    setWorkdayMessage("");
    try {
      const savedProfile = await saveWorkdayProfile(auth.token, workdayProfile);
      setWorkdayProfile(savedProfile);
      setWorkdayError(false);
      setWorkdayMessage("");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not save autofill profile.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setWorkdayMessage(message);
      setWorkdayError(true);
    } finally {
      setWorkdayProfileBusy(false);
    }
  };

  const handleLaunchWorkdayAutofill = async () => {
    if (!auth.token) {
      setView("login");
      return;
    }

    const normalizedUrl = normalizeJobFormLaunchUrl(workdayUrl);
    if (!normalizedUrl) {
      setWorkdayMessage("Paste a job application URL first.");
      setWorkdayError(true);
      return;
    }
    if (!isJobFormUrl(normalizedUrl)) {
      setWorkdayMessage("URL must be an http or https job application link.");
      setWorkdayError(true);
      return;
    }
    if (workdayResumeBusy) {
      setWorkdayMessage("Resume is still loading. Try again in a second.");
      setWorkdayError(true);
      return;
    }
    if (normalizedUrl !== workdayUrl) {
      setWorkdayUrl(normalizedUrl);
    }

    setWorkdayLaunchBusy(true);
    setWorkdayError(false);
    setWorkdayMessage("");
    try {
      const response = await launchWorkdayAutofill(auth.token, normalizedUrl, workdayResumeUpload);
      if (!response || !response.started) {
        setWorkdayMessage(response && response.message
          ? response.message
          : "Could not launch job form autofill.");
        setWorkdayError(true);
      } else {
        setWorkdayError(false);
        setWorkdayMessage("");
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not launch job form autofill.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setWorkdayMessage(message);
      setWorkdayError(true);
    } finally {
      setWorkdayLaunchBusy(false);
    }
  };

  const handleSetJobStatus = async (jobId, status) => {
    if (!auth.token) {
      setView("login");
      return;
    }
    const normalizedStatus = normalizeText(status);
    setUpdatingAction({ jobId, status: normalizedStatus });
    try {
      await setJobStatus(auth.token, jobId, normalizedStatus);
      await refreshSavedJobs(auth.token);
      setSavedJobsError(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not update job status.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setSavedJobsStatus(message);
      setSavedJobsError(true);
    } finally {
      setUpdatingAction(null);
    }
  };

  const handleStartEditSavedJob = (job) => {
    setEditingJobId(job.id);
    setEditingJobDraft({
      jobTitle: job.jobTitle || "",
      companyName: job.companyName || "",
      location: job.location || "",
      salary: job.salary || "",
      jobUrl: job.jobUrl || "",
      notes: job.notes || ""
    });
  };

  const handleCancelEditSavedJob = () => {
    setEditingJobId(null);
    setEditingJobDraft(EMPTY_JOB_EDIT_DRAFT);
  };

  const handleEditSavedJobFieldChange = (field, value) => {
    setEditingJobDraft((current) => ({
      ...current,
      [field]: value
    }));
  };

  const handleSaveEditedJob = async () => {
    if (!auth.token) {
      setView("login");
      return;
    }
    if (editingJobId == null) {
      return;
    }

    setUpdatingAction({ jobId: editingJobId, status: "edit" });
    try {
      await updateSavedJobDetails(auth.token, editingJobId, {
        jobTitle: editingJobDraft.jobTitle,
        companyName: editingJobDraft.companyName,
        location: editingJobDraft.location,
        salary: editingJobDraft.salary,
        jobUrl: editingJobDraft.jobUrl,
        notes: editingJobDraft.notes
      });
      await refreshSavedJobs(auth.token);
      setSavedJobsError(false);
      handleCancelEditSavedJob();
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not update saved job details.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      setSavedJobsStatus(message);
      setSavedJobsError(true);
    } finally {
      setUpdatingAction(null);
    }
  };

  const handleRejectByLeftClick = (jobId) => {
    handleSetJobStatus(jobId, "rejected");
  };

  const handleAcceptByRightClick = (event, jobId) => {
    event.preventDefault();
    handleSetJobStatus(jobId, "accepted");
  };

  const handleDefaultByMiddleMouseDown = (event, jobId) => {
    if (event.button !== 1) {
      return;
    }
    event.preventDefault();
    handleSetJobStatus(jobId, "applied");
  };

  const handleToggleJobSelection = (jobId) => {
    setSelectedJobIds((current) => (
      current.includes(jobId)
        ? current.filter((id) => id !== jobId)
        : [...current, jobId]
    ));
  };

  const handleClearSelectedJobs = () => {
    setSelectedJobIds([]);
  };

  const handleSelectAllJobs = () => {
    setSelectedJobIds(savedJobs.map((job) => job.id));
  };

  const handleDeleteSelectedJobs = async (selectedIds) => {
    if (!auth.token) {
      setView("login");
      return;
    }

    if (selectedIds.length === 0) {
      return;
    }

    const confirmed = window.confirm(`Delete ${selectedIds.length} selected job${selectedIds.length === 1 ? "" : "s"}?`);
    if (!confirmed) {
      return;
    }

    setDeletingSelectionBusy(true);
    let deletedCount = 0;
    try {
      for (const jobId of selectedIds) {
        await deleteSavedJob(auth.token, jobId);
        deletedCount += 1;
      }
      const rows = await refreshSavedJobs(auth.token);
      setSelectedJobIds([]);
      setSavedJobsStatus(`Deleted ${deletedCount} job${deletedCount === 1 ? "" : "s"}. Showing ${rows.length} saved job${rows.length === 1 ? "" : "s"}.`);
      setSavedJobsError(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not delete selected jobs.";
      if (message === UNAUTHORIZED_MARKER) {
        handleSessionExpired();
        return;
      }
      const prefix = deletedCount > 0 ? `Deleted ${deletedCount} before error. ` : "";
      setSavedJobsStatus(`${prefix}${message}`);
      setSavedJobsError(true);
      try {
        await refreshSavedJobs(auth.token);
      } catch (refreshError) {
        const refreshMessage = refreshError instanceof Error ? refreshError.message : "";
        if (refreshMessage === UNAUTHORIZED_MARKER) {
          handleSessionExpired();
        }
      }
    } finally {
      setDeletingSelectionBusy(false);
    }
  };

  const title = view === "scrape"
    ? "Scrape a Job Link"
    : (view === "saved" ? "Saved Jobs" : (view === "integrations" ? "Integrations" : "Login to Continue"));
  const subtitle = view === "scrape"
    ? "Paste a job posting URL, or click Handshake Text to open a modal parser."
    : (view === "saved"
      ? "Review your saved job listings in a spreadsheet-style table."
      : (view === "integrations"
        ? "Connect Google OAuth, sync Gmail statuses, and manage local/cloud backups."
        : "Sign in first, then switch tabs from the navigation bar."));

  const cardClass = "border-2 border-[#4a4c4d] bg-[#fcfbff] p-5 shadow-[6px_6px_0_0_rgba(25,28,33,0.2)] dark:border-[#5b6170] dark:bg-[#393b3b] dark:shadow-[6px_6px_0_0_rgba(0,0,0,0.55)]";
  const navItemClass = (active, disabled) => [
    "bg-transparent p-0 text-xs uppercase tracking-[0.22em] transition-colors duration-150",
    disabled
      ? "cursor-not-allowed text-[#8f918f] dark:text-[#596074]"
      : (active
        ? "font-bold text-[#23262c] underline decoration-2 underline-offset-4 decoration-[#7a6399] dark:text-[#ece6d8] dark:decoration-[#b8a9cf]"
        : "text-[#50535a] hover:text-[#23262c] hover:underline hover:underline-offset-4 dark:text-[#b8b0a0] dark:hover:text-[#ece6d8]")
  ].join(" ");
  const inputClass = "w-full border-2 border-[#4a4c4d] bg-[#ffffff] px-3 py-2 text-sm text-[#23262c] outline-none transition focus:border-[#7a6399] focus:bg-[#f3efff] dark:border-[#5b6170] dark:bg-[#2a2d34] dark:text-[#ece6d8] dark:focus:border-[#b8a9cf] dark:focus:bg-[#343648]";
  const primaryActionClass = "border-2 border-[#4a4c4d] bg-[#7a6399] px-4 py-2 text-sm font-bold uppercase tracking-[0.08em] text-[#f2efe7] transition hover:bg-[#6a558d] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5b6170] dark:bg-[#6a558d] dark:text-[#f3ecff] dark:hover:bg-[#7a6399]";
  const secondaryActionClass = "border-2 border-[#4a4c4d] bg-[#f3efff] px-4 py-2 text-sm font-bold uppercase tracking-[0.08em] text-[#3f335a] transition hover:bg-[#e6dcfb] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5b6170] dark:bg-[#3d3552] dark:text-[#ece6ff] dark:hover:bg-[#4b4164]";
  const popupClearClass = "border-2 border-[#4a4c4d] bg-[#7a6399] px-4 py-2 text-sm font-bold uppercase tracking-[0.08em] text-[#f3ecff] transition hover:bg-[#6a558d] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5b6170] dark:bg-[#ffffff] dark:text-[#23262c] dark:hover:bg-[#f3efff]";
  const popupSelectAllClass = "border-2 border-[#4a4c4d] bg-[#7a6399] px-4 py-2 text-sm font-bold uppercase tracking-[0.08em] text-[#f3ecff] transition hover:bg-[#6a558d] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5b6170] dark:bg-[#7a6399] dark:text-[#f3ecff] dark:hover:bg-[#6a558d]";
  const popupDeleteClass = "border-2 border-[#4a4c4d] bg-[#8d242f] px-4 py-2 text-sm font-bold uppercase tracking-[0.08em] text-[#f5eceb] transition hover:bg-[#7c1f2a] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5b6170] dark:bg-[#7a2430] dark:text-[#f5d8dd] dark:hover:bg-[#902d3a]";
  const hintTextClass = "text-sm text-[#585174] dark:text-[#b8b0a0]";
  const themeToggleClass = "inline-flex h-8 w-8 items-center justify-center border-2 border-[#4a4c4d] bg-[#ffffff] text-[#3f335a] transition hover:border-[#7a6399] hover:bg-[#7a6399] hover:text-[#f3ecff] dark:border-[#5b6170] dark:bg-[#2d3038] dark:text-[#ece6d8] dark:hover:bg-[#6a558d]";
  const workdayConfigured = Boolean(workdayProfile.configured);
  const workdayStatusClass = workdayConfigured
    ? "inline-flex items-center gap-2 rounded-full border border-[#3f7a62] bg-[#d9f0df] px-3 py-1 text-[11px] font-bold uppercase tracking-[0.08em] text-[#1f5439] dark:border-[#3f8d63] dark:bg-[#264636] dark:text-[#b8e7cb]"
    : "inline-flex items-center gap-2 rounded-full border border-[#7a6399] bg-[#f3efff] px-3 py-1 text-[11px] font-bold uppercase tracking-[0.08em] text-[#4b3e66] dark:border-[#8f7ab3] dark:bg-[#30283f] dark:text-[#cdbce6]";
  const integrationStatusClass = (active) => active
    ? "inline-flex items-center gap-2 rounded-full border border-[#3f7a62] bg-[#d9f0df] px-3 py-1 text-[11px] font-bold uppercase tracking-[0.08em] text-[#1f5439] dark:border-[#3f8d63] dark:bg-[#264636] dark:text-[#b8e7cb]"
    : "inline-flex items-center gap-2 rounded-full border border-[#7a6399] bg-[#f3efff] px-3 py-1 text-[11px] font-bold uppercase tracking-[0.08em] text-[#4b3e66] dark:border-[#8f7ab3] dark:bg-[#30283f] dark:text-[#cdbce6]";
  const pillCardClass = "rounded-3xl border border-[#5c6073] bg-gradient-to-r from-[#f6f3ff] to-[#f0ecfb] p-4 dark:border-[#4d5364] dark:from-[#2a2f3a] dark:to-[#232936]";
  const pillPrimaryButtonClass = "inline-flex items-center justify-center rounded-full border border-[#4a4c4d] bg-[#7a6399] px-4 py-2 text-xs font-bold uppercase tracking-[0.1em] text-[#f2efe7] transition hover:bg-[#6a558d] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5b6170] dark:bg-[#6a558d] dark:text-[#f3ecff] dark:hover:bg-[#7a6399]";
  const pillSecondaryButtonClass = "inline-flex items-center justify-center rounded-full border border-[#666a7d] bg-[#ede7fb] px-4 py-2 text-xs font-bold uppercase tracking-[0.1em] text-[#3f335a] transition hover:bg-[#e2d8fa] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5a6073] dark:bg-[#343b4a] dark:text-[#ece6ff] dark:hover:bg-[#40485a]";
  const pillInputClass = "w-full rounded-full border border-[#6a6f82] bg-[#f9f8ff] px-4 py-2 text-sm text-[#23262c] outline-none transition focus:border-[#7a6399] focus:bg-[#ffffff] dark:border-[#5c6275] dark:bg-[#232834] dark:text-[#ece6d8] dark:focus:border-[#b8a9cf] dark:focus:bg-[#2d3340]";
  const modalSurfaceClass = "w-full max-w-6xl overflow-hidden rounded-3xl border border-[#5b5d6d] bg-[#f7f4ff] shadow-[0_24px_70px_rgba(9,10,13,0.45)] dark:border-[#4b4f61] dark:bg-[#1f2430]";
  const modalSectionClass = "rounded-3xl border border-[#5f6476] bg-[#ffffff] p-4 dark:border-[#4f5568] dark:bg-[#2a2f3a]";
  const modalInputClass = pillInputClass;
  const modalPrimaryActionClass = pillPrimaryButtonClass;
  const modalSecondaryActionClass = pillSecondaryButtonClass;
  const progressHelpText = "Progress controls: left-click Set Progress = Rejected, middle-click Set Progress = Applied (default), right-click Set Progress = Accepted.";
  const progressButtonClass = (statusLaneName) => {
    if (isStatusLaneForStatus(statusLaneName, "accepted")) {
      return "whitespace-nowrap border-2 border-[#4a4c4d] bg-[#d9f0df] px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.06em] text-[#1f5439] dark:border-[#5b6170] dark:bg-[#3f8d63] dark:text-[#d9f5e6]";
    }
    if (isStatusLaneForStatus(statusLaneName, "rejected")) {
      return "whitespace-nowrap border-2 border-[#4a4c4d] bg-[#f4dddf] px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.06em] text-[#7a1f2a] dark:border-[#5b6170] dark:bg-[#7a2430] dark:text-[#f5d8dd]";
    }
    return "whitespace-nowrap border-2 border-[#4a4c4d] bg-[#f3efff] px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.06em] text-[#3f335a] transition hover:bg-[#e6dcfb] dark:border-[#5b6170] dark:bg-[#343840] dark:text-[#ece4d6] dark:hover:bg-[#444955]";
  };
  const tableCheckboxClass = "h-4 w-4 cursor-pointer border-2 border-[#4a4c4d] accent-[#7a6399] focus:ring-2 focus:ring-[#7a6399] dark:border-[#8f7ab3] dark:bg-[#201a2f] dark:accent-[#b8a9cf]";
  const tableInlineInputClass = "w-full rounded border border-[#8a8f9d] bg-[#ffffff] px-2 py-1 text-xs text-[#23262c] outline-none transition focus:border-[#7a6399] dark:border-[#6f7688] dark:bg-[#1f2430] dark:text-[#ece6d8] dark:focus:border-[#b8a9cf]";
  const tableInlineTextareaClass = `${tableInlineInputClass} min-h-[3.25rem] resize-y`;
  const rowActionButtonClass = "whitespace-nowrap border-2 border-[#4a4c4d] bg-[#f3efff] px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.06em] text-[#3f335a] transition hover:bg-[#e6dcfb] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5b6170] dark:bg-[#343840] dark:text-[#ece4d6] dark:hover:bg-[#444955]";
  const rowSaveButtonClass = "whitespace-nowrap border-2 border-[#4a4c4d] bg-[#7a6399] px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.06em] text-[#f3ecff] transition hover:bg-[#6a558d] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#5b6170] dark:bg-[#6a558d] dark:text-[#f3ecff] dark:hover:bg-[#7a6399]";
  const hoverEditIconButtonClass = "inline-flex h-6 w-6 items-center justify-center rounded border border-[#5f566f] bg-[#f8f4ff] text-[#4f4268] transition hover:bg-[#e9defd] disabled:cursor-not-allowed disabled:opacity-60 dark:border-[#6f7688] dark:bg-[#2c3240] dark:text-[#d7ccbc] dark:hover:bg-[#394153]";
  const normalizedSavedJobsSearch = savedJobsSearch.trim().toLowerCase();
  const filteredSavedJobs = useMemo(() => {
    if (!normalizedSavedJobsSearch) {
      return savedJobs;
    }
    const terms = normalizedSavedJobsSearch.split(/\s+/).filter(Boolean);
    return savedJobs.filter((job) => {
      const haystack = `${job.jobTitle || ""} ${job.companyName || ""} ${job.boardName || ""} ${job.location || ""} ${job.notes || ""}`.toLowerCase();
      return terms.every((term) => haystack.includes(term));
    });
  }, [savedJobs, normalizedSavedJobsSearch]);
  const appliedTodayCount = useMemo(() => {
    const today = new Date();
    return savedJobs.reduce((count, job) => {
      if (normalizeText(job.statusLaneName) === "wish list") {
        return count;
      }
      const createdAt = new Date(job.createdAt);
      if (Number.isNaN(createdAt.getTime())) {
        return count;
      }
      return isSameLocalDay(createdAt, today) ? count + 1 : count;
    }, 0);
  }, [savedJobs]);
  const appliedTodayLabel = useMemo(
    () => new Date().toLocaleDateString(undefined, {
      year: "numeric",
      month: "numeric",
      day: "numeric"
    }),
    [savedJobs]
  );
  const justRecordedJobIdForCurrentUrl = useMemo(() => {
    if (normalizeText(lastScrapeSaveInfo.saveStatus) !== "saved" || lastScrapeSaveInfo.jobId == null) {
      return null;
    }
    return normalizeComparableJobUrl(lastScrapeSaveInfo.inputUrl) === normalizeComparableJobUrl(jobUrl)
      ? lastScrapeSaveInfo.jobId
      : null;
  }, [
    lastScrapeSaveInfo.saveStatus,
    lastScrapeSaveInfo.jobId,
    lastScrapeSaveInfo.inputUrl,
    jobUrl
  ]);
  const exactUrlPositionMatch = useMemo(
    () => findSavedJobByUrl(savedJobs, jobUrl, justRecordedJobIdForCurrentUrl),
    [savedJobs, jobUrl, justRecordedJobIdForCurrentUrl]
  );
  const scrapeResultMatchesCurrentInput = useMemo(() => {
    if (!result || !lastScrapeSaveInfo.source) {
      return false;
    }
    if (lastScrapeSaveInfo.source === "text") {
      return !jobUrl.trim();
    }
    return normalizeComparableJobUrl(lastScrapeSaveInfo.inputUrl) === normalizeComparableJobUrl(jobUrl);
  }, [result, lastScrapeSaveInfo.source, lastScrapeSaveInfo.inputUrl, jobUrl]);
  const samePositionMatch = useMemo(() => {
    if (!result || !scrapeResultMatchesCurrentInput) {
      return null;
    }
    const excludedJobId = normalizeText(lastScrapeSaveInfo.saveStatus) === "saved"
      ? lastScrapeSaveInfo.jobId
      : null;
    return findSavedJobByPosition(savedJobs, result, excludedJobId);
  }, [
    savedJobs,
    result,
    scrapeResultMatchesCurrentInput,
    lastScrapeSaveInfo.saveStatus,
    lastScrapeSaveInfo.jobId
  ]);
  const positionCheckerMatch = exactUrlPositionMatch || samePositionMatch;
  const shouldShowPositionChecker = Boolean(positionCheckerMatch);
  let positionCheckerMessage = "";
  if (exactUrlPositionMatch) {
    positionCheckerMessage = "Already applied through this link.";
  } else if (samePositionMatch) {
    positionCheckerMessage = "Possible same position already applied.";
  }
  const positionCheckerDetail = positionCheckerMatch
    ? formatSavedJobMatch(positionCheckerMatch)
    : "";
  const positionCheckerClass = "border-l-4 border-[#8d242f] bg-[#f6e1e4] px-3 py-2 text-[#7a1f2a] dark:border-[#c96c75] dark:bg-[#4b2b32] dark:text-[#f0c0c6]";
  const savedJobIdSet = useMemo(() => new Set(savedJobs.map((job) => job.id)), [savedJobs]);
  const selectedSavedJobIds = useMemo(
    () => selectedJobIds.filter((jobId) => savedJobIdSet.has(jobId)),
    [selectedJobIds, savedJobIdSet]
  );
  const allSavedJobsSelected = savedJobs.length > 0 && selectedSavedJobIds.length === savedJobs.length;
  const savedJobsActionBusy = Boolean(updatingAction) || deletingSelectionBusy;
  const savedJobsSelectionBusy = savedJobsActionBusy || editingJobId !== null;

  return (
    <main className="relative z-10 mx-auto w-full max-w-[1500px] space-y-6 px-4 py-8 font-['JetBrains_Mono'] text-[#1f2126] sm:px-6 lg:px-8 dark:text-[#f2ebdc]">
      <nav className="sticky top-0 z-20 border-b-2 border-[#4a4c4d] bg-[#ffffff]/95 px-1 py-3 backdrop-blur dark:border-[#5b6170] dark:bg-[#1e2127]/95" aria-label="Main navigation">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <span className="font-['Archivo_Black'] text-lg tracking-wide text-[#1b1f24] dark:text-[#f2ebdc]">SPRING JOB TRACKER</span>
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
            <button
              type="button"
              className={navItemClass(view === "login", false)}
              onClick={() => setView("login")}
            >
              Login
            </button>
            <button
              type="button"
              className={navItemClass(view === "scrape", !auth.token)}
              onClick={() => setView("scrape")}
              disabled={!auth.token}
              title={!auth.token ? "Login first to open scrape screen" : "Open scrape screen"}
            >
              Scrape
            </button>
            <button
              type="button"
              className={navItemClass(view === "saved", !auth.token)}
              onClick={() => setView("saved")}
              disabled={!auth.token}
              title={!auth.token ? "Login first to open saved jobs screen" : "Open saved jobs screen"}
            >
              Saved Jobs
            </button>
            <button
              type="button"
              className={navItemClass(view === "integrations", !auth.token)}
              onClick={() => setView("integrations")}
              disabled={!auth.token}
              title={!auth.token ? "Login first to open integrations screen" : "Open integrations screen"}
            >
              Integrations
            </button>
            <button
              type="button"
              className={themeToggleClass}
              onClick={handleToggleTheme}
              title={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
              aria-label={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
            >
              {theme === "dark"
                ? <Sun size={16} strokeWidth={2.4} aria-hidden="true" />
                : <Moon size={16} strokeWidth={2.4} aria-hidden="true" />}
            </button>
            {auth.token && (
              <button
                type="button"
                className={navItemClass(false, false)}
                onClick={handleLogout}
              >
                Logout
              </button>
            )}
          </div>
        </div>
      </nav>

      <header className="space-y-2 px-1">
        <p className="text-xs font-bold uppercase tracking-[0.24em] text-[#7a6399] dark:text-[#b8a9cf]">GMK DMG MODE</p>
        <h1 className="font-['Archivo_Black'] text-3xl tracking-wide text-[#1b1f24] dark:text-[#f2ebdc] sm:text-5xl">{title}</h1>
        <p className="max-w-3xl text-sm text-[#50535a] dark:text-[#b8b0a0] sm:text-base">{subtitle}</p>
      </header>

      {view === "login" && (
        <section className={cardClass}>
          <h2 className="mb-4 text-xl font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">{authMode === "register" ? "Sign Up" : "Login"}</h2>
          <div className="mb-4 flex flex-wrap gap-2">
            <button
              type="button"
              className={secondaryActionClass}
              onClick={() => {
                setAuthMode("login");
                setLoginStatus("Not logged in.");
                setLoginError(false);
              }}
              disabled={loginBusy || authMode === "login"}
            >
              Use Login
            </button>
            <button
              type="button"
              className={secondaryActionClass}
              onClick={() => {
                setAuthMode("register");
                setLoginStatus("Create a username and password.");
                setLoginError(false);
              }}
              disabled={loginBusy || authMode === "register"}
            >
              Create Account
            </button>
          </div>
          <form className="grid gap-4" onSubmit={handleLoginSubmit}>
            <label className="grid gap-2 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
              Username
              <input
                className={inputClass}
                type="text"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
                required
              />
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
              Password
              <input
                className={inputClass}
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete={authMode === "register" ? "new-password" : "current-password"}
                required
              />
            </label>
            <button type="submit" className={primaryActionClass} disabled={loginBusy}>
              {loginBusy
                ? (authMode === "register" ? "Creating account..." : "Logging in...")
                : (authMode === "register" ? "Create Account" : "Login")}
            </button>
          </form>
          <p className={`mt-3 text-sm ${loginError ? "text-[#8d242f] dark:text-[#c96c75]" : "text-[#7a6399] dark:text-[#b8a9cf]"}`}>
            {loginStatus}
          </p>
        </section>
      )}

      {view === "scrape" && (
        <>
          <section className={cardClass}>
            <div className="mb-4 flex items-center justify-between">
              <p className={hintTextClass}>{auth.token ? `Logged in as ${auth.username || "user"}.` : "Not logged in."}</p>
            </div>

            <form className="grid gap-4" onSubmit={handleScrapeSubmit}>
              <label className="grid gap-2 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                Save To Board
                <select
                  className={inputClass}
                  value={jobBoardName}
                  onChange={(event) => setJobBoardName(event.target.value)}
                  disabled={scrapeBusy}
                >
                  {JOB_BOARD_OPTIONS.map((boardName) => (
                    <option key={boardName} value={boardName}>{boardName}</option>
                  ))}
                </select>
              </label>
              <label className="grid gap-2 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                Job Posting URL
                <input
                  className={inputClass}
                  type="url"
                  value={jobUrl}
                  onChange={handleJobUrlChange}
                  placeholder="https://example.com/jobs/123"
                  required
                />
              </label>
              <p className={hintTextClass}>Every scrape is auto-saved to the `Applied` column on the selected board.</p>
              <p className="text-sm font-semibold text-[#4f4268] dark:text-[#cdbce6]">
                Applied today ({appliedTodayLabel}): {appliedTodayCount}
              </p>
              {shouldShowPositionChecker && (
                <div className={positionCheckerClass} role="status" aria-live="polite">
                  <p className="text-sm font-semibold">{positionCheckerMessage}</p>
                  {positionCheckerDetail && (
                    <p className="mt-1 text-xs">{positionCheckerDetail}</p>
                  )}
                </div>
              )}
              <p className={hintTextClass}>Backend API target: {RESOLVED_API_BASE}</p>
              <div className="flex flex-wrap gap-2">
                <button type="submit" className={primaryActionClass} disabled={scrapeBusy}>
                  {scrapeBusy ? "Scraping..." : "Scrape Link"}
                </button>
                <button
                  type="button"
                  className={secondaryActionClass}
                  onClick={handleOpenHandshakeModal}
                  disabled={scrapeBusy}
                >
                  Handshake Text
                </button>
              </div>
            </form>
            <p className={`mt-3 text-sm ${scrapeError ? "text-[#8d242f] dark:text-[#c96c75]" : "text-[#7a6399] dark:text-[#b8a9cf]"}`}>
              {scrapeStatus}
            </p>
          </section>

          {result && (
            <section className={cardClass}>
              <h2 className="mb-4 text-xl font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Result</h2>
              <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-[150px_1fr]">
                <dt className="font-semibold text-[#3d4148] dark:text-[#d7cfbf]">Title</dt><dd className="text-[#3d4148] dark:text-[#d7cfbf]">{result.title || "Not found"}</dd>
                <dt className="font-semibold text-[#3d4148] dark:text-[#d7cfbf]">Company</dt><dd className="text-[#3d4148] dark:text-[#d7cfbf]">{result.company || "Not found"}</dd>
                <dt className="font-semibold text-[#3d4148] dark:text-[#d7cfbf]">Location</dt><dd className="text-[#3d4148] dark:text-[#d7cfbf]">{formatCompactLocation(result.location)}</dd>
                <dt className="font-semibold text-[#3d4148] dark:text-[#d7cfbf]">Salary</dt><dd className="text-[#3d4148] dark:text-[#d7cfbf]">{result.salary || "Not listed"}</dd>
                <dt className="font-semibold text-[#3d4148] dark:text-[#d7cfbf]">Source</dt><dd className="text-[#3d4148] dark:text-[#d7cfbf]">{result.source || "unknown"}</dd>
                <dt className="font-semibold text-[#3d4148] dark:text-[#d7cfbf]">Original Link</dt>
                <dd>
                  <a className="break-all text-[#7a6399] hover:underline dark:text-[#b8a9cf]" href={result.originalLink || "#"} target="_blank" rel="noopener noreferrer">
                    {result.originalLink || "Not found"}
                  </a>
                </dd>
              </dl>
            </section>
          )}
        </>
      )}

      {view === "saved" && (
        <section className={cardClass}>
          <div className="mb-2 flex items-center gap-2">
            <h2 className="text-xl font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Saved Jobs</h2>
          </div>
          <p className={`mt-2 text-sm ${savedJobsError ? "text-[#8d242f] dark:text-[#c96c75]" : "text-[#50535a] dark:text-[#b8b0a0]"}`}>
            {savedJobsBusy ? "Loading saved jobs..." : savedJobsStatus}
          </p>
          <div className="mt-3 grid gap-2 sm:max-w-[420px]">
            <label className="text-xs font-semibold uppercase tracking-[0.08em] text-[#5a4e74] dark:text-[#b8a9cf]">
              Search Title, Company, or Board
            </label>
            <input
              className={inputClass}
              type="search"
              value={savedJobsSearch}
              onChange={(event) => setSavedJobsSearch(event.target.value)}
              placeholder="e.g. internship, full-time, openai"
              aria-label="Search saved jobs by title, company, or board"
            />
            <p className="text-xs text-[#666a70] dark:text-[#b8b0a0]">
              Showing {filteredSavedJobs.length} of {savedJobs.length} saved job{savedJobs.length === 1 ? "" : "s"}.
            </p>
          </div>
          <div className="mt-3 max-h-[32rem] overflow-auto border-2 border-[#4a4c4d] dark:border-[#687083]">
            <table className="min-w-[1480px] w-full table-fixed divide-y divide-[#bcb8ad] text-xs sm:text-sm dark:divide-[#596072]">
              <thead className="bg-[#7a6399] dark:bg-[#6a558d]">
                <tr>
                  <th scope="col" className="w-[4%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]" aria-label="Select rows">
                    <span className="sr-only">Select row</span>
                  </th>
                  <th scope="col" className="w-[16%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Title</th>
                  <th scope="col" className="w-[8%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Company</th>
                  <th scope="col" className="w-[8%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Board</th>
                  <th scope="col" className="w-[9%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Location</th>
                  <th scope="col" className="w-[8%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Salary</th>
                  <th scope="col" className="w-[7%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Source</th>
                  <th scope="col" className="w-[7%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Status</th>
                  <th scope="col" className="w-[8%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Saved</th>
                  <th scope="col" className="w-[8%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Link</th>
                  <th scope="col" className="w-[12%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Notes</th>
                  <th scope="col" className="w-[9%] px-2 py-2 text-left text-xs font-semibold uppercase tracking-wide text-[#f3ecff] dark:text-[#f3ecff]">Progress</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#c2bcae] dark:divide-[#52596b]">
                {filteredSavedJobs.length === 0 && (
                  <tr>
                    <td colSpan={12} className="px-4 py-8 text-center text-sm text-[#666a70] dark:text-[#b8b0a0]">
                      {savedJobs.length === 0
                        ? "No saved jobs yet."
                        : "No jobs match your search."}
                    </td>
                  </tr>
                )}
                {filteredSavedJobs.map((job) => {
                  const isSelected = selectedSavedJobIds.includes(job.id);
                  const isEditing = editingJobId === job.id;
                  const rowTitle = isEditing ? editingJobDraft.jobTitle : (job.jobTitle || "");
                  const rowCompany = isEditing ? editingJobDraft.companyName : (job.companyName || "");
                  const rowLocation = isEditing ? editingJobDraft.location : (job.location || "");
                  const rowSalary = isEditing ? editingJobDraft.salary : (job.salary || "");
                  const rowJobUrl = isEditing ? editingJobDraft.jobUrl : (job.jobUrl || "");
                  const rowNotes = isEditing ? editingJobDraft.notes : (job.notes || "");
                  const compactLocation = formatCompactLocation(rowLocation);
                  const titleLabel = rowTitle || "Not found";
                  const companyLabel = rowCompany || "Not found";
                  const salaryLabel = rowSalary || "Not listed";
                  const notesLabel = rowNotes || "No notes";
                  const sourceLabel = sourceFromUrl(rowJobUrl);
                  const savedAtLabel = formatSavedAt(job.createdAt);
                  const rowActionBusy = savedJobsActionBusy || (editingJobId !== null && !isEditing);
                  const rowCheckboxVisibilityClass = isSelected
                    ? "opacity-100"
                    : "opacity-0 pointer-events-none group-hover/checkbox:opacity-100 group-hover/checkbox:pointer-events-auto";
                  const rowEditVisibilityClass = isEditing
                    ? "opacity-100"
                    : "opacity-0 pointer-events-none group-hover/checkbox:opacity-100 group-hover/checkbox:pointer-events-auto";
                  return (
                  <tr key={`${job.id}-${job.createdAt || ""}`} className="bg-[#ffffff] odd:bg-[#ffffff] even:bg-[#f5f1fd] dark:bg-[#2d323c] dark:odd:bg-[#2d323c] dark:even:bg-[#252b34]">
                    <td className="group/checkbox px-2 py-1.5 align-top">
                      <div className="flex items-center gap-1">
                        <input
                          className={`${tableCheckboxClass} transition-opacity duration-150 ${rowCheckboxVisibilityClass}`}
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => handleToggleJobSelection(job.id)}
                          disabled={savedJobsSelectionBusy}
                          aria-label={`Select ${job.jobTitle || "saved job"}`}
                        />
                        {!isEditing && (
                          <button
                            type="button"
                            className={`${hoverEditIconButtonClass} transition-opacity duration-150 ${rowEditVisibilityClass}`}
                            onClick={() => handleStartEditSavedJob(job)}
                            disabled={rowActionBusy}
                            aria-label={`Edit ${job.jobTitle || "saved job"} details`}
                            title="Edit row"
                          >
                            <Pencil size={13} strokeWidth={2.1} />
                          </button>
                        )}
                      </div>
                    </td>
                    <td className="px-2 py-1.5 align-top font-semibold text-[#2f333a] dark:text-[#f2ebdc]">
                      {isEditing ? (
                        <input
                          className={tableInlineInputClass}
                          value={editingJobDraft.jobTitle}
                          onChange={(event) => handleEditSavedJobFieldChange("jobTitle", event.target.value)}
                          disabled={savedJobsActionBusy}
                          aria-label="Edit title"
                        />
                      ) : (
                        <span className="block truncate" title={titleLabel}>{titleLabel}</span>
                      )}
                    </td>
                    <td className="px-2 py-1.5 align-top text-[#3d4148] dark:text-[#e7dfd0]">
                      {isEditing ? (
                        <input
                          className={tableInlineInputClass}
                          value={editingJobDraft.companyName}
                          onChange={(event) => handleEditSavedJobFieldChange("companyName", event.target.value)}
                          disabled={savedJobsActionBusy}
                          aria-label="Edit company"
                        />
                      ) : (
                        <span className="block truncate" title={companyLabel}>{companyLabel}</span>
                      )}
                    </td>
                    <td className="px-2 py-1.5 align-top text-[#3d4148] dark:text-[#e7dfd0]">
                      <span className="block truncate" title={job.boardName || "Unknown board"}>
                        {job.boardName || "Unknown"}
                      </span>
                    </td>
                    <td className="px-2 py-1.5 align-top text-[#3d4148] dark:text-[#e7dfd0]">
                      {isEditing ? (
                        <input
                          className={tableInlineInputClass}
                          value={editingJobDraft.location}
                          onChange={(event) => handleEditSavedJobFieldChange("location", event.target.value)}
                          disabled={savedJobsActionBusy}
                          aria-label="Edit location"
                        />
                      ) : (
                        <span className="block truncate" title={rowLocation || compactLocation}>{compactLocation}</span>
                      )}
                    </td>
                    <td className="px-2 py-1.5 align-top text-[#3d4148] dark:text-[#e7dfd0]">
                      {isEditing ? (
                        <input
                          className={tableInlineInputClass}
                          value={editingJobDraft.salary}
                          onChange={(event) => handleEditSavedJobFieldChange("salary", event.target.value)}
                          disabled={savedJobsActionBusy}
                          aria-label="Edit salary"
                        />
                      ) : (
                        <span className="block truncate" title={salaryLabel}>{salaryLabel}</span>
                      )}
                    </td>
                    <td className="px-2 py-1.5 align-top text-[#3d4148] dark:text-[#e7dfd0]">
                      <span className="block truncate" title={sourceLabel}>{sourceLabel}</span>
                    </td>
                    <td className="px-2 py-1.5 align-top">
                      <span className={statusBadgeClass(job.statusLaneName)}>{displayStatusLaneLabel(job.statusLaneName)}</span>
                    </td>
                    <td className="px-2 py-1.5 align-top text-[#3d4148] dark:text-[#e7dfd0] whitespace-nowrap">{savedAtLabel}</td>
                    <td className="px-2 py-1.5 align-top">
                      {isEditing ? (
                        <input
                          className={tableInlineInputClass}
                          value={editingJobDraft.jobUrl}
                          onChange={(event) => handleEditSavedJobFieldChange("jobUrl", event.target.value)}
                          disabled={savedJobsActionBusy}
                          placeholder="https://..."
                          aria-label="Edit job URL"
                        />
                      ) : (
                        job.jobUrl ? (
                          <a
                            className="inline-block text-[#7a6399] hover:underline dark:text-[#b8a9cf]"
                            href={job.jobUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            title={job.jobUrl}
                            aria-label={`Open job link for ${job.jobTitle || "saved job"}`}
                          >
                            Open Link
                          </a>
                        ) : "N/A"
                      )}
                    </td>
                    <td className="px-2 py-1.5 align-top text-[#3d4148] dark:text-[#e7dfd0]">
                      {isEditing ? (
                        <textarea
                          className={tableInlineTextareaClass}
                          value={editingJobDraft.notes}
                          onChange={(event) => handleEditSavedJobFieldChange("notes", event.target.value)}
                          disabled={savedJobsActionBusy}
                          rows={2}
                          aria-label="Edit notes"
                        />
                      ) : (
                        <span className="block max-h-12 overflow-hidden whitespace-pre-wrap break-words text-xs leading-5" title={notesLabel}>
                          {notesLabel}
                        </span>
                      )}
                    </td>
                    <td className="px-2 py-1.5 align-top">
                      <div
                        className={isEditing
                          ? "flex flex-nowrap items-center justify-center gap-2"
                          : "flex flex-wrap items-center gap-2"}
                        role="group"
                        aria-label="Saved job row actions"
                      >
                        {isEditing ? (
                          <>
                            <button
                              type="button"
                              className={rowSaveButtonClass}
                              onClick={handleSaveEditedJob}
                              disabled={savedJobsActionBusy}
                              aria-label={`Save edits for ${job.jobTitle || "saved job"}`}
                            >
                              {Boolean(updatingAction) && updatingAction.jobId === job.id ? "Saving..." : "Save"}
                            </button>
                            <button
                              type="button"
                              className={rowActionButtonClass}
                              onClick={handleCancelEditSavedJob}
                              disabled={savedJobsActionBusy}
                              aria-label={`Cancel edits for ${job.jobTitle || "saved job"}`}
                            >
                              Cancel
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            className={progressButtonClass(job.statusLaneName)}
                            onClick={() => handleRejectByLeftClick(job.id)}
                            onMouseDown={(event) => handleDefaultByMiddleMouseDown(event, job.id)}
                            onContextMenu={(event) => handleAcceptByRightClick(event, job.id)}
                            disabled={rowActionBusy}
                            title={progressHelpText}
                            aria-label={`Set progress for ${job.jobTitle || "saved job"}. ${progressHelpText}`}
                          >
                            {Boolean(updatingAction) && updatingAction.jobId === job.id
                              ? "Saving..."
                              : "Set Progress"}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {selectedSavedJobIds.length > 0 && (
            <div className="fixed bottom-5 left-5 z-30 w-[min(460px,calc(100vw-2rem))] border-2 border-[#4a4c4d] bg-[#ffffff]/95 p-3 shadow-[6px_6px_0_0_rgba(25,28,33,0.22)] backdrop-blur dark:border-[#5b6170] dark:bg-[#2b3038]/95 dark:shadow-[6px_6px_0_0_rgba(0,0,0,0.55)]">
              <p className="text-sm font-semibold text-[#2f333a] dark:text-[#ece4d6]">
                {selectedSavedJobIds.length} selected
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                <button
                  type="button"
                  className={popupClearClass}
                  onClick={handleClearSelectedJobs}
                  disabled={savedJobsSelectionBusy}
                >
                  Clear
                </button>
                <button
                  type="button"
                  className={popupSelectAllClass}
                  onClick={handleSelectAllJobs}
                  disabled={savedJobsSelectionBusy || allSavedJobsSelected}
                >
                  Select All
                </button>
                <button
                  type="button"
                  className={popupDeleteClass}
                  onClick={() => handleDeleteSelectedJobs(selectedSavedJobIds)}
                  disabled={savedJobsSelectionBusy}
                >
                  {deletingSelectionBusy ? "Deleting..." : `Delete Selected (${selectedSavedJobIds.length})`}
                </button>
              </div>
            </div>
          )}
        </section>
      )}

      {view === "integrations" && (
        <section className={cardClass}>
          <h2 className="mb-4 text-xl font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Integrations</h2>
          <div className="space-y-3">
            <div className={pillCardClass}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <h3 className="text-base font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Gmail</h3>
                  <span className={integrationStatusClass(Boolean(gmailStatus.connected))}>
                    <span className="h-1.5 w-1.5 rounded-full bg-current" />
                    {gmailStatus.connected ? "Connected" : "Not Connected"}
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    className={pillPrimaryButtonClass}
                    onClick={handleConnectGmail}
                    disabled={gmailConnectBusy}
                  >
                    {gmailConnectBusy ? "Redirecting..." : (gmailStatus.connected ? "Reconnect" : "Connect")}
                  </button>
                  <button
                    type="button"
                    className={pillSecondaryButtonClass}
                    onClick={handleSyncNow}
                    disabled={!gmailStatus.connected || gmailSyncBusy}
                  >
                    {gmailSyncBusy ? "Syncing..." : "Sync"}
                  </button>
                </div>
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  className={Boolean(gmailStatus.autoSyncEnabled) ? pillPrimaryButtonClass : pillSecondaryButtonClass}
                  onClick={() => handleToggleAutoSync({ target: { checked: !Boolean(gmailStatus.autoSyncEnabled) } })}
                  disabled={!gmailStatus.connected || gmailAutoBusy}
                >
                  {gmailAutoBusy ? "Saving..." : (gmailStatus.autoSyncEnabled ? "Auto On" : "Auto Off")}
                </button>
                {gmailStatus.gmailAddress && <span className="text-xs text-[#4b4f57] dark:text-[#b7ad99]">{gmailStatus.gmailAddress}</span>}
                {gmailStatus.lastSyncedAt && <span className="text-xs text-[#4b4f57] dark:text-[#b7ad99]">Last: {formatSavedAt(gmailStatus.lastSyncedAt)}</span>}
              </div>
              {gmailError && <p className="mt-2 text-sm text-[#8d242f] dark:text-[#c96c75]">{gmailMessage}</p>}
            </div>

            <div className={pillCardClass}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <h3 className="text-base font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Job Form Autofill</h3>
                  <span className={workdayStatusClass}>
                    <span className="h-1.5 w-1.5 rounded-full bg-current" />
                    {workdayConfigured ? "Configured" : "Setup"}
                  </span>
                </div>
                <button
                  type="button"
                  className={pillPrimaryButtonClass}
                  onClick={() => setWorkdayModalOpen(true)}
                  disabled={workdayProfileBusy || workdayLaunchBusy}
                >
                  Open
                </button>
              </div>
              {workdayError && workdayMessage && <p className="mt-2 text-sm text-[#8d242f] dark:text-[#c96c75]">{workdayMessage}</p>}
            </div>

            <div className={pillCardClass}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <h3 className="text-base font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Backup</h3>
                  <span className={integrationStatusClass(Boolean(cloudBackupStatus.configured))}>
                    <span className="h-1.5 w-1.5 rounded-full bg-current" />
                    {cloudBackupStatus.configured ? "Cloud Ready" : "Local"}
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    className={pillSecondaryButtonClass}
                    onClick={handleDownloadBackup}
                    disabled={backupDownloadBusy || backupImportBusy || backupUploadBusy || backupSheetBusy}
                  >
                    {backupDownloadBusy ? "Preparing..." : "Download"}
                  </button>
                  <label
                    className={`${pillSecondaryButtonClass} ${(backupDownloadBusy || backupImportBusy || backupUploadBusy || backupSheetBusy) ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
                  >
                    Import
                    <input
                      className="hidden"
                      type="file"
                      accept=".json,application/json"
                      onChange={handleImportBackupFile}
                      disabled={backupDownloadBusy || backupImportBusy || backupUploadBusy || backupSheetBusy}
                    />
                  </label>
                  <button
                    type="button"
                    className={pillPrimaryButtonClass}
                    onClick={handleUploadBackupToCloud}
                    disabled={backupDownloadBusy || backupImportBusy || backupUploadBusy || backupSheetBusy || !cloudBackupStatus.configured}
                  >
                    {backupUploadBusy ? "Uploading..." : "Cloud Upload"}
                  </button>
                </div>
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <input
                  className={`${pillInputClass} min-w-[220px] flex-1`}
                  type="url"
                  placeholder="Google Sheet URL"
                  value={googleSheetUrl}
                  onChange={(event) => setGoogleSheetUrl(event.target.value)}
                  disabled={backupDownloadBusy || backupImportBusy || backupUploadBusy || backupSheetBusy}
                />
                <button
                  type="button"
                  className={pillPrimaryButtonClass}
                  onClick={handleUploadBackupToGoogleSheet}
                  disabled={backupDownloadBusy || backupImportBusy || backupUploadBusy || backupSheetBusy || !googleSheetUrl.trim()}
                >
                  {backupSheetBusy ? "Syncing..." : "Sheet Sync"}
                </button>
              </div>
              {backupError && <p className="mt-2 text-sm text-[#8d242f] dark:text-[#c96c75]">{backupMessage}</p>}
            </div>
          </div>
        </section>
      )}

      {handshakeModalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-[#10131a]/75 p-4 backdrop-blur-sm"
          onMouseDown={() => {
            if (!scrapeBusy) {
              setHandshakeModalOpen(false);
            }
          }}
        >
          <section
            className={`${modalSurfaceClass} max-w-3xl`}
            onMouseDown={(event) => event.stopPropagation()}
            aria-modal="true"
            role="dialog"
            aria-label="Handshake Text Scraper Modal"
          >
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#cfc6e1] bg-gradient-to-r from-[#f4efff] to-[#efe9fb] px-5 py-4 dark:border-[#444b5d] dark:from-[#252b38] dark:to-[#212633]">
              <div>
                <h3 className="text-xl font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Pasted Job Text Scraper</h3>
                <p className="mt-1 text-sm text-[#585174] dark:text-[#b8b0a0]">Paste the posting text and save directly to Applied on the selected board.</p>
              </div>
              <button
                type="button"
                className={modalSecondaryActionClass}
                onClick={() => setHandshakeModalOpen(false)}
                disabled={scrapeBusy}
              >
                Close
              </button>
            </div>

            <form className="grid max-h-[calc(92vh-120px)] gap-4 overflow-y-auto p-5" onSubmit={handleHandshakeScrapeSubmit}>
                <label className="grid gap-2 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                  Save To Board
                  <select
                    className={modalInputClass}
                    value={jobBoardName}
                    onChange={(event) => setJobBoardName(event.target.value)}
                    disabled={scrapeBusy}
                  >
                    {JOB_BOARD_OPTIONS.map((boardName) => (
                      <option key={boardName} value={boardName}>{boardName}</option>
                    ))}
                  </select>
                </label>

                <label className="grid gap-2 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                  Job URL (optional)
                  <input
                    className={modalInputClass}
                    type="url"
                    placeholder="https://wellfound.com/jobs/... or https://app.joinhandshake.com/stu/jobs/..."
                    value={handshakeUrl}
                    onChange={(event) => setHandshakeUrl(event.target.value)}
                    disabled={scrapeBusy}
                  />
                </label>

              <label className="grid gap-2 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                Pasted Job Text
                <textarea
                  className={`${inputClass} min-h-[220px] rounded-3xl`}
                  value={handshakeText}
                  onChange={(event) => setHandshakeText(event.target.value)}
                  placeholder={"Paste the full job text here.\nInclude title, company, location, and salary lines for best results."}
                  disabled={scrapeBusy}
                  required
                />
              </label>

              <p className={hintTextClass}>Tip: include the header block plus any labeled fields (Company, Location, Salary).</p>

              <div className="flex flex-wrap justify-end gap-2">
                <button
                  type="button"
                  className={modalSecondaryActionClass}
                  onClick={() => setHandshakeModalOpen(false)}
                  disabled={scrapeBusy}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className={modalPrimaryActionClass}
                  disabled={scrapeBusy || !handshakeText.trim()}
                >
                  {scrapeBusy ? "Scraping..." : "Scrape Pasted Text"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}

      {workdayModalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-[#10131a]/75 p-4 backdrop-blur-sm"
          onMouseDown={() => setWorkdayModalOpen(false)}
        >
          <section
            className={modalSurfaceClass}
            onMouseDown={(event) => event.stopPropagation()}
            aria-modal="true"
            role="dialog"
            aria-label="Job Form Autofill Modal"
          >
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#cfc6e1] bg-gradient-to-r from-[#f4efff] to-[#efe9fb] px-5 py-4 dark:border-[#444b5d] dark:from-[#252b38] dark:to-[#212633]">
              <div>
                <h3 className="text-xl font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Job Form Autofill</h3>
              </div>
              <div className="flex items-center gap-2">
                <span className={workdayStatusClass}>
                  <span className="h-1.5 w-1.5 rounded-full bg-current" />
                  {workdayConfigured ? "Configured" : "Setup"}
                </span>
                <button
                  type="button"
                  className={modalSecondaryActionClass}
                  onClick={() => setWorkdayModalOpen(false)}
                >
                  Close
                </button>
              </div>
            </div>

            <div className="grid max-h-[calc(92vh-120px)] gap-4 overflow-y-auto p-5 lg:grid-cols-[1.35fr_1fr]">
              <div className={modalSectionClass}>
                <h4 className="mb-3 text-base font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Profile</h4>
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    First Name
                    <input
                      className={modalInputClass}
                      value={workdayProfile.firstName}
                      onChange={(event) => handleWorkdayProfileFieldChange("firstName", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Last Name
                    <input
                      className={modalInputClass}
                      value={workdayProfile.lastName}
                      onChange={(event) => handleWorkdayProfileFieldChange("lastName", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Email
                    <input
                      className={modalInputClass}
                      type="email"
                      value={workdayProfile.email}
                      onChange={(event) => handleWorkdayProfileFieldChange("email", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Phone
                    <input
                      className={modalInputClass}
                      value={workdayProfile.phone}
                      onChange={(event) => handleWorkdayProfileFieldChange("phone", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Address Line 1
                    <input
                      className={modalInputClass}
                      value={workdayProfile.addressLine1}
                      onChange={(event) => handleWorkdayProfileFieldChange("addressLine1", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Address Line 2
                    <input
                      className={modalInputClass}
                      value={workdayProfile.addressLine2}
                      onChange={(event) => handleWorkdayProfileFieldChange("addressLine2", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    City
                    <input
                      className={modalInputClass}
                      value={workdayProfile.city}
                      onChange={(event) => handleWorkdayProfileFieldChange("city", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    State / Region
                    <input
                      className={modalInputClass}
                      value={workdayProfile.stateRegion}
                      onChange={(event) => handleWorkdayProfileFieldChange("stateRegion", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Postal Code
                    <input
                      className={modalInputClass}
                      value={workdayProfile.postalCode}
                      onChange={(event) => handleWorkdayProfileFieldChange("postalCode", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Country
                    <input
                      className={modalInputClass}
                      value={workdayProfile.country}
                      onChange={(event) => handleWorkdayProfileFieldChange("country", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    LinkedIn URL
                    <input
                      className={modalInputClass}
                      type="url"
                      value={workdayProfile.linkedinUrl}
                      onChange={(event) => handleWorkdayProfileFieldChange("linkedinUrl", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Portfolio / Website URL
                    <input
                      className={modalInputClass}
                      type="url"
                      value={workdayProfile.websiteUrl}
                      onChange={(event) => handleWorkdayProfileFieldChange("websiteUrl", event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <label className="grid gap-1 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf] sm:col-span-2">
                    Work Authorization
                    <input
                      className={modalInputClass}
                      value={workdayProfile.workAuthorization}
                      onChange={(event) => handleWorkdayProfileFieldChange("workAuthorization", event.target.value)}
                      placeholder="e.g. US Citizen, Green Card, H1B transfer"
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                </div>
                <div className="mt-4 flex flex-wrap justify-end gap-2">
                  <button
                    type="button"
                    className={modalSecondaryActionClass}
                    onClick={handleSaveWorkdayProfile}
                    disabled={workdayProfileBusy || workdayLaunchBusy}
                  >
                    {workdayProfileBusy ? "Saving..." : "Save"}
                  </button>
                </div>
              </div>

              <div className="space-y-4">
                <div className={modalSectionClass}>
                  <h4 className="text-base font-semibold text-[#1b1f24] dark:text-[#f2ebdc]">Launch</h4>
                  <label className="mt-3 grid gap-2 text-sm font-medium text-[#3d4148] dark:text-[#d7cfbf]">
                    Job URL
                    <input
                      className={modalInputClass}
                      type="url"
                      placeholder="https://company.example.com/careers/apply"
                      value={workdayUrl}
                      onChange={(event) => setWorkdayUrl(event.target.value)}
                      disabled={workdayProfileBusy || workdayLaunchBusy}
                    />
                  </label>
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <label
                      className={`${pillSecondaryButtonClass} ${(workdayProfileBusy || workdayLaunchBusy || workdayResumeBusy) ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
                    >
                      {workdayResumeBusy ? "Reading..." : (workdayResumeUpload ? "Replace Resume" : "Attach Resume")}
                      <input
                        className="hidden"
                        type="file"
                        accept=".pdf,.doc,.docx,.rtf,.txt,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/rtf,text/rtf,text/plain"
                        onChange={handleWorkdayResumeSelected}
                        disabled={workdayProfileBusy || workdayLaunchBusy || workdayResumeBusy}
                      />
                    </label>
                    {workdayResumeUpload && (
                      <button
                        type="button"
                        className={pillSecondaryButtonClass}
                        onClick={handleClearWorkdayResume}
                        disabled={workdayProfileBusy || workdayLaunchBusy || workdayResumeBusy}
                      >
                        Clear
                      </button>
                    )}
                    {workdayResumeUpload && (
                      <span className="max-w-[220px] truncate text-xs text-[#4b4f57] dark:text-[#b7ad99]" title={workdayResumeUpload.fileName}>
                        {workdayResumeUpload.fileName}
                      </span>
                    )}
                  </div>
                  <button
                    type="button"
                    className={`${modalPrimaryActionClass} mt-3 w-full`}
                    onClick={handleLaunchWorkdayAutofill}
                    disabled={workdayProfileBusy || workdayLaunchBusy || workdayResumeBusy || !workdayUrl.trim()}
                  >
                    {workdayLaunchBusy ? "Launching..." : "Launch"}
                  </button>
                  {workdayError && workdayMessage && (
                    <p className="mt-2 text-sm text-[#8d242f] dark:text-[#c96c75]">
                      {workdayMessage}
                    </p>
                  )}
                </div>
              </div>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);

function logFrontendEntryPoint() {
  console.log("Frontend entry point:", `${window.location.origin}${window.location.pathname}`);
}

function scheduleFrontendEntryPointLog() {
  const emit = () => setTimeout(logFrontendEntryPoint, 0);
  if (document.readyState === "complete") {
    emit();
    return;
  }
  window.addEventListener("load", emit, { once: true });
}

scheduleFrontendEntryPointLog();
