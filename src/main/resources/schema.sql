------ src/main/resources/migrations/20240414161707_basejump-setup.sql ------
``````
/**
      ____                 _
     |  _ \               (_)
     | |_) | __ _ ___  ___ _ _   _ _ __ ___  _ __
     |  _ < / _` / __|/ _ \ | | | | '_ ` _ \| '_ \
     | |_) | (_| \__ \  __/ | |_| | | | | | | |_) |
     |____/ \__,_|___/\___| |\__,_|_| |_| |_| .__/
                         _/ |               | |
                        |__/                |_|

     Basejump is a starter kit for building SaaS products on top of Supabase.
     Learn more at https://usebasejump.com
 */


/**
  * -------------------------------------------------------
  * Section - Basejump schema setup and utility functions
  * -------------------------------------------------------
 */

-- revoke execution by default from public
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA PUBLIC REVOKE EXECUTE ON FUNCTIONS FROM anon, authenticated;

-- Create basejump schema
CREATE SCHEMA IF NOT EXISTS basejump;
GRANT USAGE ON SCHEMA basejump to authenticated;
GRANT USAGE ON SCHEMA basejump to service_role;

/**
  * -------------------------------------------------------
  * Section - Enums
  * -------------------------------------------------------
 */

/**
 * Invitation types are either email or link. Email invitations are sent to
 * a single user and can only be claimed once.  Link invitations can be used multiple times
 * Both expire after 24 hours
 */
DO
$$
    BEGIN
        -- check it account_role already exists on basejump schema
        IF NOT EXISTS(SELECT 1
                      FROM pg_type t
                               JOIN pg_namespace n ON n.oid = t.typnamespace
                      WHERE t.typname = 'invitation_type'
                        AND n.nspname = 'basejump') THEN
            CREATE TYPE basejump.invitation_type AS ENUM ('one_time', '24_hour');
        end if;
    end;
$$;

/**
  * -------------------------------------------------------
  * Section - Basejump settings
  * -------------------------------------------------------
 */

CREATE TABLE IF NOT EXISTS basejump.config
(
    enable_team_accounts            boolean default true,
    enable_personal_account_billing boolean default true,
    enable_team_account_billing     boolean default true,
    billing_provider                text    default 'stripe'
);

-- create config row
INSERT INTO basejump.config (enable_team_accounts, enable_personal_account_billing, enable_team_account_billing)
VALUES (true, true, true);

-- enable select on the config table
GRANT SELECT ON basejump.config TO authenticated, service_role;

-- enable RLS on config
ALTER TABLE basejump.config
    ENABLE ROW LEVEL SECURITY;

create policy "Basejump settings can be read by authenticated users" on basejump.config
    for select
    to authenticated
    using (
    true
    );

/**
  * -------------------------------------------------------
  * Section - Basejump utility functions
  * -------------------------------------------------------
 */

/**
  basejump.get_config()
  Get the full config object to check basejump settings
  This is not accessible from the outside, so can only be used inside postgres functions
 */
CREATE OR REPLACE FUNCTION basejump.get_config()
    RETURNS json AS
$$
DECLARE
    result RECORD;
BEGIN
    SELECT * from basejump.config limit 1 into result;
    return row_to_json(result);
END;
$$ LANGUAGE plpgsql;

grant execute on function basejump.get_config() to authenticated, service_role;


/**
  basejump.is_set("field_name")
  Check a specific boolean config value
 */
CREATE OR REPLACE FUNCTION basejump.is_set(field_name text)
    RETURNS boolean AS
$$
DECLARE
    result BOOLEAN;
BEGIN
    execute format('select %I from basejump.config limit 1', field_name) into result;
    return result;
END;
$$ LANGUAGE plpgsql;

grant execute on function basejump.is_set(text) to authenticated;


/**
  * Automatic handling for maintaining created_at and updated_at timestamps
  * on tables
 */
CREATE OR REPLACE FUNCTION basejump.trigger_set_timestamps()
    RETURNS TRIGGER AS
$$
BEGIN
    if TG_OP = 'INSERT' then
        NEW.created_at = now();
        NEW.updated_at = now();
    else
        NEW.updated_at = now();
        NEW.created_at = OLD.created_at;
    end if;
    RETURN NEW;
END
$$ LANGUAGE plpgsql;


/**
  * Automatic handling for maintaining created_by and updated_by timestamps
  * on tables
 */
CREATE OR REPLACE FUNCTION basejump.trigger_set_user_tracking()
    RETURNS TRIGGER AS
$$
BEGIN
    if TG_OP = 'INSERT' then
        NEW.created_by = auth.uid();
        NEW.updated_by = auth.uid();
    else
        NEW.updated_by = auth.uid();
        NEW.created_by = OLD.created_by;
    end if;
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

/**
  basejump.generate_token(length)
  Generates a secure token - used internally for invitation tokens
  but could be used elsewhere.  Check out the invitations table for more info on
  how it's used
 */
CREATE OR REPLACE FUNCTION basejump.generate_token(length int)
    RETURNS text AS
