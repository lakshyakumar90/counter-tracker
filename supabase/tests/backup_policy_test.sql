-- Structural policy checks; run with `supabase test db` against a disposable local DB.
do $$
declare
    table_name text;
    row_security boolean;
    owner_policy_count integer;
begin
    foreach table_name in array array[
        'pace_activities',
        'pace_workout_templates',
        'pace_planned_sessions',
        'pace_goals',
        'pace_personal_records',
        'pace_saved_routes'
    ] loop
        select c.relrowsecurity into row_security
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = table_name;
        if row_security is distinct from true then
            raise exception 'RLS is not enabled on public.%', table_name;
        end if;
        select count(*) into owner_policy_count
        from pg_policies
        where schemaname = 'public'
          and tablename = table_name
          and policyname = table_name || '_owner_access'
          and 'authenticated' = any(roles)
          and qual like '%auth.uid()%'
          and with_check like '%auth.uid()%';
        if owner_policy_count <> 1 then
            raise exception 'Expected one authenticated owner policy on public.%', table_name;
        end if;
    end loop;

    if exists (
        select 1 from storage.buckets
        where id in ('pace-private-tracks', 'pace-private-backups') and public
    ) then
        raise exception 'Pace backup Storage buckets must remain private';
    end if;
    if (select count(*) from storage.buckets where id in ('pace-private-tracks', 'pace-private-backups')) <> 2 then
        raise exception 'Expected both Pace private Storage buckets';
    end if;
    if not exists (
        select 1 from pg_policies
        where schemaname = 'storage' and tablename = 'objects'
          and policyname = 'pace-private-tracks_read_own'
          and qual like '%storage.foldername(name)%'
    ) then
        raise exception 'Track object reads must be scoped to an account folder';
    end if;

    if not exists (
        select 1 from pg_proc p
        where p.oid = to_regprocedure('public.pace_backup_preview()')
          and p.prosecdef
          and has_function_privilege('authenticated', p.oid, 'EXECUTE')
          and not has_function_privilege('anon', p.oid, 'EXECUTE')
    ) then
            raise exception 'Backup preview must be an authenticated-only, fixed-search-path quota/usage summary';
    end if;
    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'pace_activities'
          and column_name = 'track_archive_bytes'
    ) or not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'pace_saved_routes'
          and column_name = 'route_archive_bytes'
    ) then
        raise exception 'Backup preview archive size columns are missing';
    end if;

    if not exists (
        select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'pace_backup_quota_settings' and c.relrowsecurity
    ) or has_table_privilege('anon', 'public.pace_backup_quota_settings', 'SELECT')
      or has_table_privilege('authenticated', 'public.pace_backup_quota_settings', 'SELECT')
      or has_table_privilege('service_role', 'public.pace_backup_quota_settings', 'SELECT') then
        raise exception 'Backup quota configuration must be private to the database owner';
    end if;
    if not exists (select 1 from public.pace_backup_quota_settings where singleton)
       or not exists (
           select 1 from pg_constraint c
           where c.conrelid = 'public.pace_backup_quota_settings'::regclass
             and pg_get_constraintdef(c.oid) like '%per_user_bytes IS NULL%'
             and pg_get_constraintdef(c.oid) like '%per_user_bytes > 0%'
       ) then
        raise exception 'Backup quota must have a singleton config and positive-or-unset validation';
    end if;
    if not exists (
        select 1 from pg_proc p
        where p.oid = to_regprocedure('public.pace_backup_upload_check(text,bigint)')
          and p.prosecdef
          and has_function_privilege('authenticated', p.oid, 'EXECUTE')
          and not has_function_privilege('anon', p.oid, 'EXECUTE')
    ) then
        raise exception 'Backup upload quota preflight must be authenticated and owner-scoped';
    end if;
    if not exists (
        select 1 from pg_proc p
        where p.oid = to_regprocedure('public.pace_storage_quota_allows(text,text,jsonb)')
          and p.prosecdef
          and p.provolatile = 'v'
          and has_function_privilege('authenticated', p.oid, 'EXECUTE')
          and not has_function_privilege('anon', p.oid, 'EXECUTE')
    ) then
        raise exception 'Authenticated Storage quota helper must serialize and enforce configured limits';
    end if;
    if (
        select count(*) from pg_policies
        where schemaname = 'storage' and tablename = 'objects'
          and policyname in (
              'pace-private-tracks_insert_own', 'pace-private-tracks_update_own',
              'pace-private-backups_insert_own', 'pace-private-backups_update_own'
          )
          and with_check like '%pace_storage_quota_allows%'
    ) <> 4 then
        raise exception 'Both private buckets must enforce the quota helper on inserts and upserts';
    end if;

    if not exists (
        select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = 'pace_ai_control' and c.relrowsecurity
    ) or has_table_privilege('anon', 'public.pace_ai_control', 'SELECT')
      or has_table_privilege('authenticated', 'public.pace_ai_control', 'SELECT') then
        raise exception 'AI control must be server-only and protected by RLS';
    end if;
    if (select enabled from public.pace_ai_control where id) is distinct from false then
        raise exception 'AI control must start disabled';
    end if;
    if not exists (
        select 1 from pg_proc p
        where p.oid = to_regprocedure('public.pace_reserve_ai_request(uuid,text,integer,integer,integer,text,numeric,numeric)')
          and pg_get_function_arguments(p.oid) like '%p_user_monthly_request_limit integer%'
          and pg_get_function_arguments(p.oid) like '%p_prompt_version text%'
          and has_function_privilege('service_role', p.oid, 'EXECUTE')
          and not has_function_privilege('authenticated', p.oid, 'EXECUTE')
    ) then
        raise exception 'AI quota reservation must enforce a service-only per-user monthly cap';
    end if;
end;
$$;
