package com.example.jobtracker.feature.integration.model.dto;

/**
 * Request DTO for saving reusable autofill profile data.
 * These fields are used to populate common job application form inputs.
 */
public class WorkdayProfileRequest {
    /** Applicant first/given name. */
    private String firstName;

    /** Applicant last/family name. */
    private String lastName;

    /** Applicant email address. */
    private String email;

    /** Applicant phone number. */
    private String phone;

    /** Applicant street address line 1. */
    private String addressLine1;

    /** Applicant street address line 2. */
    private String addressLine2;

    /** Applicant city. */
    private String city;

    /** Applicant state/province/region. */
    private String stateRegion;

    /** Applicant postal or zip code. */
    private String postalCode;

    /** Applicant country. */
    private String country;

    /** Applicant LinkedIn profile URL. */
    private String linkedinUrl;

    /** Applicant personal website, portfolio, or GitHub URL. */
    private String websiteUrl;

    /** Free-form work authorization/visa status answer. */
    private String workAuthorization;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStateRegion() {
        return stateRegion;
    }

    public void setStateRegion(String stateRegion) {
        this.stateRegion = stateRegion;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getWorkAuthorization() {
        return workAuthorization;
    }

    public void setWorkAuthorization(String workAuthorization) {
        this.workAuthorization = workAuthorization;
    }
}
