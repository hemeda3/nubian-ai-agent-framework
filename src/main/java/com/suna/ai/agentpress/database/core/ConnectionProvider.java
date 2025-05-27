package com.Nubian.ai.agentpress.database.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agentpress.database.ResilientSupabaseClient;

/**
 * Provides the core database connection components: ResilientSupabaseClient and ObjectMapper.
 */
@Component
public class ConnectionProvider {

    private final ResilientSupabaseClient supabaseClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConnectionProvider(ResilientSupabaseClient supabaseClient, ObjectMapper objectMapper) {
        this.supabaseClient = supabaseClient;
        this.objectMapper = objectMapper;
    }

    public ResilientSupabaseClient getSupabaseClient() {
        return supabaseClient;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
