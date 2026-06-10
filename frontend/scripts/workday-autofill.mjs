import fs from "node:fs/promises";
import process from "node:process";
import { chromium } from "playwright";

const LOG_PREFIX = "job-form-autofill";

const FIELD_SELECTORS = {
  fullName: [
    "input[name*='fullName' i]",
    "input[id*='fullName' i]",
    "input[name*='full-name' i]",
    "input[id*='full-name' i]",
    "input[autocomplete='name']",
    "input[aria-label*='Full Name' i]",
    "input[placeholder*='Full Name' i]",
    "input[data-automation-id*='fullName' i]"
  ],
  firstName: [
    "input[name*='first' i]",
    "input[id*='first' i]",
    "input[autocomplete='given-name']",
    "input[data-automation-id*='firstName' i]",
    "input[aria-label*='First Name' i]",
    "input[placeholder*='First Name' i]"
  ],
  lastName: [
    "input[name*='last' i]",
    "input[id*='last' i]",
    "input[autocomplete='family-name']",
    "input[data-automation-id*='lastName' i]",
    "input[aria-label*='Last Name' i]",
    "input[placeholder*='Last Name' i]"
  ],
  email: [
    "input[type='email']",
    "input[name*='email' i]",
    "input[id*='email' i]",
    "input[autocomplete='email']",
    "input[data-automation-id*='email' i]",
    "input[aria-label*='Email' i]",
    "input[placeholder*='Email' i]"
  ],
  phone: [
    "input[type='tel']",
    "input[name*='phone' i]",
    "input[id*='phone' i]",
    "input[name*='mobile' i]",
    "input[id*='mobile' i]",
    "input[autocomplete='tel']",
    "input[data-automation-id*='phone' i]",
    "input[aria-label*='Phone' i]",
    "input[placeholder*='Phone' i]"
  ],
  addressLine1: [
    "input[name*='address1' i]",
    "input[id*='address1' i]",
    "input[name*='street1' i]",
    "input[id*='street1' i]",
    "input[name*='address-line1' i]",
    "input[id*='address-line1' i]",
    "input[autocomplete='address-line1']",
    "input[data-automation-id*='addressLine1' i]",
    "input[aria-label*='Address Line 1' i]",
    "input[placeholder*='Street Address' i]"
  ],
  addressLine2: [
    "input[name*='address2' i]",
    "input[id*='address2' i]",
    "input[name*='street2' i]",
    "input[id*='street2' i]",
    "input[name*='address-line2' i]",
    "input[id*='address-line2' i]",
    "input[autocomplete='address-line2']",
    "input[data-automation-id*='addressLine2' i]",
    "input[aria-label*='Address Line 2' i]"
  ],
  city: [
    "input[name*='city' i]",
    "input[id*='city' i]",
    "input[autocomplete='address-level2']",
    "input[data-automation-id*='city' i]",
    "input[aria-label*='City' i]",
    "input[placeholder*='City' i]"
  ],
  stateRegion: [
    "input[name*='state' i]",
    "input[id*='state' i]",
    "input[name*='province' i]",
    "input[id*='province' i]",
    "select[name*='state' i]",
    "select[id*='state' i]",
    "select[name*='province' i]",
    "select[id*='province' i]",
    "input[autocomplete='address-level1']",
    "select[autocomplete='address-level1']",
    "input[data-automation-id*='state' i]",
    "select[data-automation-id*='state' i]"
  ],
  postalCode: [
    "input[name*='zip' i]",
    "input[id*='zip' i]",
    "input[name*='postal' i]",
    "input[id*='postal' i]",
    "input[autocomplete='postal-code']",
    "input[data-automation-id*='postal' i]",
    "input[aria-label*='Postal' i]",
    "input[placeholder*='Postal' i]",
    "input[placeholder*='Zip' i]"
  ],
  country: [
    "input[name*='country' i]",
    "input[id*='country' i]",
    "select[name*='country' i]",
    "select[id*='country' i]",
    "input[autocomplete='country-name']",
    "select[autocomplete='country-name']",
    "input[data-automation-id*='country' i]",
    "select[data-automation-id*='country' i]"
  ],
  linkedinUrl: [
    "input[name*='linkedin' i]",
    "input[id*='linkedin' i]",
    "input[data-automation-id*='linkedin' i]",
    "input[aria-label*='LinkedIn' i]"
  ],
  websiteUrl: [
    "input[name*='website' i]",
    "input[id*='website' i]",
    "input[name*='portfolio' i]",
    "input[id*='portfolio' i]",
    "input[data-automation-id*='website' i]"
  ],
  workAuthorization: [
    "input[name*='authorization' i]",
    "input[id*='authorization' i]",
    "input[name*='authorized' i]",
    "input[id*='authorized' i]",
    "input[name*='eligibility' i]",
    "input[id*='eligibility' i]",
    "input[name*='sponsorship' i]",
    "input[id*='sponsorship' i]",
    "textarea[name*='authorization' i]",
    "textarea[id*='authorization' i]",
    "input[data-automation-id*='authorization' i]",
    "textarea[data-automation-id*='authorization' i]"
  ]
};

