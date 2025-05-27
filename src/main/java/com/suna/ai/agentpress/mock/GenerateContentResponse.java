package com.Nubian.ai.agentpress.mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock implementation of the GenerateContentResponse class.
 */
public class GenerateContentResponse {
    private List<Candidate> candidates;
    private String finishReason;
    
    public GenerateContentResponse() {
        this.candidates = new ArrayList<>();
    }
    
    public List<Candidate> getCandidates() {
        return candidates;
    }
    
    public void setCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }
    
    public void addCandidate(Candidate candidate) {
        this.candidates.add(candidate);
    }
    
    public String getFinishReason() {
        return finishReason;
    }
    
    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
    
    /**
     * Candidate class representing a generated response.
     */
    public static class Candidate {
        private Content content;
        private String finishReason;
        
        public Content getContent() {
            return content;
        }
        
        public void setContent(Content content) {
            this.content = content;
        }
        
        public String getFinishReason() {
            return finishReason;
        }
        
        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }
}