$$
select regexp_replace(replace(
                              replace(replace(replace(encode(gen_random_bytes(length)::bytea, 'base64'), '/', ''), '+',
                                              ''), '\', ''),
                              '=',
                              ''), E'[\\n\\r]+', '', 'g');
$$ LANGUAGE sql;

grant execute on function basejump.generate_token(int) to authenticated;
``````
------ src/main/resources/migrations/20240414161947_basejump-accounts.sql ------
``````
/**
      ____                 _
     |  _ \               (_)
     | |_) | __ _ ___  ___ _ _   _ _ __ ___  _ __
     |  _ < / _` / __|/ _ \ | | | | '_ ` _ \| '_ \
     | |_) | (_| \__ \  __/ | |_| | | | | | | |_) |
     |____/ \__,_|___/\___| |\__,_|_| |_| |_| .__/
                         _/ |               | |
                        |__/                |_|

     Basejump is a starter kit for building SaaS products on top of Supabase.
     Learn more at https://usebasejump.com
 */

/**
  * -------------------------------------------------------
  * Section - Accounts
  * -------------------------------------------------------
 */

/**
 * Account roles allow you to provide permission levels to users
 * when they're acting on an account.  By default, we provide
 * "owner" and "member".  The only distinction is that owners can
 * also manage billing and invite/remove account members.
 */
DO
$$
    BEGIN
        -- check it account_role already exists on basejump schema
        IF NOT EXISTS(SELECT 1
                      FROM pg_type t
                               JOIN pg_namespace n ON n.oid = t.typnamespace
                      WHERE t.typname = 'account_role'
                        AND n.nspname = 'basejump') THEN
            CREATE TYPE basejump.account_role AS ENUM ('owner', 'member');
        end if;
    end;
$$;

/**
 * Accounts are the primary grouping for most objects within
 * the system. They have many users, and all billing is connected to
 * an account.
 */
CREATE TABLE IF NOT EXISTS basejump.accounts
(
    id                    uuid unique                NOT NULL DEFAULT extensions.uuid_generate_v4(),
    -- defaults to the user who creates the account
    -- this user cannot be removed from an account without changing
    -- the primary owner first
    primary_owner_user_id uuid references auth.users not null default auth.uid(),
    -- Account name
    name                  text,
    slug                  text unique,
    personal_account      boolean                             default false not null,
    updated_at            timestamp with time zone,
    created_at            timestamp with time zone,
    created_by            uuid references auth.users,
    updated_by            uuid references auth.users,
    private_metadata      jsonb                               default '{}'::jsonb,
    public_metadata       jsonb                               default '{}'::jsonb,
    PRIMARY KEY (id)
);

-- constraint that conditionally allows nulls on the slug ONLY if personal_account is true
-- remove this if you want to ignore accounts slugs entirely
ALTER TABLE basejump.accounts
    ADD CONSTRAINT basejump_accounts_slug_null_if_personal_account_true CHECK (
            (personal_account = true AND slug is null)
            OR (personal_account = false AND slug is not null)
        );

-- Open up access to accounts
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE basejump.accounts TO authenticated, service_role;

/**
 * We want to protect some fields on accounts from being updated
 * Specifically the primary owner user id and account id.
 * primary_owner_user_id should be updated using the dedicated function
 */
CREATE OR REPLACE FUNCTION basejump.protect_account_fields()
    RETURNS TRIGGER AS
$$
BEGIN
    IF current_user IN ('authenticated', 'anon') THEN
        -- these are protected fields that users are not allowed to update themselves
        -- platform admins should be VERY careful about updating them as well.
        if NEW.id <> OLD.id
            OR NEW.personal_account <> OLD.personal_account
            OR NEW.primary_owner_user_id <> OLD.primary_owner_user_id
        THEN
            RAISE EXCEPTION 'You do not have permission to update this field';
        end if;
    end if;

    RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- trigger to protect account fields
CREATE TRIGGER basejump_protect_account_fields
    BEFORE UPDATE
    ON basejump.accounts
    FOR EACH ROW
EXECUTE FUNCTION basejump.protect_account_fields();

-- convert any character in the slug that's not a letter, number, or dash to a dash on insert/update for accounts
CREATE OR REPLACE FUNCTION basejump.slugify_account_slug()
    RETURNS TRIGGER AS
$$
BEGIN
    if NEW.slug is not null then
        NEW.slug = lower(regexp_replace(NEW.slug, '[^a-zA-Z0-9-]+', '-', 'g'));
    end if;

    RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- trigger to slugify the account slug
CREATE TRIGGER basejump_slugify_account_slug
    BEFORE INSERT OR UPDATE
    ON basejump.accounts
    FOR EACH ROW
EXECUTE FUNCTION basejump.slugify_account_slug();

-- enable RLS for accounts
alter table basejump.accounts
    enable row level security;

-- protect the timestamps
CREATE TRIGGER basejump_set_accounts_timestamp
    BEFORE INSERT OR UPDATE
    ON basejump.accounts
    FOR EACH ROW
EXECUTE PROCEDURE basejump.trigger_set_timestamps();

-- set the user tracking
CREATE TRIGGER basejump_set_accounts_user_tracking
    BEFORE INSERT OR UPDATE
    ON basejump.accounts
    FOR EACH ROW
EXECUTE PROCEDURE basejump.trigger_set_user_tracking();

/**
  * Account users are the users that are associated with an account.
  * They can be invited to join the account, and can have different roles.
  * The system does not enforce any permissions for roles, other than restricting
  * billing and account membership to only owners
 */
create table if not exists basejump.account_user
(
    -- id of the user in the account
    user_id      uuid references auth.users on delete cascade        not null,
    -- id of the account the user is in
    account_id   uuid references basejump.accounts on delete cascade not null,
    -- role of the user in the account
    account_role basejump.account_role                               not null,
    constraint account_user_pkey primary key (user_id, account_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE basejump.account_user TO authenticated, service_role;


-- enable RLS for account_user
alter table basejump.account_user
    enable row level security;

/**
  * When an account gets created, we want to insert the current user as the first
  * owner
 */
create or replace function basejump.add_current_user_to_new_account()
    returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
begin
    if new.primary_owner_user_id = auth.uid() then
        insert into basejump.account_user (account_id, user_id, account_role)
        values (NEW.id, auth.uid(), 'owner');
    end if;
    return NEW;
end;
$$;

-- trigger the function whenever a new account is created
CREATE TRIGGER basejump_add_current_user_to_new_account
    AFTER INSERT
    ON basejump.accounts
    FOR EACH ROW
EXECUTE FUNCTION basejump.add_current_user_to_new_account();

/**
  * When a user signs up, we need to create a personal account for them
  * and add them to the account_user table so they can act on it
 */
create or replace function basejump.run_new_user_setup()
    returns trigger
    language plpgsql
    security definer
    set search_path = public
as
$$
declare
    first_account_id    uuid;
    generated_user_name text;
begin

    -- first we setup the user profile
    -- TODO: see if we can get the user's name from the auth.users table once we learn how oauth works
    if new.email IS NOT NULL then
        generated_user_name := split_part(new.email, '@', 1);
    end if;
    -- create the new users's personal account
    insert into basejump.accounts (name, primary_owner_user_id, personal_account, id)
    values (generated_user_name, NEW.id, true, NEW.id)
    returning id into first_account_id;

    -- add them to the account_user table so they can act on it
    insert into basejump.account_user (account_id, user_id, account_role)
    values (first_account_id, NEW.id, 'owner');

    return NEW;
end;
$$;

-- trigger the function every time a user is created
create trigger on_auth_user_created
    after insert
    on auth.users
    for each row
execute procedure basejump.run_new_user_setup();

/**
  * -------------------------------------------------------
  * Section - Account permission utility functions
  * -------------------------------------------------------
  * These functions are stored on the basejump schema, and useful for things like
  * generating RLS policies
 */

/**
  * Returns true if the current user has the pass in role on the passed in account
  * If no role is sent, will return true if the user is a member of the account
  * NOTE: This is an inefficient function when used on large query sets. You should reach for the get_accounts_with_role and lookup
  * the account ID in those cases.
 */
create or replace function basejump.has_role_on_account(account_id uuid, account_role basejump.account_role default null)
    returns boolean
    language sql
    security definer
    set search_path = public
as
$$
select exists(
               select 1
               from basejump.account_user wu
               where wu.user_id = auth.uid()
                 and wu.account_id = has_role_on_account.account_id
                 and (
                           wu.account_role = has_role_on_account.account_role
                       or has_role_on_account.account_role is null
                   )
           );
$$;

grant execute on function basejump.has_role_on_account(uuid, basejump.account_role) to authenticated, anon, public, service_role;


/**
  * Returns account_ids that the current user is a member of. If you pass in a role,
  * it'll only return accounts that the user is a member of with that role.
  */
create or replace function basejump.get_accounts_with_role(passed_in_role basejump.account_role default null)
    returns setof uuid
    language sql
    security definer
    set search_path = public
as
$$
select account_id
from basejump.account_user wu
where wu.user_id = auth.uid()
  and (
            wu.account_role = passed_in_role
        or passed_in_role is null
    );
$$;

grant execute on function basejump.get_accounts_with_role(basejump.account_role) to authenticated;

/**
  * -------------------------
  * Section - RLS Policies
  * -------------------------
  * This is where we define access to tables in the basejump schema
 */

create policy "users can view their own account_users" on basejump.account_user
    for select
    to authenticated
    using (
    user_id = auth.uid()
    );

create policy "users can view their teammates" on basejump.account_user
    for select
    to authenticated
    using (
    basejump.has_role_on_account(account_id) = true
    );

create policy "Account users can be deleted by owners except primary account owner" on basejump.account_user
    for delete
    to authenticated
    using (
        (basejump.has_role_on_account(account_id, 'owner') = true)
        AND
        user_id != (select primary_owner_user_id
                    from basejump.accounts
                    where account_id = accounts.id)
    );

create policy "Accounts are viewable by members" on basejump.accounts
    for select
    to authenticated
    using (
    basejump.has_role_on_account(id) = true
    );

-- Primary owner should always have access to the account
create policy "Accounts are viewable by primary owner" on basejump.accounts
    for select
    to authenticated
    using (
    primary_owner_user_id = auth.uid()
    );

create policy "Team accounts can be created by any user" on basejump.accounts
    for insert
    to authenticated
    with check (
            basejump.is_set('enable_team_accounts') = true
        and personal_account = false
    );


create policy "Accounts can be edited by owners" on basejump.accounts
    for update
    to authenticated
    using (
    basejump.has_role_on_account(id, 'owner') = true
    );

/**
  * -------------------------------------------------------
  * Section - Public functions
  * -------------------------------------------------------
  * Each of these functions exists in the public name space because they are accessible
  * via the API.  it is the primary way developers can interact with Basejump accounts
 */

/**
* Returns the account_id for a given account slug
*/

create or replace function public.get_account_id(slug text)
    returns uuid
    language sql
as
$$
select id
from basejump.accounts
where slug = get_account_id.slug;
$$;

grant execute on function public.get_account_id(text) to authenticated, service_role;

/**
 * Returns the current user's role within a given account_id
*/
create or replace function public.current_user_account_role(account_id uuid)
    returns jsonb
    language plpgsql
as
$$
DECLARE
    response jsonb;
BEGIN

    select jsonb_build_object(
                   'account_role', wu.account_role,
                   'is_primary_owner', a.primary_owner_user_id = auth.uid(),
                   'is_personal_account', a.personal_account
               )
    into response
    from basejump.account_user wu
             join basejump.accounts a on a.id = wu.account_id
    where wu.user_id = auth.uid()
      and wu.account_id = current_user_account_role.account_id;

    -- if the user is not a member of the account, throw an error
    if response ->> 'account_role' IS NULL then
        raise exception 'Not found';
    end if;

    return response;
END
$$;

grant execute on function public.current_user_account_role(uuid) to authenticated;

/**
  * Let's you update a users role within an account if you are an owner of that account
  **/
create or replace function public.update_account_user_role(account_id uuid, user_id uuid,
                                                           new_account_role basejump.account_role,
                                                           make_primary_owner boolean default false)
    returns void
    security definer
    set search_path = public
    language plpgsql
as
$$
declare
    is_account_owner         boolean;
    is_account_primary_owner boolean;
    changing_primary_owner   boolean;
begin
    -- check if the user is an owner, and if they are, allow them to update the role
    select basejump.has_role_on_account(update_account_user_role.account_id, 'owner') into is_account_owner;

    if not is_account_owner then
        raise exception 'You must be an owner of the account to update a users role';
    end if;

    -- check if the user being changed is the primary owner, if so its not allowed
    select primary_owner_user_id = auth.uid(), primary_owner_user_id = update_account_user_role.user_id
    into is_account_primary_owner, changing_primary_owner
    from basejump.accounts
    where id = update_account_user_role.account_id;

    if changing_primary_owner = true and is_account_primary_owner = false then
        raise exception 'You must be the primary owner of the account to change the primary owner';
    end if;

    update basejump.account_user au
    set account_role = new_account_role
    where au.account_id = update_account_user_role.account_id
      and au.user_id = update_account_user_role.user_id;

    if make_primary_owner = true then
        -- first we see if the current user is the owner, only they can do this
        if is_account_primary_owner = false then
            raise exception 'You must be the primary owner of the account to change the primary owner';
        end if;

        update basejump.accounts
        set primary_owner_user_id = update_account_user_role.user_id
        where id = update_account_user_role.account_id;
    end if;
end;
$$;

grant execute on function public.update_account_user_role(uuid, uuid, basejump.account_role, boolean) to authenticated;

/**
  Returns the current user's accounts
 */
create or replace function public.get_accounts()
    returns json
    language sql
as
$$
select coalesce(json_agg(
                        json_build_object(
                                'account_id', wu.account_id,
                                'account_role', wu.account_role,
                                'is_primary_owner', a.primary_owner_user_id = auth.uid(),
                                'name', a.name,
                                'slug', a.slug,
                                'personal_account', a.personal_account,
                                'created_at', a.created_at,
                                'updated_at', a.updated_at
                            )
                    ), '[]'::json)
from basejump.account_user wu
         join basejump.accounts a on a.id = wu.account_id
where wu.user_id = auth.uid();
$$;

grant execute on function public.get_accounts() to authenticated;

/**
  Returns a specific account that the current user has access to
 */
create or replace function public.get_account(account_id uuid)
    returns json
    language plpgsql
as
$$
BEGIN
    -- check if the user is a member of the account or a service_role user
    if current_user IN ('anon', 'authenticated') and
       (select current_user_account_role(get_account.account_id) ->> 'account_role' IS NULL) then
        raise exception 'You must be a member of an account to access it';
    end if;


    return (select json_build_object(
                           'account_id', a.id,
                           'account_role', wu.account_role,
                           'is_primary_owner', a.primary_owner_user_id = auth.uid(),
                           'name', a.name,
                           'slug', a.slug,
                           'personal_account', a.personal_account,
                           'billing_enabled', case
                                                  when a.personal_account = true then
                                                      config.enable_personal_account_billing
                                                  else
                                                      config.enable_team_account_billing
                               end,
                           'billing_status', bs.status,
                           'created_at', a.created_at,
                           'updated_at', a.updated_at,
                           'metadata', a.public_metadata
                       )
            from basejump.accounts a
                     left join basejump.account_user wu on a.id = wu.account_id and wu.user_id = auth.uid()
                     join basejump.config config on true
                     left join (select bs.account_id, status
                                from basejump.billing_subscriptions bs
                                where bs.account_id = get_account.account_id
                                order by created desc
                                limit 1) bs on bs.account_id = a.id
            where a.id = get_account.account_id);
END;
$$;

grant execute on function public.get_account(uuid) to authenticated, service_role;

/**
  Returns a specific account that the current user has access to
 */
create or replace function public.get_account_by_slug(slug text)
    returns json
    language plpgsql
as
$$
DECLARE
    internal_account_id uuid;
BEGIN
    select a.id
    into internal_account_id
    from basejump.accounts a
    where a.slug IS NOT NULL
      and a.slug = get_account_by_slug.slug;

    return public.get_account(internal_account_id);
END;
$$;

grant execute on function public.get_account_by_slug(text) to authenticated;

/**
  Returns the personal account for the current user
 */
create or replace function public.get_personal_account()
    returns json
    language plpgsql
as
$$
BEGIN
    return public.get_account(auth.uid());
END;
$$;

grant execute on function public.get_personal_account() to authenticated;

/**
  * Create an account
 */
create or replace function public.create_account(slug text default null, name text default null)
    returns json
    language plpgsql
as
$$
DECLARE
    new_account_id uuid;
BEGIN
    insert into basejump.accounts (slug, name)
    values (create_account.slug, create_account.name)
    returning id into new_account_id;

    return public.get_account(new_account_id);
EXCEPTION
    WHEN unique_violation THEN
        raise exception 'An account with that unique ID already exists';
END;
$$;

grant execute on function public.create_account(slug text, name text) to authenticated;

/**
  Update an account with passed in info. None of the info is required except for account ID.
  If you don't pass in a value for a field, it will not be updated.
  If you set replace_meta to true, the metadata will be replaced with the passed in metadata.
  If you set replace_meta to false, the metadata will be merged with the passed in metadata.
 */
create or replace function public.update_account(account_id uuid, slug text default null, name text default null,
                                                 public_metadata jsonb default null,
                                                 replace_metadata boolean default false)
    returns json
    language plpgsql
as
$$
BEGIN

    -- check if postgres role is service_role
    if current_user IN ('anon', 'authenticated') and
       not (select current_user_account_role(update_account.account_id) ->> 'account_role' = 'owner') then
        raise exception 'Only account owners can update an account';
    end if;

    update basejump.accounts accounts
    set slug            = coalesce(update_account.slug, accounts.slug),
        name            = coalesce(update_account.name, accounts.name),
        public_metadata = case
                              when update_account.public_metadata is null then accounts.public_metadata -- do nothing
                              when accounts.public_metadata IS NULL then update_account.public_metadata -- set metadata
                              when update_account.replace_metadata
                                  then update_account.public_metadata -- replace metadata
                              else accounts.public_metadata || update_account.public_metadata end -- merge metadata
    where accounts.id = update_account.account_id;

    return public.get_account(account_id);
END;
$$;

grant execute on function public.update_account(uuid, text, text, jsonb, boolean) to authenticated, service_role;

/**
  Returns a list of current account members. Only account owners can access this function.
  It's a security definer because it requries us to lookup personal_accounts for existing members so we can
  get their names.
 */
create or replace function public.get_account_members(account_id uuid, results_limit integer default 50,
                                                      results_offset integer default 0)
    returns json
    language plpgsql
    security definer
    set search_path = basejump
as
$$
BEGIN

    -- only account owners can access this function
    if (select public.current_user_account_role(get_account_members.account_id) ->> 'account_role' <> 'owner') then
        raise exception 'Only account owners can access this function';
    end if;

    return (select json_agg(
                           json_build_object(
                                   'user_id', wu.user_id,
                                   'account_role', wu.account_role,
                                   'name', p.name,
                                   'email', u.email,
                                   'is_primary_owner', a.primary_owner_user_id = wu.user_id
                               )
                       )
            from basejump.account_user wu
                     join basejump.accounts a on a.id = wu.account_id
                     join basejump.accounts p on p.primary_owner_user_id = wu.user_id and p.personal_account = true
                     join auth.users u on u.id = wu.user_id
            where wu.account_id = get_account_members.account_id
            limit coalesce(get_account_members.results_limit, 50) offset coalesce(get_account_members.results_offset, 0));
END;
$$;

grant execute on function public.get_account_members(uuid, integer, integer) to authenticated;

/**
  Allows an owner of the account to remove any member other than the primary owner
 */

create or replace function public.remove_account_member(account_id uuid, user_id uuid)
    returns void
    language plpgsql
as
$$
BEGIN
    -- only account owners can access this function
    if basejump.has_role_on_account(remove_account_member.account_id, 'owner') <> true then
        raise exception 'Only account owners can access this function';
    end if;

    delete
    from basejump.account_user wu
    where wu.account_id = remove_account_member.account_id
      and wu.user_id = remove_account_member.user_id;
END;
$$;

grant execute on function public.remove_account_member(uuid, uuid) to authenticated;
``````
------ src/main/resources/migrations/20240414162100_basejump-invitations.sql ------
``````
/**
  * -------------------------------------------------------
  * Section - Invitations
  * -------------------------------------------------------
 */

/**
  * Invitations are sent to users to join a account
  * They pre-define the role the user should have once they join
 */
create table if not exists basejump.invitations
(
    -- the id of the invitation
    id                 uuid unique                                              not null default extensions.uuid_generate_v4(),
    -- what role should invitation accepters be given in this account
    account_role       basejump.account_role                                    not null,
    -- the account the invitation is for
    account_id         uuid references basejump.accounts (id) on delete cascade not null,
    -- unique token used to accept the invitation
    token              text unique                                              not null default basejump.generate_token(30),
    -- who created the invitation
    invited_by_user_id uuid references auth.users                               not null,
    -- account name. filled in by a trigger
    account_name       text,
    -- when the invitation was last updated
    updated_at         timestamp with time zone,
    -- when the invitation was created
    created_at         timestamp with time zone,
    -- what type of invitation is this
    invitation_type    basejump.invitation_type                                 not null,
    primary key (id)
);

-- Open up access to invitations
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE basejump.invitations TO authenticated, service_role;

-- manage timestamps
CREATE TRIGGER basejump_set_invitations_timestamp
    BEFORE INSERT OR UPDATE
    ON basejump.invitations
    FOR EACH ROW
EXECUTE FUNCTION basejump.trigger_set_timestamps();

/**
  * This funciton fills in account info and inviting user email
  * so that the recipient can get more info about the invitation prior to
  * accepting.  It allows us to avoid complex permissions on accounts
 */
CREATE OR REPLACE FUNCTION basejump.trigger_set_invitation_details()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.invited_by_user_id = auth.uid();
    NEW.account_name = (select name from basejump.accounts where id = NEW.account_id);
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER basejump_trigger_set_invitation_details
    BEFORE INSERT
    ON basejump.invitations
    FOR EACH ROW
EXECUTE FUNCTION basejump.trigger_set_invitation_details();

-- enable RLS on invitations
alter table basejump.invitations
    enable row level security;

/**
  * -------------------------
  * Section - RLS Policies
  * -------------------------
  * This is where we define access to tables in the basejump schema
 */

 create policy "Invitations viewable by account owners" on basejump.invitations
    for select
    to authenticated
    using (
            created_at > (now() - interval '24 hours')
        and
            basejump.has_role_on_account(account_id, 'owner') = true
    );


create policy "Invitations can be created by account owners" on basejump.invitations
    for insert
    to authenticated
    with check (
    -- team accounts should be enabled
            basejump.is_set('enable_team_accounts') = true
        -- this should not be a personal account
        and (SELECT personal_account
             FROM basejump.accounts
             WHERE id = account_id) = false
        -- the inserting user should be an owner of the account
        and
            (basejump.has_role_on_account(account_id, 'owner') = true)
    );

create policy "Invitations can be deleted by account owners" on basejump.invitations
    for delete
    to authenticated
    using (
    basejump.has_role_on_account(account_id, 'owner') = true
    );



/**
  * -------------------------------------------------------
  * Section - Public functions
  * -------------------------------------------------------
  * Each of these functions exists in the public name space because they are accessible
  * via the API.  it is the primary way developers can interact with Basejump accounts
 */


/**
  Returns a list of currently active invitations for a given account
 */

create or replace function public.get_account_invitations(account_id uuid, results_limit integer default 25,
                                                          results_offset integer default 0)
    returns json
    language plpgsql
as
$$
BEGIN
    -- only account owners can access this function
    if (select public.current_user_account_role(get_account_invitations.account_id) ->> 'account_role' <> 'owner') then
        raise exception 'Only account owners can access this function';
    end if;

    return (select json_agg(
                           json_build_object(
                                   'account_role', i.account_role,
                                   'created_at', i.created_at,
                                   'invitation_type', i.invitation_type,
                                   'invitation_id', i.id
                               )
                       )
            from basejump.invitations i
            where i.account_id = get_account_invitations.account_id
              and i.created_at > now() - interval '24 hours'
            limit coalesce(get_account_invitations.results_limit, 25) offset coalesce(get_account_invitations.results_offset, 0));
END;
$$;

grant execute on function public.get_account_invitations(uuid, integer, integer) to authenticated;


/**
  * Allows a user to accept an existing invitation and join a account
  * This one exists in the public schema because we want it to be called
  * using the supabase rpc method
 */
create or replace function public.accept_invitation(lookup_invitation_token text)
    returns jsonb
    language plpgsql
    security definer set search_path = public, basejump
as
$$
declare
    lookup_account_id       uuid;
    declare new_member_role basejump.account_role;
    lookup_account_slug     text;
begin
    select i.account_id, i.account_role, a.slug
    into lookup_account_id, new_member_role, lookup_account_slug
    from basejump.invitations i
             join basejump.accounts a on a.id = i.account_id
    where i.token = lookup_invitation_token
      and i.created_at > now() - interval '24 hours';

    if lookup_account_id IS NULL then
        raise exception 'Invitation not found';
    end if;

    if lookup_account_id is not null then
        -- we've validated the token is real, so grant the user access
        insert into basejump.account_user (account_id, user_id, account_role)
        values (lookup_account_id, auth.uid(), new_member_role);
        -- email types of invitations are only good for one usage
        delete from basejump.invitations where token = lookup_invitation_token and invitation_type = 'one_time';
    end if;
    return json_build_object('account_id', lookup_account_id, 'account_role', new_member_role, 'slug',
                             lookup_account_slug);
EXCEPTION
    WHEN unique_violation THEN
        raise exception 'You are already a member of this account';
end;
$$;

grant execute on function public.accept_invitation(text) to authenticated;


/**
  * Allows a user to lookup an existing invitation and join a account
  * This one exists in the public schema because we want it to be called
  * using the supabase rpc method
 */
create or replace function public.lookup_invitation(lookup_invitation_token text)
    returns json
    language plpgsql
    security definer set search_path = public, basejump
as
$$
declare
    name              text;
    invitation_active boolean;
begin
    select account_name,
           case when id IS NOT NULL then true else false end as active
    into name, invitation_active
    from basejump.invitations
    where token = lookup_invitation_token
      and created_at > now() - interval '24 hours'
    limit 1;
    return json_build_object('active', coalesce(invitation_active, false), 'account_name', name);
end;
$$;

grant execute on function public.lookup_invitation(text) to authenticated;


/**
  Allows a user to create a new invitation if they are an owner of an account
 */
create or replace function public.create_invitation(account_id uuid, account_role basejump.account_role,
                                                    invitation_type basejump.invitation_type)
    returns json
    language plpgsql
as
$$
declare
    new_invitation basejump.invitations;
begin
    insert into basejump.invitations (account_id, account_role, invitation_type, invited_by_user_id)
    values (account_id, account_role, invitation_type, auth.uid())
    returning * into new_invitation;

    return json_build_object('token', new_invitation.token);
end
$$;

grant execute on function public.create_invitation(uuid, basejump.account_role, basejump.invitation_type) to authenticated;

/**
  Allows an owner to delete an existing invitation
 */

create or replace function public.delete_invitation(invitation_id uuid)
    returns void
    language plpgsql
as
$$
begin
    -- verify account owner for the invitation
    if basejump.has_role_on_account(
               (select account_id from basejump.invitations where id = delete_invitation.invitation_id), 'owner') <>
       true then
        raise exception 'Only account owners can delete invitations';
    end if;

    delete from basejump.invitations where id = delete_invitation.invitation_id;
end
$$;

grant execute on function public.delete_invitation(uuid) to authenticated;
``````
------ src/main/resources/migrations/20240414162131_basejump-billing.sql ------
``````
/**
  * -------------------------------------------------------
  * Section - Billing
  * -------------------------------------------------------
 */

/**
* Subscription Status
* Tracks the current status of the account subscription
*/
DO
$$
    BEGIN
        IF NOT EXISTS(SELECT 1
                      FROM pg_type t
                               JOIN pg_namespace n ON n.oid = t.typnamespace
                      WHERE t.typname = 'subscription_status'
                        AND n.nspname = 'basejump') THEN
            create type basejump.subscription_status as enum (
                'trialing',
                'active',
                'canceled',
                'incomplete',
                'incomplete_expired',
                'past_due',
                'unpaid'
                );
        end if;
    end;
$$;


/**
 * Billing customer
 * This is a private table that contains a mapping of user IDs to your billing providers IDs
 */
create table if not exists basejump.billing_customers
(
    -- UUID from auth.users
    account_id uuid references basejump.accounts (id) on delete cascade not null,
    -- The user's customer ID in Stripe. User must not be able to update this.
    id         text primary key,
    -- The email address the customer wants to use for invoicing
    email      text,
    -- The active status of a customer
    active     boolean,
    -- The billing provider the customer is using
    provider   text
);

-- Open up access to billing_customers
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE basejump.billing_customers TO service_role;
GRANT SELECT ON TABLE basejump.billing_customers TO authenticated;


-- enable RLS for billing_customers
alter table
    basejump.billing_customers
    enable row level security;

/**
  * Billing subscriptions
  * This is a private table that contains a mapping of account IDs to your billing providers subscription IDs
 */
create table if not exists basejump.billing_subscriptions
(
    -- Subscription ID from Stripe, e.g. sub_1234.
    id                   text primary key,
    account_id           uuid references basejump.accounts (id) on delete cascade          not null,
    billing_customer_id  text references basejump.billing_customers (id) on delete cascade not null,
    -- The status of the subscription object, one of subscription_status type above.
    status               basejump.subscription_status,
    -- Set of key-value pairs, used to store additional information about the object in a structured format.
    metadata             jsonb,
    -- ID of the price that created this subscription.
    price_id             text,
    plan_name            text,
    -- Quantity multiplied by the unit amount of the price creates the amount of the subscription. Can be used to charge multiple seats.
    quantity             integer,
    -- If true the subscription has been canceled by the user and will be deleted at the end of the billing period.
    cancel_at_period_end boolean,
    -- Time at which the subscription was created.
    created              timestamp with time zone default timezone('utc' :: text, now())   not null,
    -- Start of the current period that the subscription has been invoiced for.
    current_period_start timestamp with time zone default timezone('utc' :: text, now())   not null,
    -- End of the current period that the subscription has been invoiced for. At the end of this period, a new invoice will be created.
    current_period_end   timestamp with time zone default timezone('utc' :: text, now())   not null,
    -- If the subscription has ended, the timestamp of the date the subscription ended.
    ended_at             timestamp with time zone default timezone('utc' :: text, now()),
    -- A date in the future at which the subscription will automatically get canceled.
    cancel_at            timestamp with time zone default timezone('utc' :: text, now()),
    -- If the subscription has been canceled, the date of that cancellation. If the subscription was canceled with `cancel_at_period_end`, `canceled_at` will still reflect the date of the initial cancellation request, not the end of the subscription period when the subscription is automatically moved to a canceled state.
    canceled_at          timestamp with time zone default timezone('utc' :: text, now()),
    -- If the subscription has a trial, the beginning of that trial.
    trial_start          timestamp with time zone default timezone('utc' :: text, now()),
    -- If the subscription has a trial, the end of that trial.
    trial_end            timestamp with time zone default timezone('utc' :: text, now()),
    provider             text
);

-- Open up access to billing_subscriptions
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE basejump.billing_subscriptions TO service_role;
GRANT SELECT ON TABLE basejump.billing_subscriptions TO authenticated;

-- enable RLS for billing_subscriptions
alter table
    basejump.billing_subscriptions
    enable row level security;

/**
  * -------------------------
  * Section - RLS Policies
  * -------------------------
  * This is where we define access to tables in the basejump schema
 */

create policy "Can only view own billing customer data." on basejump.billing_customers for
    select
    using (
    basejump.has_role_on_account(account_id) = true
    );


create policy "Can only view own billing subscription data." on basejump.billing_subscriptions for
    select
    using (
    basejump.has_role_on_account(account_id) = true
    );

/**
  * -------------------------------------------------------
  * Section - Public functions
  * -------------------------------------------------------
  * Each of these functions exists in the public name space because they are accessible
  * via the API.  it is the primary way developers can interact with Basejump accounts
 */


/**
  * Returns the current billing status for an account
 */
CREATE OR REPLACE FUNCTION public.get_account_billing_status(account_id uuid)
    RETURNS jsonb
    security definer
    set search_path = public, basejump
AS
$$
DECLARE
    result      jsonb;
    role_result jsonb;
BEGIN
    select public.current_user_account_role(get_account_billing_status.account_id) into role_result;

    select jsonb_build_object(
                   'account_id', get_account_billing_status.account_id,
                   'billing_subscription_id', s.id,
                   'billing_enabled', case
                                          when a.personal_account = true then config.enable_personal_account_billing
                                          else config.enable_team_account_billing end,
                   'billing_status', s.status,
                   'billing_customer_id', c.id,
                   'billing_provider', config.billing_provider,
                   'billing_email',
                   coalesce(c.email, u.email) -- if we don't have a customer email, use the user's email as a fallback
               )
    into result
    from basejump.accounts a
             join auth.users u on u.id = a.primary_owner_user_id
             left join basejump.billing_subscriptions s on s.account_id = a.id
             left join basejump.billing_customers c on c.account_id = coalesce(s.account_id, a.id)
             join basejump.config config on true
    where a.id = get_account_billing_status.account_id
    order by s.created desc
    limit 1;

    return result || role_result;
END;
$$ LANGUAGE plpgsql;

grant execute on function public.get_account_billing_status(uuid) to authenticated;

/**
  * Allow service accounts to upsert the billing data for an account
 */
CREATE OR REPLACE FUNCTION public.service_role_upsert_customer_subscription(account_id uuid,
                                                                            customer jsonb default null,
                                                                            subscription jsonb default null)
    RETURNS void AS
$$
BEGIN
    -- if the customer is not null, upsert the data into billing_customers, only upsert fields that are present in the jsonb object
    if customer is not null then
        insert into basejump.billing_customers (id, account_id, email, provider)
        values (customer ->> 'id', service_role_upsert_customer_subscription.account_id, customer ->> 'billing_email',
                (customer ->> 'provider'))
        on conflict (id) do update
            set email = customer ->> 'billing_email';
    end if;

    -- if the subscription is not null, upsert the data into billing_subscriptions, only upsert fields that are present in the jsonb object
    if subscription is not null then
        insert into basejump.billing_subscriptions (id, account_id, billing_customer_id, status, metadata, price_id,
                                                    quantity, cancel_at_period_end, created, current_period_start,
                                                    current_period_end, ended_at, cancel_at, canceled_at, trial_start,
                                                    trial_end, plan_name, provider)
        values (subscription ->> 'id', service_role_upsert_customer_subscription.account_id,
                subscription ->> 'billing_customer_id', (subscription ->> 'status')::basejump.subscription_status,
                subscription -> 'metadata',
                subscription ->> 'price_id', (subscription ->> 'quantity')::int,
                (subscription ->> 'cancel_at_period_end')::boolean,
                (subscription ->> 'created')::timestamptz, (subscription ->> 'current_period_start')::timestamptz,
                (subscription ->> 'current_period_end')::timestamptz, (subscription ->> 'ended_at')::timestamptz,
                (subscription ->> 'cancel_at')::timestamptz,
                (subscription ->> 'canceled_at')::timestamptz, (subscription ->> 'trial_start')::timestamptz,
                (subscription ->> 'trial_end')::timestamptz,
                subscription ->> 'plan_name', (subscription ->> 'provider'))
        on conflict (id) do update
            set billing_customer_id  = subscription ->> 'billing_customer_id',
                status               = (subscription ->> 'status')::basejump.subscription_status,
                metadata             = subscription -> 'metadata',
                price_id             = subscription ->> 'price_id',
                quantity             = (subscription ->> 'quantity')::int,
                cancel_at_period_end = (subscription ->> 'cancel_at_period_end')::boolean,
                current_period_start = (subscription ->> 'current_period_start')::timestamptz,
                current_period_end   = (subscription ->> 'current_period_end')::timestamptz,
                ended_at             = (subscription ->> 'ended_at')::timestamptz,
                cancel_at            = (subscription ->> 'cancel_at')::timestamptz,
                canceled_at          = (subscription ->> 'canceled_at')::timestamptz,
                trial_start          = (subscription ->> 'trial_start')::timestamptz,
                trial_end            = (subscription ->> 'trial_end')::timestamptz,
                plan_name            = subscription ->> 'plan_name';
    end if;
end;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION public.service_role_upsert_customer_subscription(uuid, jsonb, jsonb) TO service_role;
``````
------ src/main/resources/migrations/20250409211903_basejump-configure.sql ------
``````
UPDATE basejump.config SET enable_team_accounts = TRUE;
UPDATE basejump.config SET enable_personal_account_billing = TRUE;
UPDATE basejump.config SET enable_team_account_billing = TRUE;

``````
------ src/main/resources/migrations/20250409212058_initial.sql ------
``````
-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create devices table first
CREATE TABLE public.devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL,
    name TEXT,
    last_seen TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    is_online BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_account FOREIGN KEY (account_id) REFERENCES basejump.accounts(id) ON DELETE CASCADE
);

-- Create recordings table
CREATE TABLE public.recordings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL,
    device_id UUID NOT NULL,
    preprocessed_file_path TEXT,
    meta JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    name TEXT,
    ui_annotated BOOLEAN DEFAULT FALSE,
    a11y_file_path TEXT,
    audio_file_path TEXT,
    action_annotated BOOLEAN DEFAULT FALSE,
    raw_data_file_path TEXT,
    metadata_file_path TEXT,
    action_training_file_path TEXT,
    CONSTRAINT fk_account FOREIGN KEY (account_id) REFERENCES basejump.accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_device FOREIGN KEY (device_id) REFERENCES public.devices(id) ON DELETE CASCADE
);

-- Create indexes for foreign keys
CREATE INDEX idx_recordings_account_id ON public.recordings(account_id);
CREATE INDEX idx_recordings_device_id ON public.recordings(device_id);
CREATE INDEX idx_devices_account_id ON public.devices(account_id);

-- Add RLS policies (optional, can be customized as needed)
ALTER TABLE public.recordings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

-- Create RLS policies for devices
CREATE POLICY "Account members can delete their own devices"
    ON public.devices FOR DELETE
    USING (basejump.has_role_on_account(account_id));

CREATE POLICY "Account members can insert their own devices"
    ON public.devices FOR INSERT
    WITH CHECK (basejump.has_role_on_account(account_id));

CREATE POLICY "Account members can only access their own devices"
    ON public.devices FOR ALL
    USING (basejump.has_role_on_account(account_id));

CREATE POLICY "Account members can update their own devices"
    ON public.devices FOR UPDATE
    USING (basejump.has_role_on_account(account_id));

CREATE POLICY "Account members can view their own devices"
    ON public.devices FOR SELECT
    USING (basejump.has_role_on_account(account_id));

-- Create RLS policies for recordings
CREATE POLICY "Account members can delete their own recordings"
    ON public.recordings FOR DELETE
    USING (basejump.has_role_on_account(account_id));

CREATE POLICY "Account members can insert their own recordings"
    ON public.recordings FOR INSERT
    WITH CHECK (basejump.has_role_on_account(account_id));

CREATE POLICY "Account members can only access their own recordings"
    ON public.recordings FOR ALL
    USING (basejump.has_role_on_account(account_id));

CREATE POLICY "Account members can update their own recordings"
    ON public.recordings FOR UPDATE
    USING (basejump.has_role_on_account(account_id));

CREATE POLICY "Account members can view their own recordings"
    ON public.recordings FOR SELECT
    USING (basejump.has_role_on_account(account_id));

-- Note: For threads and messages, you might want different RLS policies
-- depending on your application's requirements


-- Also drop the old function signature
DROP FUNCTION IF EXISTS transfer_device(UUID, UUID, TEXT);


CREATE OR REPLACE FUNCTION transfer_device(
  device_id UUID,      -- Parameter remains UUID
  new_account_id UUID, -- Changed parameter name and implies new ownership target
  device_name TEXT DEFAULT NULL
)
RETURNS SETOF devices AS $$
DECLARE
  device_exists BOOLEAN;
  updated_device devices;
BEGIN
  -- Check if a device with the specified UUID exists
  SELECT EXISTS (
    SELECT 1 FROM devices WHERE id = device_id
  ) INTO device_exists;

  IF device_exists THEN
    -- Device exists: update its account ownership and last_seen timestamp
    UPDATE devices
    SET
      account_id = new_account_id, -- Update account_id instead of user_id
      name = COALESCE(device_name, name),
      last_seen = NOW()
    WHERE id = device_id
    RETURNING * INTO updated_device;

    RETURN NEXT updated_device;
  ELSE
    -- Device doesn't exist; return nothing so the caller can handle creation
    RETURN;
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Grant execute permission so that authenticated users can call this function
-- Updated function signature
GRANT EXECUTE ON FUNCTION transfer_device(UUID, UUID, TEXT) TO authenticated;




-- Create the ui_grounding bucket
INSERT INTO storage.buckets (id, name, public)
VALUES ('ui_grounding', 'ui_grounding', false)
ON CONFLICT (id) DO NOTHING; -- Avoid error if bucket already exists

-- Create the ui_grounding_trajs bucket
INSERT INTO storage.buckets (id, name, public)
VALUES ('ui_grounding_trajs', 'ui_grounding_trajs', false)
ON CONFLICT (id) DO NOTHING; -- Avoid error if bucket already exists

-- Create the recordings bucket
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('recordings', 'recordings', false, null, null) -- Set file size limit and mime types as needed
ON CONFLICT (id) DO NOTHING; -- Avoid error if bucket already exists


-- RLS policies for the 'recordings' bucket
-- Allow members to view files in accounts they belong to
CREATE POLICY "Account members can select recording files"
    ON storage.objects FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'recordings' AND
        (storage.foldername(name))[1]::uuid IN (SELECT basejump.get_accounts_with_role())
    );

