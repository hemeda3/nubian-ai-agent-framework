package com.nubian.ai.agentpress.model.billing;

/**
 * Request model for creating a Stripe checkout session.
 */
public class CheckoutSessionRequest {
    private String priceId;
    private String successUrl;
    private String cancelUrl;
    
    /**
     * Default constructor.
     */
    public CheckoutSessionRequest() {
        // Default constructor
    }
    
    /**
     * Create a new checkout session request.
     *
     * @param priceId The Stripe price ID
     * @param successUrl The URL to redirect to on successful checkout
     * @param cancelUrl The URL to redirect to on cancelled checkout
     */
    public CheckoutSessionRequest(String priceId, String successUrl, String cancelUrl) {
        this.priceId = priceId;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }
    
    /**
     * Get the Stripe price ID.
     *
     * @return The price ID
     */
    public String getPriceId() {
        return priceId;
    }
    
    /**
     * Set the Stripe price ID.
     *
     * @param priceId The price ID
     */
    public void setPriceId(String priceId) {
        this.priceId = priceId;
    }
    
    /**
     * Get the success URL.
     *
     * @return The success URL
     */
    public String getSuccessUrl() {
        return successUrl;
    }
    
    /**
     * Set the success URL.
     *
     * @param successUrl The success URL
     */
    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }
    
    /**
     * Get the cancel URL.
     *
     * @return The cancel URL
     */
    public String getCancelUrl() {
        return cancelUrl;
    }
    
    /**
     * Set the cancel URL.
     *
     * @param cancelUrl The cancel URL
     */
    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