const FIELD_HINTS = {
  fullName: {
    include: ["full name", "legal name", "your name", "candidate name", "applicant name"],
    exclude: ["company", "employer", "reference", "school", "university", "emergency"]
  },
  firstName: {
    include: ["first name", "given name"],
    exclude: ["reference", "emergency"]
  },
  lastName: {
    include: ["last name", "family name", "surname"],
    exclude: ["reference", "emergency"]
  },
  email: {
    include: ["email", "e-mail"],
    exclude: ["reference", "recruiter"]
  },
  phone: {
    include: ["phone", "mobile", "telephone", "cell"],
    exclude: ["reference", "emergency"]
  },
  addressLine1: {
    include: ["address line 1", "street address", "address 1", "street 1"],
    exclude: ["company", "employer"]
  },
  addressLine2: {
    include: ["address line 2", "address 2", "street 2", "apartment", "apt", "suite"],
    exclude: ["company", "employer"]
  },
  city: {
    include: ["city", "town"],
    exclude: ["company", "employer"]
  },
  stateRegion: {
    include: ["state", "province", "region"],
    exclude: ["employment state", "application state"]
  },
  postalCode: {
    include: ["postal", "zip"],
    exclude: []
  },
  country: {
    include: ["country"],
    exclude: ["company"]
  },
  linkedinUrl: {
    include: ["linkedin", "linked in"],
    exclude: []
  },
  websiteUrl: {
    include: ["portfolio", "website", "personal site", "github", "web site"],
    exclude: ["company"]
  },
  workAuthorization: {
    include: ["work authorization", "authorized to work", "work eligibility", "employment authorization", "visa status"],
    exclude: []
  }
};

function parsePayloadPath(argv) {
  const markerIndex = argv.indexOf("--payload");
  if (markerIndex === -1 || !argv[markerIndex + 1]) {
    throw new Error("Missing required --payload <path> argument.");
  }
  return argv[markerIndex + 1];
}

function isBlank(value) {
  return typeof value !== "string" || value.trim().length === 0;
}

function buildProfile(rawProfile) {
  const profile = rawProfile && typeof rawProfile === "object" ? { ...rawProfile } : {};
  const firstName = typeof profile.firstName === "string" ? profile.firstName.trim() : "";
  const lastName = typeof profile.lastName === "string" ? profile.lastName.trim() : "";
  profile.fullName = [firstName, lastName].filter(Boolean).join(" ").trim();
  return profile;
}

