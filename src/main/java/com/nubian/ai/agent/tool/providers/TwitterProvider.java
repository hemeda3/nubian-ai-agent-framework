package com.nubian.ai.agent.tool.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * Data provider for Twitter (X) data.
 * Provides access to tweets, user profiles, and trending topics.
 */
@Slf4j
public class TwitterProvider extends AbstractDataProvider {
    private static final String BASE_URL = "https://twitter154.p.rapidapi.com";
    
    /**
     * Create a new Twitter provider.
     * 
     * @param apiKey The Rapid API key
     */
    public TwitterProvider(String apiKey) {
        super(apiKey);
    }
    
    @Override
    protected void registerEndpoints() {
        // User profile endpoint
        Map<String, Object> userParams = new HashMap<>();
        Map<String, Object> usernameProp = new HashMap<>();
        usernameProp.put("type", "string");
        usernameProp.put("description", "Twitter username (without @)");
        userParams.put("username", usernameProp);
        
        addEndpoint("user_profile", "Get Twitter user profile information", userParams);
        
        // User tweets endpoint
        Map<String, Object> tweetsParams = new HashMap<>();
        Map<String, Object> tweetUsernameProp = new HashMap<>();
        tweetUsernameProp.put("type", "string");
        tweetUsernameProp.put("description", "Twitter username (without @)");
        tweetsParams.put("username", tweetUsernameProp);
        
        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "Maximum number of tweets to return");
        tweetsParams.put("limit", limitProp);
        
        addEndpoint("user_tweets", "Get tweets from a specific user", tweetsParams);
        
        // Search tweets endpoint
        Map<String, Object> searchParams = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "Search query for tweets");
        searchParams.put("query", queryProp);
        
        Map<String, Object> searchLimitProp = new HashMap<>();
        searchLimitProp.put("type", "integer");
        searchLimitProp.put("description", "Maximum number of tweets to return");
        searchParams.put("limit", searchLimitProp);
        
        addEndpoint("search_tweets", "Search for tweets by keyword", searchParams);
        
        // Trending topics endpoint
        Map<String, Object> trendingParams = new HashMap<>();
        Map<String, Object> locationProp = new HashMap<>();
        locationProp.put("type", "string");
        locationProp.put("description", "Location for trending topics (e.g., 'worldwide', 'united-states')");
        trendingParams.put("location", locationProp);
        
        addEndpoint("trending_topics", "Get trending topics on Twitter", trendingParams);
    }
    
    @Override
    public Object callEndpoint(String endpoint, Map<String, Object> payload) {
        log.info("Calling Twitter endpoint: {}", endpoint);
        
        // Set up common headers for all Twitter API calls
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key", apiKey);
        headers.set("X-RapidAPI-Host", "twitter154.p.rapidapi.com");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Handle different endpoints
        switch (endpoint) {
            case "user_profile":
                return getUserProfile(headers, payload);
            case "user_tweets":
                return getUserTweets(headers, payload);
            case "search_tweets":
                return searchTweets(headers, payload);
            case "trending_topics":
                return getTrendingTopics(headers, payload);
            default:
                throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        }
    }
    
    /**
     * Get Twitter user profile information.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The user profile data
     */
    private Object getUserProfile(HttpHeaders headers, Map<String, Object> payload) {
        String username = (String) payload.get("username");
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Twitter username is required");
        }
        
        String url = BASE_URL + "/user/details?username=" + username;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get tweets from a specific user.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The user tweets data
     */
    private Object getUserTweets(HttpHeaders headers, Map<String, Object> payload) {
        String username = (String) payload.get("username");
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Twitter username is required");
        }
        
        int limit = (Integer) payload.getOrDefault("limit", 10);
        
        String url = BASE_URL + "/user/tweets?username=" + username + "&limit=" + limit;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Search for tweets by keyword.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The search results
     */
    private Object searchTweets(HttpHeaders headers, Map<String, Object> payload) {
        String query = (String) payload.get("query");
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Search query is required");
        }
        
        int limit = (Integer) payload.getOrDefault("limit", 10);
        
        String url = BASE_URL + "/search?query=" + query + "&limit=" + limit;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get trending topics on Twitter.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The trending topics data
     */
    private Object getTrendingTopics(HttpHeaders headers, Map<String, Object> payload) {
        String location = (String) payload.getOrDefault("location", "worldwide");
        
        String url = BASE_URL + "/trends?location=" + location;
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
}
