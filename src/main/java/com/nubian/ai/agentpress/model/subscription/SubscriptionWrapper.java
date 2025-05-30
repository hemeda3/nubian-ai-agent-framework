package com.nubian.ai.agentpress.model.subscription;

import com.stripe.model.Subscription;
import java.util.Map;
import java.util.HashMap;

/**
 * Wrapper class for Stripe Subscription to add convenience methods
 * for accessing subscription properties that may have different method
 * names across Stripe library versions.
 */
public class SubscriptionWrapper {
    
    private final Subscription subscription;
    
    /**
     * Create a new subscription wrapper.
     * 
     * @param subscription The Stripe subscription to wrap
     */
    public SubscriptionWrapper(Subscription subscription) {
        this.subscription = subscription;
    }
    
    /**
     * Get the underlying Stripe subscription.
     * 
     * @return The Stripe subscription
     */
    public Subscription getSubscription() {
        return subscription;
    }
    
    /**
     * Get the subscription ID.
     * 
     * @return The subscription ID
     */
    public String getId() {
        return subscription.getId();
    }
    
    /**
     * Get the subscription status.
     * 
     * @return The subscription status
     */
    public String getStatus() {
        return subscription.getStatus();
    }
    
    /**
     * Get the current period end timestamp.
     * 
     * @return The current period end timestamp
     */
    public Long getCurrentPeriodEnd() {
        // Use reflection to safely access methods that might differ between Stripe API versions
        try {
            // Try different methods that might exist in different versions
            try {
                java.lang.reflect.Method method = subscription.getClass().getMethod("getCurrentPeriodEnd");
                return (Long) method.invoke(subscription);
            } catch (NoSuchMethodException e) {
                // Try other possible method names
                try {
                    java.lang.reflect.Method method = subscription.getClass().getMethod("getEndDate");
                    return (Long) method.invoke(subscription);
                } catch (NoSuchMethodException ex) {
                    // Try accessing raw property map if available
                    try {
                        java.lang.reflect.Method method = subscription.getClass().getMethod("getLastResponse");
                        Object response = method.invoke(subscription);
                        if (response != null) {
                            java.lang.reflect.Method jsonMethod = response.getClass().getMethod("getJsonObject");
                            Object json = jsonMethod.invoke(response);
                            if (json instanceof Map<?, ?>) {
                                return (Long) ((Map<?, ?>) json).get("current_period_end");
                            }
                        }
                    } catch (Exception exc) {
                        // Fall through to final approach
                    }
                    
                    // Try using toMap() if available
                    try {
                        java.lang.reflect.Method method = subscription.getClass().getMethod("toMap");
                        Object result = method.invoke(subscription);
                        if (result instanceof Map) {
                            return (Long) ((Map<?, ?>) result).get("current_period_end");
                        }
                    } catch (Exception excf) {
                        // All attempts failed
                    }
                }
            }
            // If all else fails, return null
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get the current period start timestamp.
     * 
     * @return The current period start timestamp
     */
    public Long getCurrentPeriodStart() {
        // Use reflection to safely access methods that might differ between Stripe API versions
        try {
            // Try different methods that might exist in different versions
            try {
                java.lang.reflect.Method method = subscription.getClass().getMethod("getCurrentPeriodStart");
                return (Long) method.invoke(subscription);
            } catch (NoSuchMethodException e) {
                // Try other possible method names
                try {
                    java.lang.reflect.Method method = subscription.getClass().getMethod("getStartDate");
                    return (Long) method.invoke(subscription);
                } catch (NoSuchMethodException ex) {
                    // Try accessing raw property map if available
                    try {
                        java.lang.reflect.Method method = subscription.getClass().getMethod("getLastResponse");
                        Object response = method.invoke(subscription);
                        if (response != null) {
                            java.lang.reflect.Method jsonMethod = response.getClass().getMethod("getJsonObject");
                            Object json = jsonMethod.invoke(response);
                            if (json instanceof Map<?, ?>) {
                                return (Long) ((Map<?, ?>) json).get("current_period_start");
                            }
                        }
                    } catch (Exception exc) {
                        // Fall through to final approach
                    }
                    
                    // Try using toMap() if available
                    try {
                        java.lang.reflect.Method method = subscription.getClass().getMethod("toMap");
                        Object result = method.invoke(subscription);
                        if (result instanceof Map) {
                            return (Long) ((Map<?, ?>) result).get("current_period_start");
                        }
                    } catch (Exception excf) {
                        // All attempts failed
                    }
                }
            }
            // If all else fails, return null
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get the trial end timestamp.
     * 
     * @return The trial end timestamp
     */
    public Long getTrialEnd() {
        return subscription.getTrialEnd();
    }
    
    /**
     * Check if the subscription will be canceled at the end of the period.
     * 
     * @return Whether the subscription will be canceled at the end of the period
     */
    public Boolean getCancelAtPeriodEnd() {
        return subscription.getCancelAtPeriodEnd();
    }
    
    /**
     * Get the subscription schedule ID.
     * 
     * @return The subscription schedule ID
     */
    public String getSchedule() {
        try {
            // Try different methods that might exist in different versions
            try {
                java.lang.reflect.Method method = subscription.getClass().getMethod("getSchedule");
                return (String) method.invoke(subscription);
            } catch (NoSuchMethodException e) {
                // Try accessing raw property map if available
                try {
                    java.lang.reflect.Method method = subscription.getClass().getMethod("getLastResponse");
                    Object response = method.invoke(subscription);
                    if (response != null) {
                        java.lang.reflect.Method jsonMethod = response.getClass().getMethod("getJsonObject");
                        Object json = jsonMethod.invoke(response);
                        if (json instanceof Map<?, ?>) {
                            return (String) ((Map<?, ?>) json).get("schedule");
                        }
                    }
                } catch (Exception exc) {
                    // Fall through to final approach
                }
                
                // Try using toMap() if available
                try {
                    java.lang.reflect.Method method = subscription.getClass().getMethod("toMap");
                    Object result = method.invoke(subscription);
                    if (result instanceof Map) {
                        return (String) ((Map<?, ?>) result).get("schedule");
                    }
                } catch (Exception excf) {
                    // All attempts failed
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