function normalizeDescriptor(value) {
  return String(value || "")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

function descriptorMatches(descriptor, hintConfig) {
  if (!descriptor || !hintConfig) {
    return false;
  }

  const hasExcludedToken = hintConfig.exclude.some((token) => descriptor.includes(token));
  if (hasExcludedToken) {
    return false;
  }

  return hintConfig.include.some((token) => descriptor.includes(token));
}

function inferFieldFromDescriptor(descriptor, profile) {
  for (const [field, hintConfig] of Object.entries(FIELD_HINTS)) {
    const value = typeof profile[field] === "string" ? profile[field].trim() : "";
    if (!value) {
      continue;
    }
    if (descriptorMatches(descriptor, hintConfig)) {
      return field;
    }
  }
  return "";
}

async function locatorCanFill(locator) {
  try {
    const visible = await locator.isVisible();
    if (!visible) {
      return false;
    }
    const enabled = await locator.isEnabled();
    return enabled;
  } catch {
    return false;
  }
}

async function getFieldDescriptor(locator) {
  return locator.evaluate((element) => {
    const parts = [
      element.getAttribute("name"),
      element.getAttribute("id"),
      element.getAttribute("placeholder"),
      element.getAttribute("aria-label"),
      element.getAttribute("autocomplete"),
      element.getAttribute("data-automation-id")
    ];

    if (element.id) {
      const label = Array.from(document.querySelectorAll("label"))
        .find((candidate) => candidate.htmlFor === element.id);
      if (label) {
        parts.push(label.textContent);
      }
    }

    const wrappingLabel = element.closest("label");
    if (wrappingLabel) {
      parts.push(wrappingLabel.textContent);
    }

    const parentText = element.parentElement ? element.parentElement.textContent : "";
    parts.push(parentText);

    return parts.filter(Boolean).join(" ");
  }).catch(() => "");
}

async function fillIntoLocator(locator, value) {
  try {
    const tagName = await locator.evaluate((element) => element.tagName.toLowerCase());
    if (tagName === "select") {
      try {
        await locator.selectOption({ label: value });
      } catch {
        await locator.selectOption({ value });
      }
      return true;
    }

    if (tagName !== "input" && tagName !== "textarea") {
      return false;
    }

    const current = await locator.inputValue().catch(() => "");
    if (typeof current === "string" && current.trim().length > 0) {
      return false;
    }

    await locator.fill(value);
    return true;
  } catch {
    return false;
  }
}

async function fillField(page, selectors, value) {
  if (isBlank(value)) {
    return false;
  }

  for (const selector of selectors) {
    const locator = page.locator(selector);
    const count = await locator.count().catch(() => 0);
    if (count === 0) {
      continue;
    }

    for (let index = 0; index < Math.min(count, 3); index += 1) {
      const candidate = locator.nth(index);
      if (!(await locatorCanFill(candidate))) {
        continue;
      }
      if (await fillIntoLocator(candidate, value)) {
        return true;
      }
    }
  }

  return false;
}

async function fillFieldsByDescriptors(page, profile, filledFields, unresolvedFields) {
  const controls = page.locator("input:not([type='hidden']):not([type='file']):not([type='submit']):not([type='button']):not([type='checkbox']):not([type='radio']), textarea, select");
  const count = await controls.count().catch(() => 0);
  let filledCount = 0;

  for (let index = 0; index < Math.min(count, 120); index += 1) {
    const control = controls.nth(index);
    if (!(await locatorCanFill(control))) {
      continue;
    }

    const descriptor = normalizeDescriptor(await getFieldDescriptor(control));
    const field = inferFieldFromDescriptor(descriptor, profile);
    if (!field) {
      continue;
    }

    const value = typeof profile[field] === "string" ? profile[field].trim() : "";
    if (!value) {
      continue;
    }

    if (await fillIntoLocator(control, value)) {
      filledCount += 1;
      filledFields.add(field);
      unresolvedFields.delete(field);
    }
  }

  return filledCount;
}

async function setResumeOnAvailableInputs(page, resumePath) {
  const fileInputs = page.locator("input[type='file']");
  const count = await fileInputs.count().catch(() => 0);
  if (count === 0) {
    return false;
  }

  for (let index = 0; index < Math.min(count, 6); index += 1) {
    const candidate = fileInputs.nth(index);
    const enabled = await candidate.isEnabled().catch(() => false);
    if (!enabled) {
      continue;
    }
    try {
      await candidate.setInputFiles(resumePath);
      return true;
    } catch {
      // Try another file input if this one rejects the file.
    }
  }

  return false;
}

async function uploadResumeIfProvided(page, resumePath) {
  if (isBlank(resumePath)) {
    return false;
  }

  if (await setResumeOnAvailableInputs(page, resumePath)) {
    return true;
  }

  const uploadTriggerSelectors = [
    "button:has-text('Upload Resume')",
    "button:has-text('Upload resume')",
    "button:has-text('Attach Resume')",
    "button:has-text('Upload')",
    "[role='button']:has-text('Upload Resume')",
    "[data-automation-id*='resumeUpload' i]",
    "[data-automation-id*='resume' i]",
    "[data-automation-id*='upload' i]"
  ];

  for (const selector of uploadTriggerSelectors) {
    const triggers = page.locator(selector);
    const count = await triggers.count().catch(() => 0);
    if (count === 0) {
      continue;
    }

    for (let index = 0; index < Math.min(count, 3); index += 1) {
      const trigger = triggers.nth(index);
      const visible = await trigger.isVisible().catch(() => false);
      const enabled = await trigger.isEnabled().catch(() => false);
      if (!visible || !enabled) {
        continue;
      }

      await trigger.click({ timeout: 1000 }).catch(() => {});
      await page.waitForTimeout(700);
      if (await setResumeOnAvailableInputs(page, resumePath)) {
        return true;
      }
    }
  }

  return false;
}

async function runFillPass(page, profile, filledFields, unresolvedFields) {
  let filledThisPass = 0;

  for (const [field, selectors] of Object.entries(FIELD_SELECTORS)) {
    if (filledFields.has(field)) {
      continue;
    }
    const value = typeof profile[field] === "string" ? profile[field].trim() : "";
    if (!value) {
      continue;
    }
    const didFill = await fillField(page, selectors, value);
    if (didFill) {
      filledThisPass += 1;
      filledFields.add(field);
      unresolvedFields.delete(field);
    } else {
      unresolvedFields.add(field);
    }
  }

  filledThisPass += await fillFieldsByDescriptors(page, profile, filledFields, unresolvedFields);

  return filledThisPass;
}

async function main() {
  const payloadPath = parsePayloadPath(process.argv.slice(2));
  const payloadText = await fs.readFile(payloadPath, "utf8");
  const payload = JSON.parse(payloadText);

  const url = typeof payload.url === "string" ? payload.url.trim() : "";
  const profile = buildProfile(payload.profile);
  const resumePath = typeof payload.resumePath === "string" ? payload.resumePath.trim() : "";
  const resumeFileName = typeof payload.resumeFileName === "string" ? payload.resumeFileName.trim() : "";
  if (!url) {
    throw new Error("Payload must include a valid URL.");
  }

  console.log(`[${LOG_PREFIX}] Launching browser for: ${url}`);
  const browser = await chromium.launch({ headless: false, slowMo: 50 });
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 120000 });
  console.log(`[${LOG_PREFIX}] If login is required, complete it in the opened browser.`);
  await page.waitForTimeout(6000);

  if (!isBlank(resumePath)) {
    console.log(`[${LOG_PREFIX}] Uploading resume${resumeFileName ? ` (${resumeFileName})` : ""}...`);
    let uploaded = await uploadResumeIfProvided(page, resumePath);
    for (let attempt = 0; !uploaded && attempt < 3; attempt += 1) {
      console.log(`[${LOG_PREFIX}] Resume control not ready yet. Waiting for page/login state...`);
      await page.waitForTimeout(5000);
      uploaded = await uploadResumeIfProvided(page, resumePath);
    }
    if (uploaded) {
      console.log(`[${LOG_PREFIX}] Resume uploaded. Waiting for the application page to populate fields...`);
      await page.waitForTimeout(8000);
    } else {
      console.log(`[${LOG_PREFIX}] Resume upload control not found. Continuing with profile autofill only.`);
    }
  }

  const filledFields = new Set();
  const unresolvedFields = new Set();
  for (let pass = 0; pass < 3; pass += 1) {
    await runFillPass(page, profile, filledFields, unresolvedFields);
    await page.waitForTimeout(1200);
  }

  console.log(
    `[${LOG_PREFIX}] Autofill attempted. Filled matches: ${filledFields.size}. `
    + `Unmatched fields: ${unresolvedFields.size}.`
  );
  console.log(`[${LOG_PREFIX}] Review every answer and submit manually.`);
  console.log(`[${LOG_PREFIX}] Close the browser window when finished.`);

  await Promise.race([
    browser.waitForEvent("disconnected"),
    new Promise((resolve) => setTimeout(resolve, 30 * 60 * 1000))
  ]);

  if (browser.isConnected()) {
    await browser.close();
  }
}

main().catch((error) => {
  const message = error instanceof Error ? error.message : "Unknown error";
  console.error(`[${LOG_PREFIX}] ${message}`);
  process.exitCode = 1;
});