-- Allow members to insert files into accounts they belong to
CREATE POLICY "Account members can insert recording files"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'recordings' AND
        (storage.foldername(name))[1]::uuid IN (SELECT basejump.get_accounts_with_role())
    );

-- Allow members to update files in accounts they belong to
CREATE POLICY "Account members can update recording files"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (
        bucket_id = 'recordings' AND
        (storage.foldername(name))[1]::uuid IN (SELECT basejump.get_accounts_with_role())
    );

-- Allow members to delete files from accounts they belong to
-- Consider restricting this further, e.g., to 'owner' role if needed:
-- (storage.foldername(name))[1]::uuid IN (SELECT basejump.get_accounts_with_role('owner'))
CREATE POLICY "Account members can delete recording files"
    ON storage.objects FOR DELETE
    TO authenticated
    USING (
        bucket_id = 'recordings' AND
        (storage.foldername(name))[1]::uuid IN (SELECT basejump.get_accounts_with_role())
    );

``````
------ src/main/resources/migrations/20250416133920_agentpress_schema.sql ------
``````
-- AGENTPRESS SCHEMA:
-- Create projects table
CREATE TABLE projects (
    project_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    account_id UUID NOT NULL REFERENCES basejump.accounts(id) ON DELETE CASCADE,
    sandbox JSONB DEFAULT '{}'::jsonb,
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Create threads table
CREATE TABLE threads (
    thread_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES basejump.accounts(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(project_id) ON DELETE CASCADE,
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Create messages table
CREATE TABLE messages (
    message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES threads(thread_id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    is_llm_message BOOLEAN NOT NULL DEFAULT TRUE,
    content JSONB NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Create agent_runs table
CREATE TABLE agent_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES threads(thread_id),
    status TEXT NOT NULL DEFAULT 'running',
    started_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    responses JSONB NOT NULL DEFAULT '[]'::jsonb, -- TO BE REMOVED, NOT USED 
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = TIMEZONE('utc'::text, NOW());
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_threads_updated_at
    BEFORE UPDATE ON threads
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_messages_updated_at
    BEFORE UPDATE ON messages
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_agent_runs_updated_at
    BEFORE UPDATE ON agent_runs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create indexes for better query performance
CREATE INDEX idx_threads_created_at ON threads(created_at);
CREATE INDEX idx_threads_account_id ON threads(account_id);
CREATE INDEX idx_threads_project_id ON threads(project_id);
CREATE INDEX idx_agent_runs_thread_id ON agent_runs(thread_id);
CREATE INDEX idx_agent_runs_status ON agent_runs(status);
CREATE INDEX idx_agent_runs_created_at ON agent_runs(created_at);
CREATE INDEX idx_projects_account_id ON projects(account_id);
CREATE INDEX idx_projects_created_at ON projects(created_at);
CREATE INDEX idx_messages_thread_id ON messages(thread_id);
CREATE INDEX idx_messages_created_at ON messages(created_at);

-- Enable Row Level Security
ALTER TABLE threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;

-- Project policies
CREATE POLICY project_select_policy ON projects
    FOR SELECT
    USING (
        is_public = TRUE OR
        basejump.has_role_on_account(account_id) = true
    );

CREATE POLICY project_insert_policy ON projects
    FOR INSERT
    WITH CHECK (basejump.has_role_on_account(account_id) = true);

CREATE POLICY project_update_policy ON projects
    FOR UPDATE
    USING (basejump.has_role_on_account(account_id) = true);

CREATE POLICY project_delete_policy ON projects
    FOR DELETE
    USING (basejump.has_role_on_account(account_id) = true);

-- Thread policies based on project and account ownership
CREATE POLICY thread_select_policy ON threads
    FOR SELECT
    USING (
        basejump.has_role_on_account(account_id) = true OR 
        EXISTS (
            SELECT 1 FROM projects
            WHERE projects.project_id = threads.project_id
            AND (
                projects.is_public = TRUE OR
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

CREATE POLICY thread_insert_policy ON threads
    FOR INSERT
    WITH CHECK (
        basejump.has_role_on_account(account_id) = true OR 
        EXISTS (
            SELECT 1 FROM projects
            WHERE projects.project_id = threads.project_id
            AND basejump.has_role_on_account(projects.account_id) = true
        )
    );

CREATE POLICY thread_update_policy ON threads
    FOR UPDATE
    USING (
        basejump.has_role_on_account(account_id) = true OR 
        EXISTS (
            SELECT 1 FROM projects
            WHERE projects.project_id = threads.project_id
            AND basejump.has_role_on_account(projects.account_id) = true
        )
    );

CREATE POLICY thread_delete_policy ON threads
    FOR DELETE
    USING (
        basejump.has_role_on_account(account_id) = true OR 
        EXISTS (
            SELECT 1 FROM projects
            WHERE projects.project_id = threads.project_id
            AND basejump.has_role_on_account(projects.account_id) = true
        )
    );

-- Create policies for agent_runs based on thread ownership
CREATE POLICY agent_run_select_policy ON agent_runs
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM threads
            LEFT JOIN projects ON threads.project_id = projects.project_id
            WHERE threads.thread_id = agent_runs.thread_id
            AND (
                projects.is_public = TRUE OR
                basejump.has_role_on_account(threads.account_id) = true OR 
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

CREATE POLICY agent_run_insert_policy ON agent_runs
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM threads
            LEFT JOIN projects ON threads.project_id = projects.project_id
            WHERE threads.thread_id = agent_runs.thread_id
            AND (
                basejump.has_role_on_account(threads.account_id) = true OR 
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

CREATE POLICY agent_run_update_policy ON agent_runs
    FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM threads
            LEFT JOIN projects ON threads.project_id = projects.project_id
            WHERE threads.thread_id = agent_runs.thread_id
            AND (
                basejump.has_role_on_account(threads.account_id) = true OR 
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

CREATE POLICY agent_run_delete_policy ON agent_runs
    FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM threads
            LEFT JOIN projects ON threads.project_id = projects.project_id
            WHERE threads.thread_id = agent_runs.thread_id
            AND (
                basejump.has_role_on_account(threads.account_id) = true OR 
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

-- Create message policies based on thread ownership
CREATE POLICY message_select_policy ON messages
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM threads
            LEFT JOIN projects ON threads.project_id = projects.project_id
            WHERE threads.thread_id = messages.thread_id
            AND (
                projects.is_public = TRUE OR
                basejump.has_role_on_account(threads.account_id) = true OR 
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

CREATE POLICY message_insert_policy ON messages
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM threads
            LEFT JOIN projects ON threads.project_id = projects.project_id
            WHERE threads.thread_id = messages.thread_id
            AND (
                basejump.has_role_on_account(threads.account_id) = true OR 
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

CREATE POLICY message_update_policy ON messages
    FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM threads
            LEFT JOIN projects ON threads.project_id = projects.project_id
            WHERE threads.thread_id = messages.thread_id
            AND (
                basejump.has_role_on_account(threads.account_id) = true OR 
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

CREATE POLICY message_delete_policy ON messages
    FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM threads
            LEFT JOIN projects ON threads.project_id = projects.project_id
            WHERE threads.thread_id = messages.thread_id
            AND (
                basejump.has_role_on_account(threads.account_id) = true OR 
                basejump.has_role_on_account(projects.account_id) = true
            )
        )
    );

-- Grant permissions to roles
GRANT ALL PRIVILEGES ON TABLE projects TO authenticated, service_role;
GRANT SELECT ON TABLE projects TO anon;
GRANT SELECT ON TABLE threads TO authenticated, anon, service_role;
GRANT SELECT ON TABLE messages TO authenticated, anon, service_role;
GRANT ALL PRIVILEGES ON TABLE agent_runs TO authenticated, service_role;

-- Create a function that matches the Python get_messages behavior
CREATE OR REPLACE FUNCTION get_llm_formatted_messages(p_thread_id UUID)
RETURNS JSONB
SECURITY DEFINER -- Changed to SECURITY DEFINER to allow service role access
LANGUAGE plpgsql
AS $$
DECLARE
    messages_array JSONB := '[]'::JSONB;
    has_access BOOLEAN;
    current_role TEXT;
    latest_summary_id UUID;
    latest_summary_time TIMESTAMP WITH TIME ZONE;
    is_project_public BOOLEAN;
BEGIN
    -- Get current role
    SELECT current_user INTO current_role;
    
    -- Check if associated project is public
    SELECT p.is_public INTO is_project_public
    FROM threads t
    LEFT JOIN projects p ON t.project_id = p.project_id
    WHERE t.thread_id = p_thread_id;
    
    -- Skip access check for service_role or public projects
    IF current_role = 'authenticated' AND NOT is_project_public THEN
        -- Check if thread exists and user has access
        SELECT EXISTS (
            SELECT 1 FROM threads t
            LEFT JOIN projects p ON t.project_id = p.project_id
            WHERE t.thread_id = p_thread_id
            AND (
                basejump.has_role_on_account(t.account_id) = true OR 
                basejump.has_role_on_account(p.account_id) = true
            )
        ) INTO has_access;
        
        IF NOT has_access THEN
            RAISE EXCEPTION 'Thread not found or access denied';
        END IF;
    END IF;

    -- Find the latest summary message if it exists
    SELECT message_id, created_at
    INTO latest_summary_id, latest_summary_time
    FROM messages
    WHERE thread_id = p_thread_id
    AND type = 'summary'
    AND is_llm_message = TRUE
    ORDER BY created_at DESC
    LIMIT 1;
    
    -- Log whether a summary was found (helpful for debugging)
    IF latest_summary_id IS NOT NULL THEN
        RAISE NOTICE 'Found latest summary message: id=%, time=%', latest_summary_id, latest_summary_time;
    ELSE
        RAISE NOTICE 'No summary message found for thread %', p_thread_id;
    END IF;

    -- Parse content if it's stored as a string and return proper JSON objects
    WITH parsed_messages AS (
        SELECT 
            message_id,
            CASE 
                WHEN jsonb_typeof(content) = 'string' THEN content::text::jsonb
                ELSE content
            END AS parsed_content,
            created_at,
            type
        FROM messages
        WHERE thread_id = p_thread_id
        AND is_llm_message = TRUE
        AND (
            -- Include the latest summary and all messages after it,
            -- or all messages if no summary exists
            latest_summary_id IS NULL 
            OR message_id = latest_summary_id 
            OR created_at > latest_summary_time
        )
        ORDER BY created_at
    )
    SELECT JSONB_AGG(parsed_content)
    INTO messages_array
    FROM parsed_messages;
    
    -- Handle the case when no messages are found
    IF messages_array IS NULL THEN
        RETURN '[]'::JSONB;
    END IF;
    
    RETURN messages_array;
END;
$$;

-- Grant execute permissions
GRANT EXECUTE ON FUNCTION get_llm_formatted_messages TO authenticated, anon, service_role;
``````
------ src/main/resources/migrations/20250504123828_fix_thread_select_policy.sql ------
``````
DROP POLICY IF EXISTS thread_select_policy ON threads;

CREATE POLICY thread_select_policy ON threads
FOR SELECT
USING (
    is_public IS TRUE
    OR basejump.has_role_on_account(account_id) = true
    OR EXISTS (
        SELECT 1 FROM projects
        WHERE projects.project_id = threads.project_id
        AND (
            projects.is_public IS TRUE
            OR basejump.has_role_on_account(projects.account_id) = true
        )
    )
);

``````
------ src/main/resources/migrations/supabase_functions.sql ------
``````
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

``````
