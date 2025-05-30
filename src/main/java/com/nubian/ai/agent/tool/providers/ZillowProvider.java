package com.nubian.ai.agent.tool.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * Data provider for Zillow real estate data.
 * Provides access to property listings, home values, and real estate market information.
 */
@Slf4j
public class ZillowProvider extends AbstractDataProvider {
    private static final String BASE_URL = "https://zillow-com1.p.rapidapi.com";
    
    /**
     * Create a new Zillow provider.
     * 
     * @param apiKey The Rapid API key
     */
    public ZillowProvider(String apiKey) {
        super(apiKey);
    }
    
    @Override
    protected void registerEndpoints() {
        // Property details endpoint
        Map<String, Object> propertyParams = new HashMap<>();
        Map<String, Object> zpidProp = new HashMap<>();
        zpidProp.put("type", "string");
        zpidProp.put("description", "Zillow Property ID (zpid)");
        propertyParams.put("zpid", zpidProp);
        
        addEndpoint("property_details", "Get detailed information about a property", propertyParams);
        
        // Property search endpoint
        Map<String, Object> searchParams = new HashMap<>();
        Map<String, Object> locationProp = new HashMap<>();
        locationProp.put("type", "string");
        locationProp.put("description", "Location (city, state, zip code, or address)");
        searchParams.put("location", locationProp);
        
        Map<String, Object> statusProp = new HashMap<>();
        statusProp.put("type", "string");
        statusProp.put("description", "Property status (for_sale, for_rent, sold)");
        searchParams.put("status", statusProp);
        
        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "Maximum number of results to return");
        searchParams.put("limit", limitProp);
        
        addEndpoint("search_properties", "Search for properties in a location", searchParams);
        
        // Home valuation endpoint
        Map<String, Object> valuationParams = new HashMap<>();
        Map<String, Object> addressProp = new HashMap<>();
        addressProp.put("type", "string");
        addressProp.put("description", "Property address");
        valuationParams.put("address", addressProp);
        
        addEndpoint("home_valuation", "Get estimated home value for an address", valuationParams);
    }
    
    @Override
    public Object callEndpoint(String endpoint, Map<String, Object> payload) {
        log.info("Calling Zillow endpoint: {}", endpoint);
        
        // Set up common headers for all Zillow API calls
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key", apiKey);
        headers.set("X-RapidAPI-Host", "zillow-com1.p.rapidapi.com");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Handle different endpoints
        switch (endpoint) {
            case "property_details":
                return getPropertyDetails(headers, payload);
            case "search_properties":
                return searchProperties(headers, payload);
            case "home_valuation":
                return getHomeValuation(headers, payload);
            default:
                throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        }
    }
    
    /**
     * Get detailed information about a property.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The property details
     */
    private Object getPropertyDetails(HttpHeaders headers, Map<String, Object> payload) {
        String zpid = (String) payload.get("zpid");
        if (zpid == null || zpid.isEmpty()) {
            throw new IllegalArgumentException("Zillow Property ID (zpid) is required");
        }
        
        String url = BASE_URL + "/property?zpid=" + zpid;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Search for properties in a location.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The search results
     */
    private Object searchProperties(HttpHeaders headers, Map<String, Object> payload) {
        String location = (String) payload.get("location");
        if (location == null || location.isEmpty()) {
            throw new IllegalArgumentException("Location is required");
        }
        
        String status = (String) payload.getOrDefault("status", "for_sale");
        int limit = (Integer) payload.getOrDefault("limit", 10);
        
        String url = BASE_URL + "/propertyExtendedSearch?location=" + location + 
                     "&status=" + status + "&home_type=Houses&page=1&limit=" + limit;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get estimated home value for an address.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The home valuation data
     */
    private Object getHomeValuation(HttpHeaders headers, Map<String, Object> payload) {
        String address = (String) payload.get("address");
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("Property address is required");
        }
        
        String url = BASE_URL + "/propertyByAddress?address=" + address;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
}
