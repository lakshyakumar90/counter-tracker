-- Per-account private Storage quota. The owner configures the limit only after
-- reviewing the selected Supabase plan. NULL means no app-enforced account cap.
create table if not exists public.pace_backup_quota_settings (
    singleton boolean primary key default true check (singleton),
    per_user_bytes bigint check (per_user_bytes is null or per_user_bytes > 0),
    updated_at timestamptz not null default now()
);

insert into public.pace_backup_quota_settings (singleton, per_user_bytes)
values (true, null)
on conflict (singleton) do nothing;

alter table public.pace_backup_quota_settings enable row level security;
revoke all on table public.pace_backup_quota_settings from public, anon, authenticated, service_role;

create or replace function public.pace_backup_upload_check(p_object_path text, p_archive_bytes bigint)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_limit bigint;
    v_used bigint;
    v_existing bigint;
    v_projected bigint;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    if p_archive_bytes is null or p_archive_bytes not between 1 and 104857600
       or p_object_path is null
       or p_object_path !~ ('^' || v_user_id::text || '/([0-9a-fA-F-]{36}\.pace\.gz|routes/[0-9a-fA-F-]{36}\.pace\.gz)$') then
        raise exception 'Invalid Pace archive path or size' using errcode = '22023';
    end if;

    select per_user_bytes into v_limit
    from public.pace_backup_quota_settings where singleton = true;

    select coalesce(sum(coalesce(nullif(o.metadata ->> 'size', '')::bigint, 0)), 0)
      into v_used
      from storage.objects as o
     where o.bucket_id in ('pace-private-tracks', 'pace-private-backups')
       and (storage.foldername(o.name))[1] = v_user_id::text;

    select coalesce(nullif(o.metadata ->> 'size', '')::bigint, 0)
      into v_existing
      from storage.objects as o
     where o.bucket_id = 'pace-private-tracks'
       and o.name = p_object_path;
    v_existing := coalesce(v_existing, 0);
    v_projected := greatest(0, v_used - v_existing) + p_archive_bytes;

    return jsonb_build_object(
        'allowed', v_limit is null or v_projected <= v_limit,
        'storage_used_bytes', v_used,
        'storage_projected_bytes', v_projected,
        'storage_quota_bytes', v_limit,
        'storage_remaining_bytes', case when v_limit is null then null else greatest(0, v_limit - v_used) end
    );
end;
$$;

revoke all on function public.pace_backup_upload_check(text, bigint) from public, anon;
grant execute on function public.pace_backup_upload_check(text, bigint) to authenticated;

-- The preflight RPC gives the app a useful early message. Storage's supported
-- RLS policy path is the authoritative check; do not add triggers or otherwise
-- mutate Supabase's managed storage.objects schema.
create or replace function public.pace_storage_quota_allows(
    p_bucket_id text,
    p_object_name text,
    p_metadata jsonb
)
returns boolean
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_limit bigint;
    v_new_bytes bigint;
    v_existing_bytes bigint;
begin
    if v_user_id is null
       or p_bucket_id not in ('pace-private-tracks', 'pace-private-backups')
       or coalesce(array_length(storage.foldername(p_object_name), 1), 0) < 1
       or (storage.foldername(p_object_name))[1] <> v_user_id::text then
        return false;
    end if;

    select per_user_bytes into v_limit
    from public.pace_backup_quota_settings where singleton = true;
    if v_limit is null then
        return true;
    end if;

    -- Serialize quota checks for this owner until the Storage API transaction
    -- commits, so concurrent uploads cannot both spend the same remaining bytes.
    perform pg_advisory_xact_lock(hashtextextended(v_user_id::text, 17017));

    v_new_bytes := nullif(p_metadata ->> 'size', '')::bigint;
    if v_new_bytes is null or v_new_bytes < 0 then
        return false;
    end if;

    select coalesce(sum(coalesce(nullif(o.metadata ->> 'size', '')::bigint, 0)), 0)
      into v_existing_bytes
      from storage.objects as o
     where o.bucket_id in ('pace-private-tracks', 'pace-private-backups')
       and (storage.foldername(o.name))[1] = v_user_id::text
       and not (o.bucket_id = p_bucket_id and o.name = p_object_name);

    if v_existing_bytes + v_new_bytes > v_limit then
        raise exception 'PACE_BACKUP_QUOTA_EXCEEDED' using errcode = 'P0001';
    end if;
    return true;
