package com.nubian.ai.agentpress.model.billing;

/**
 * Request model for creating a Stripe customer portal session.
 */
public class PortalSessionRequest {
    private String returnUrl;
    
    /**
     * Default constructor.
     */
    public PortalSessionRequest() {
        // Default constructor
    }
    
    /**
     * Create a new portal session request.
     *
     * @param returnUrl The URL to redirect to after the portal session
     */
    public PortalSessionRequest(String returnUrl) {
        this.returnUrl = returnUrl;
    }
    
    /**
     * Get the return URL.
     *
     * @return The return URL
     */
    public String getReturnUrl() {
        return returnUrl;
    }
    
    /**
     * Set the return URL.
     *
     * @param returnUrl The return URL
     */
    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }
}
