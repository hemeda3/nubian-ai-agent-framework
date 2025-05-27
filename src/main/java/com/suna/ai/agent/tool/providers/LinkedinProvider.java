package com.Nubian.ai.agent.tool.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * Data provider for LinkedIn data.
 * Provides access to company profiles, job listings, and professional information.
 */
@Slf4j
public class LinkedinProvider extends AbstractDataProvider {
    private static final String BASE_URL = "https://linkedin-data.p.rapidapi.com";
    
    /**
     * Create a new LinkedIn provider.
     * 
     * @param apiKey The Rapid API key
     */
    public LinkedinProvider(String apiKey) {
        super(apiKey);
    }
    
    @Override
    protected void registerEndpoints() {
        // Company profile endpoint
        Map<String, Object> companyParams = new HashMap<>();
        Map<String, Object> linkProp = new HashMap<>();
        linkProp.put("type", "string");
        linkProp.put("description", "LinkedIn company URL (e.g., 'https://www.linkedin.com/company/example')");
        companyParams.put("link", linkProp);
        
        addEndpoint("company_profile", "Get company profile information", companyParams);
        
        // Job listings endpoint
        Map<String, Object> jobParams = new HashMap<>();
        Map<String, Object> jobLinkProp = new HashMap<>();
        jobLinkProp.put("type", "string");
        jobLinkProp.put("description", "LinkedIn company URL (e.g., 'https://www.linkedin.com/company/example')");
        jobParams.put("link", jobLinkProp);
        
        Map<String, Object> jobLimitProp = new HashMap<>();
        jobLimitProp.put("type", "integer");
        jobLimitProp.put("description", "Maximum number of job listings to return");
        jobParams.put("limit", jobLimitProp);
        
        addEndpoint("job_listings", "Get job listings from a company", jobParams);
        
        // Professional profile endpoint
        Map<String, Object> profileParams = new HashMap<>();
        Map<String, Object> profileLinkProp = new HashMap<>();
        profileLinkProp.put("type", "string");
        profileLinkProp.put("description", "LinkedIn profile URL (e.g., 'https://www.linkedin.com/in/johndoe')");
        profileParams.put("link", profileLinkProp);
        
        addEndpoint("professional_profile", "Get professional profile information", profileParams);
    }
    
    @Override
    public Object callEndpoint(String endpoint, Map<String, Object> payload) {
        log.info("Calling LinkedIn endpoint: {}", endpoint);
        
        // Set up common headers for all LinkedIn API calls
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key", apiKey);
        headers.set("X-RapidAPI-Host", "linkedin-data.p.rapidapi.com");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Handle different endpoints
        switch (endpoint) {
            case "company_profile":
                return getCompanyProfile(headers, payload);
            case "job_listings":
                return getJobListings(headers, payload);
            case "professional_profile":
                return getProfessionalProfile(headers, payload);
            default:
                throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        }
    }
    
    /**
     * Get company profile information from LinkedIn.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The company profile data
     */
    private Object getCompanyProfile(HttpHeaders headers, Map<String, Object> payload) {
        String companyUrl = (String) payload.get("link");
        if (companyUrl == null || companyUrl.isEmpty()) {
            throw new IllegalArgumentException("LinkedIn company URL is required");
        }
        
        String url = BASE_URL + "/api/v1/company/profile";
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get job listings from a company.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The job listings data
     */
    private Object getJobListings(HttpHeaders headers, Map<String, Object> payload)    {
        String companyUrl = (String) payload.get("link");
        if (companyUrl == null || companyUrl.isEmpty()) {
            throw new IllegalArgumentException("LinkedIn company URL is required");
        }
        
        int limit = (Integer) payload.getOrDefault("limit", 5);
        
        String url = BASE_URL + "/api/v1/company/jobs?link=" + companyUrl + "&limit=" + limit;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get professional profile information from LinkedIn.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The professional profile data
     */
    private Object getProfessionalProfile(HttpHeaders headers, Map<String, Object> payload) {
        String profileUrl = (String) payload.get("link");
        if (profileUrl == null || profileUrl.isEmpty()) {
            throw new IllegalArgumentException("LinkedIn profile URL is required");
        }
        
        String url = BASE_URL + "/api/v1/profile?link=" + profileUrl;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
}