end;
$$;

revoke all on function public.pace_storage_quota_allows(text, text, jsonb) from public, anon;
grant execute on function public.pace_storage_quota_allows(text, text, jsonb) to authenticated;

do $$
declare bucket text;
begin
    foreach bucket in array array['pace-private-tracks', 'pace-private-backups'] loop
        execute format('drop policy if exists %I on storage.objects', bucket || '_insert_own');
        execute format(
            'create policy %I on storage.objects for insert to authenticated with check (bucket_id = %L and (storage.foldername(name))[1] = (select auth.uid())::text and public.pace_storage_quota_allows(bucket_id, name, metadata))',
            bucket || '_insert_own', bucket
        );
        execute format('drop policy if exists %I on storage.objects', bucket || '_update_own');
        execute format(
            'create policy %I on storage.objects for update to authenticated using (bucket_id = %L and (storage.foldername(name))[1] = (select auth.uid())::text) with check (bucket_id = %L and (storage.foldername(name))[1] = (select auth.uid())::text and public.pace_storage_quota_allows(bucket_id, name, metadata))',
            bucket || '_update_own', bucket, bucket
        );
    end loop;
end $$;

create or replace function public.pace_backup_preview()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_limit bigint;
    v_used bigint;
    v_metadata_estimate bigint;
    v_activity_count bigint;
    v_route_count bigint;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    select per_user_bytes into v_limit
    from public.pace_backup_quota_settings where singleton = true;

    select coalesce(sum(coalesce(nullif(o.metadata ->> 'size', '')::bigint, 0)), 0)
      into v_used
      from storage.objects as o
     where o.bucket_id in ('pace-private-tracks', 'pace-private-backups')
       and (storage.foldername(o.name))[1] = v_user_id::text;

    select
        coalesce((select sum(octet_length(to_jsonb(a)::text)) from public.pace_activities as a where a.user_id = v_user_id), 0)
      + coalesce((select sum(octet_length(to_jsonb(t)::text)) from public.pace_workout_templates as t where t.user_id = v_user_id), 0)
      + coalesce((select sum(octet_length(to_jsonb(s)::text)) from public.pace_planned_sessions as s where s.user_id = v_user_id), 0)
      + coalesce((select sum(octet_length(to_jsonb(g)::text)) from public.pace_goals as g where g.user_id = v_user_id), 0)
      + coalesce((select sum(octet_length(to_jsonb(r)::text)) from public.pace_personal_records as r where r.user_id = v_user_id), 0)
      + coalesce((select sum(octet_length(to_jsonb(sr)::text)) from public.pace_saved_routes as sr where sr.user_id = v_user_id), 0)
      into v_metadata_estimate;

    select count(*) into v_activity_count from public.pace_activities where user_id = v_user_id and deleted_at_epoch_millis is null;
    select count(*) into v_route_count from public.pace_saved_routes where user_id = v_user_id and deleted_at_epoch_millis is null;

    return jsonb_build_object(
        'activity_count', v_activity_count,
        'route_count', v_route_count,
        'metadata_bytes_estimate', v_metadata_estimate,
        'track_archive_bytes', v_used,
        'storage_used_bytes', v_used,
        'storage_quota_bytes', v_limit,
        'storage_remaining_bytes', case when v_limit is null then null else greatest(0, v_limit - v_used) end,
        'storage_over_quota', v_limit is not null and v_used > v_limit,
        'storage_quota_warning', v_limit is not null and v_used >= ((v_limit::numeric * 0.8)::bigint)
    );
end;
$$;

revoke all on function public.pace_backup_preview() from public, anon;
grant execute on function public.pace_backup_preview() to authenticated;
