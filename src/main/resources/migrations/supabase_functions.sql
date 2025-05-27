-- Function to get LLM-formatted messages
-- This replaces the H2 database ALIAS with a native PostgreSQL function
CREATE OR REPLACE FUNCTION get_llm_formatted_messages(thread_id_param TEXT)
RETURNS TABLE (
    role TEXT,
    content TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        CASE 
            WHEN is_llm_message THEN 'assistant'::TEXT
            ELSE 'user'::TEXT
        END as role,
        content::TEXT
    FROM 
        messages
    WHERE 
        thread_id = thread_id_param
    ORDER BY 
        created_at ASC;
END;
$$ LANGUAGE plpgsql;

-- Make sure to execute this SQL in your Supabase database using the SQL Editor
-- or via a migration script if you're using a database migration tool.
