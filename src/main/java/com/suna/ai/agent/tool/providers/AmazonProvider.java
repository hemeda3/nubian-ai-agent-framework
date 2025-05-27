package com.Nubian.ai.agent.tool.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * Data provider for Amazon product data.
 * Provides access to product search, details, reviews, and more.
 */
@Slf4j
public class AmazonProvider extends AbstractDataProvider {
    private static final String BASE_URL = "https://amazon-product-data.p.rapidapi.com";
    
    /**
     * Create a new Amazon provider.
     * 
     * @param apiKey The Rapid API key
     */
    public AmazonProvider(String apiKey) {
        super(apiKey);
    }
    
    @Override
    protected void registerEndpoints() {
        // Product search endpoint
        Map<String, Object> searchParams = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "Search query for products");
        searchParams.put("query", queryProp);
        
        Map<String, Object> categoryProp = new HashMap<>();
        categoryProp.put("type", "string");
        categoryProp.put("description", "Product category (optional)");
        searchParams.put("category", categoryProp);
        
        addEndpoint("search", "Search for products on Amazon", searchParams);
        
        // Product details endpoint
        Map<String, Object> detailsParams = new HashMap<>();
        Map<String, Object> asinProp = new HashMap<>();
        asinProp.put("type", "string");
        asinProp.put("description", "Amazon ASIN (product ID)");
        detailsParams.put("asin", asinProp);
        
        addEndpoint("product_details", "Get detailed information about a product", detailsParams);
        
        // Product reviews endpoint
        Map<String, Object> reviewsParams = new HashMap<>();
        Map<String, Object> reviewAsinProp = new HashMap<>();
        reviewAsinProp.put("type", "string");
        reviewAsinProp.put("description", "Amazon ASIN (product ID)");
        reviewsParams.put("asin", reviewAsinProp);
        
        addEndpoint("reviews", "Get product reviews", reviewsParams);
    }
    
    @Override
    public Object callEndpoint(String endpoint, Map<String, Object> payload) {
        log.info("Calling Amazon endpoint: {}", endpoint);
        
        // Set up common headers for all Amazon API calls
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key", apiKey);
        headers.set("X-RapidAPI-Host", "amazon-product-data.p.rapidapi.com");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Handle different endpoints
        switch (endpoint) {
            case "search":
                return searchProducts(headers, payload);
            case "product_details":
                return getProductDetails(headers, payload);
            case "reviews":
                return getProductReviews(headers, payload);
            default:
                throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        }
    }
    
    /**
     * Search for products on Amazon.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The search results
     */
    private Object searchProducts(HttpHeaders headers, Map<String, Object> payload) {
        String query = (String) payload.get("query");
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Search query is required");
        }
        
        String category = (String) payload.getOrDefault("category", "");
        
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/search?query=" + query);
        if (category != null && !category.isEmpty()) {
            urlBuilder.append("&category=").append(category);
        }
        
        // Make the API call
        return executeRequest(urlBuilder.toString(), HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get detailed information about a product.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The product details
     */
    private Object getProductDetails(HttpHeaders headers, Map<String, Object> payload) {
        String asin = (String) payload.get("asin");
        if (asin == null || asin.isEmpty()) {
            throw new IllegalArgumentException("Product ASIN is required");
        }
        
        String url = BASE_URL + "/product?asin=" + asin;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get product reviews.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The product reviews
     */
    private Object getProductReviews(HttpHeaders headers, Map<String, Object> payload) {
        String asin = (String) payload.get("asin");
        if (asin == null || asin.isEmpty()) {
            throw new IllegalArgumentException("Product ASIN is required");
        }
        
        String url = BASE_URL + "/reviews?asin=" + asin;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
}
