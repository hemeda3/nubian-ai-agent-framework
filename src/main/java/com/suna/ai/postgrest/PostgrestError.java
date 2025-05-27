package com.Nubian.ai.postgrest;

/**
 * Error format from PostgREST.
 * 
 * Based on: https://postgrest.org/en/stable/api.html?highlight=options#errors-and-http-status-codes
 */
public class PostgrestError extends RuntimeException {
    private final String details;
    private final String hint;
    private final String code;

    public PostgrestError(String message, String details, String hint, String code) {
        super(message);
        this.details = details;
        this.hint = hint;
        this.code = code;
    }

    public String getDetails() {
        return details;
    }

    public String getHint() {
        return hint;
    }

    public String getCode() {
        return code;
    }
}
