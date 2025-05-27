package com.Nubian.ai.agent.tool.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * Data provider for Yahoo Finance data.
 * Provides access to stock quotes, company information, and other financial data.
 */
@Slf4j
public class YahooFinanceProvider extends AbstractDataProvider {
    private static final String BASE_URL = "https://yahoo-finance15.p.rapidapi.com";
    
    /**
     * Create a new Yahoo Finance provider.
     * 
     * @param apiKey The Rapid API key
     */
    public YahooFinanceProvider(String apiKey) {
        super(apiKey);
    }
    
    @Override
    protected void registerEndpoints() {
        // Stock quote endpoint
        Map<String, Object> quoteParams = new HashMap<>();
        Map<String, Object> symbolProp = new HashMap<>();
        symbolProp.put("type", "string");
        symbolProp.put("description", "Stock symbol (e.g., AAPL, MSFT)");
        quoteParams.put("symbol", symbolProp);
        
        addEndpoint("quote", "Get current stock quote information", quoteParams);
        
        // Company summary endpoint
        Map<String, Object> summaryParams = new HashMap<>();
        Map<String, Object> companySym = new HashMap<>();
        companySym.put("type", "string");
        companySym.put("description", "Stock symbol of the company");
        summaryParams.put("symbol", companySym);
        
        addEndpoint("company_summary", "Get company summary information", summaryParams);
        
        // Historical data endpoint
        Map<String, Object> historyParams = new HashMap<>();
        Map<String, Object> historySymbol = new HashMap<>();
        historySymbol.put("type", "string");
        historySymbol.put("description", "Stock symbol for historical data");
        historyParams.put("symbol", historySymbol);
        
        Map<String, Object> interval = new HashMap<>();
        interval.put("type", "string");
        interval.put("description", "Data interval (1d, 1wk, 1mo)");
        historyParams.put("interval", interval);
        
        Map<String, Object> range = new HashMap<>();
        range.put("type", "string");
        range.put("description", "Date range (1d, 5d, 1mo, 3mo, 6mo, 1y, 5y, max)");
        historyParams.put("range", range);
        
        addEndpoint("history", "Get historical stock price data", historyParams);
    }
    
    @Override
    public Object callEndpoint(String endpoint, Map<String, Object> payload) {
        log.info("Calling Yahoo Finance endpoint: {}", endpoint);
        
        // Set up common headers for all Yahoo Finance API calls
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key", apiKey);
        headers.set("X-RapidAPI-Host", "yahoo-finance15.p.rapidapi.com");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Handle different endpoints
        switch (endpoint) {
            case "quote":
                return getStockQuote(headers, payload);
            case "company_summary":
                return getCompanySummary(headers, payload);
            case "history":
                return getHistoricalData(headers, payload);
            default:
                throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        }
    }
    
    /**
     * Get current stock quote information.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The stock quote data
     */
    private Object getStockQuote(HttpHeaders headers, Map<String, Object> payload) {
        String symbol = (String) payload.get("symbol");
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Stock symbol is required");
        }
        
        String url = BASE_URL + "/api/v1/market/get-quotes?symbol=" + symbol + "&region=US";
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get company summary information.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The company summary data
     */
    private Object getCompanySummary(HttpHeaders headers, Map<String, Object> payload) {
        String symbol = (String) payload.get("symbol");
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Stock symbol is required");
        }
        
        String url = BASE_URL + "/api/v1/market/get-summary?symbol=" + symbol + "&region=US";
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
    
    /**
     * Get historical stock price data.
     * 
     * @param headers The HTTP headers
     * @param payload The request payload
     * @return The historical stock data
     */
    private Object getHistoricalData(HttpHeaders headers, Map<String, Object> payload) {
        String symbol = (String) payload.get("symbol");
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Stock symbol is required");
        }
        
        String interval = (String) payload.getOrDefault("interval", "1d");
        String range = (String) payload.getOrDefault("range", "1mo");
        
        String url = BASE_URL + "/api/v1/market/get-charts?symbol=" + symbol + 
                     "&interval=" + interval + "&range=" + range + "&region=US";
        
        // Make the API call
        return executeRequest(url, HttpMethod.GET, headers, null, Map.class);
    }
}
