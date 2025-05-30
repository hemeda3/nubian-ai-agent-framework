package com.nubian.ai.agentpress.model.billing;

import java.time.LocalDateTime;

/**
 * Model representing the status of a subscription.
 */
public class SubscriptionStatus {
    private String status;
    private String planName;
    private String priceId;
    private LocalDateTime currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private LocalDateTime trialEnd;
    private Integer minutesLimit;
    private Double currentUsage;
    
    // Fields for scheduled changes
    private boolean hasSchedule;
    private String scheduledPlanName;
    private String scheduledPriceId;
    private LocalDateTime scheduledChangeDate;
    
    /**
     * Default constructor.
     */
    public SubscriptionStatus() {
        // Default constructor
    }
    
    /**
     * Get the subscription status.
     *
     * @return The status (e.g., 'active', 'trialing', 'past_due', 'scheduled_downgrade', 'no_subscription')
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * Set the subscription status.
     *
     * @param status The status
     */
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * Get the plan name.
     *
     * @return The plan name
     */
    public String getPlanName() {
        return planName;
    }
    
    /**
     * Set the plan name.
     *
     * @param planName The plan name
     */
    public void setPlanName(String planName) {
        this.planName = planName;
    }
    
    /**
     * Get the price ID.
     *
     * @return The price ID
     */
    public String getPriceId() {
        return priceId;
    }
    
    /**
     * Set the price ID.
     *
     * @param priceId The price ID
     */
    public void setPriceId(String priceId) {
        this.priceId = priceId;
    }
    
    /**
     * Get the current period end date.
     *
     * @return The current period end date
     */
    public LocalDateTime getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }
    
    /**
     * Set the current period end date.
     *
     * @param currentPeriodEnd The current period end date
     */
    public void setCurrentPeriodEnd(LocalDateTime currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }
    
    /**
     * Check if the subscription is set to cancel at the end of the period.
     *
     * @return true if the subscription will cancel at the end of the period
     */
    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }
    
    /**
     * Set whether the subscription is set to cancel at the end of the period.
     *
     * @param cancelAtPeriodEnd Whether the subscription will cancel
     */
    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }
    
    /**
     * Get the trial end date.
     *
     * @return The trial end date
     */
    public LocalDateTime getTrialEnd() {
        return trialEnd;
    }
    
    /**
     * Set the trial end date.
     *
     * @param trialEnd The trial end date
     */
    public void setTrialEnd(LocalDateTime trialEnd) {
        this.trialEnd = trialEnd;
    }
    
    /**
     * Get the minutes limit for the subscription.
     *
     * @return The minutes limit
     */
    public Integer getMinutesLimit() {
        return minutesLimit;
    }
    
    /**
     * Set the minutes limit for the subscription.
     *
     * @param minutesLimit The minutes limit
     */
    public void setMinutesLimit(Integer minutesLimit) {
        this.minutesLimit = minutesLimit;
    }
    
    /**
     * Get the current usage in minutes.
     *
     * @return The current usage
     */
    public Double getCurrentUsage() {
        return currentUsage;
    }
    
    /**
     * Set the current usage in minutes.
     *
     * @param currentUsage The current usage
     */
    public void setCurrentUsage(Double currentUsage) {
        this.currentUsage = currentUsage;
    }
    
    /**
     * Check if the subscription has a scheduled change.
     *
     * @return true if there is a scheduled change
     */
    public boolean isHasSchedule() {
        return hasSchedule;
    }
    
    /**
     * Set whether the subscription has a scheduled change.
     *
     * @param hasSchedule Whether there is a scheduled change
     */
    public void setHasSchedule(boolean hasSchedule) {
        this.hasSchedule = hasSchedule;
    }
    
    /**
     * Get the scheduled plan name.
     *
     * @return The scheduled plan name
     */
    public String getScheduledPlanName() {
        return scheduledPlanName;
    }
    
    /**
     * Set the scheduled plan name.
     *
     * @param scheduledPlanName The scheduled plan name
     */
    public void setScheduledPlanName(String scheduledPlanName) {
        this.scheduledPlanName = scheduledPlanName;
    }
    
    /**
     * Get the scheduled price ID.
     *
     * @return The scheduled price ID
     */
    public String getScheduledPriceId() {
        return scheduledPriceId;
    }
    
    /**
     * Set the scheduled price ID.
     *
     * @param scheduledPriceId The scheduled price ID
     */
    public void setScheduledPriceId(String scheduledPriceId) {
        this.scheduledPriceId = scheduledPriceId;
    }
    
    /**
     * Get the scheduled change date.
     *
     * @return The scheduled change date
     */
    public LocalDateTime getScheduledChangeDate() {
        return scheduledChangeDate;
    }
    
    /**
     * Set the scheduled change date.
     *
     * @param scheduledChangeDate The scheduled change date
     */
    public void setScheduledChangeDate(LocalDateTime scheduledChangeDate) {
        this.scheduledChangeDate = scheduledChangeDate;
    }
}
