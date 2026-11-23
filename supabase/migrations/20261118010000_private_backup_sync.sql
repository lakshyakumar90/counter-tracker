-- Room remains the offline source of truth. Track geometry is stored privately.
create table if not exists public.pace_activities (
    user_id uuid not null references auth.users (id) on delete cascade,
    activity_id uuid not null,
    sport text not null check (sport in ('CYCLING', 'RUNNING', 'JOGGING', 'WALKING', 'HOME_WORKOUT')),
    started_at_epoch_millis bigint not null,
    ended_at_epoch_millis bigint,
    elapsed_seconds bigint not null default 0 check (elapsed_seconds >= 0),
    moving_seconds bigint not null default 0 check (moving_seconds between 0 and elapsed_seconds),
    distance_meters bigint not null default 0 check (distance_meters >= 0),
    elevation_gain_meters double precision,
    title text check (title is null or char_length(title) <= 80),
    notes text check (notes is null or char_length(notes) <= 1000),
    data_quality_flags text not null default '',
    track_object_path text,
    track_sha256 text,
    track_format_version smallint,
    created_at_epoch_millis bigint not null,
    updated_at_epoch_millis bigint not null,
    deleted_at_epoch_millis bigint,
    revision bigint not null default 1,
    primary key (user_id, activity_id),
    check ((track_object_path is null and track_sha256 is null and track_format_version is null)
        or (track_object_path is not null and track_sha256 ~ '^[0-9a-f]{64}$' and track_format_version > 0))
);
create index if not exists pace_activities_owner_started_idx on public.pace_activities(user_id, started_at_epoch_millis desc);
create index if not exists pace_activities_owner_updated_idx on public.pace_activities(user_id, updated_at_epoch_millis);

create table if not exists public.pace_workout_templates (
    user_id uuid not null references auth.users (id) on delete cascade,
    template_id uuid not null,
    name text not null check (char_length(name) between 1 and 60),
    sport text not null,
    description text,
    created_at_epoch_millis bigint not null,
    target_duration_seconds bigint check (target_duration_seconds is null or target_duration_seconds > 0),
    target_distance_meters double precision check (target_distance_meters is null or target_distance_meters > 0),
    intervals jsonb not null default '[]'::jsonb check (jsonb_typeof(intervals) = 'array'),
    updated_at_epoch_millis bigint not null,
    deleted_at_epoch_millis bigint,
    revision bigint not null default 1,
    primary key (user_id, template_id)
);
create index if not exists pace_templates_owner_updated_idx on public.pace_workout_templates(user_id, updated_at_epoch_millis);

create table if not exists public.pace_planned_sessions (
    user_id uuid not null references auth.users (id) on delete cascade,
    session_id uuid not null,
    template_id uuid,
    scheduled_date date not null,
    time_zone_id text not null,
    reminder_enabled boolean not null default false,
    status text not null default 'PLANNED' check (status in ('PLANNED', 'COMPLETED', 'SKIPPED', 'MISSED')),
    completed_activity_id uuid,
    notes text,
    updated_at_epoch_millis bigint not null,
    deleted_at_epoch_millis bigint,
    revision bigint not null default 1,
    primary key (user_id, session_id)
);
create index if not exists pace_planned_owner_date_idx on public.pace_planned_sessions(user_id, scheduled_date);
create index if not exists pace_planned_owner_updated_idx on public.pace_planned_sessions(user_id, updated_at_epoch_millis);

create table if not exists public.pace_goals (
    user_id uuid not null references auth.users (id) on delete cascade,
    goal_id uuid not null,
    goal_type text not null,
    sport text,
    target_value double precision not null check (target_value > 0),
    unit text not null,
    created_at_epoch_millis bigint not null,
    start_date date not null,
    end_date date not null check (end_date >= start_date),
    updated_at_epoch_millis bigint not null,
    deleted_at_epoch_millis bigint,
    revision bigint not null default 1,
    primary key (user_id, goal_id)
);

create table if not exists public.pace_personal_records (
    user_id uuid not null references auth.users (id) on delete cascade,
    record_id uuid not null,
    sport text not null,
    metric text not null,
    distance_meters double precision,
    record_value double precision not null,
    unit text not null,
    provenance text not null default 'RESTORED',
    achieved_at_epoch_millis bigint not null,
    activity_id uuid,
    updated_at_epoch_millis bigint not null,
    deleted_at_epoch_millis bigint,
    revision bigint not null default 1,
    primary key (user_id, record_id)
);

create table if not exists public.pace_saved_routes (
    user_id uuid not null references auth.users (id) on delete cascade,
    route_id uuid not null,
    title text not null check (char_length(title) between 1 and 80),
    notes text,
    sport text not null check (sport in ('CYCLING', 'RUNNING', 'JOGGING', 'WALKING')),
    distance_meters double precision not null check (distance_meters >= 0),
    source_activity_id uuid,
    created_at_epoch_millis bigint not null,
    updated_at_epoch_millis bigint not null,
    route_object_path text,
    route_sha256 text,
    route_format_version smallint,
    deleted_at_epoch_millis bigint,
    revision bigint not null default 1,
    primary key (user_id, route_id),
    check ((route_object_path is null and route_sha256 is null and route_format_version is null)
        or (route_object_path is not null and route_sha256 ~ '^[0-9a-f]{64}$' and route_format_version > 0))
);
create index if not exists pace_routes_owner_updated_idx on public.pace_saved_routes(user_id, updated_at_epoch_millis);

create table if not exists public.pace_sync_devices (
    user_id uuid not null references auth.users (id) on delete cascade,
    device_id uuid not null,
    last_server_updated_at timestamptz not null default 'epoch',
    last_seen_at timestamptz not null default now(),
    primary key (user_id, device_id)
);

create or replace function public.pace_bump_revision()
returns trigger language plpgsql set search_path = '' as $$
begin
    new.revision := old.revision + 1;
    return new;
end;
$$;
revoke all on function public.pace_bump_revision() from public, anon, authenticated;

do $$
declare table_name text;
begin
    foreach table_name in array array['pace_activities', 'pace_workout_templates', 'pace_planned_sessions', 'pace_goals', 'pace_personal_records', 'pace_saved_routes'] loop
        execute format('alter table public.%I enable row level security', table_name);
        execute format('revoke all on table public.%I from anon', table_name);
        execute format('grant select, insert, update, delete on table public.%I to authenticated', table_name);
        execute format('drop policy if exists %I on public.%I', table_name || '_owner_access', table_name);
        execute format('create policy %I on public.%I for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id)', table_name || '_owner_access', table_name);
        execute format('drop trigger if exists %I on public.%I', table_name || '_revision', table_name);
        execute format('create trigger %I before update on public.%I for each row execute function public.pace_bump_revision()', table_name || '_revision', table_name);
    end loop;
end $$;

alter table public.pace_sync_devices enable row level security;
revoke all on table public.pace_sync_devices from anon;
grant select, insert, update, delete on table public.pace_sync_devices to authenticated;
drop policy if exists pace_sync_devices_owner_access on public.pace_sync_devices;
create policy pace_sync_devices_owner_access on public.pace_sync_devices for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    ('pace-private-tracks', 'pace-private-tracks', false, 104857600, array['application/gzip', 'application/octet-stream']),
    ('pace-private-backups', 'pace-private-backups', false, 104857600, array['application/gzip', 'application/json', 'application/octet-stream'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

do $$
declare bucket text;
begin
    foreach bucket in array array['pace-private-tracks', 'pace-private-backups'] loop
        execute format('drop policy if exists %I on storage.objects', bucket || '_read_own');
        execute format('create policy %I on storage.objects for select to authenticated using (bucket_id = %L and (storage.foldername(name))[1] = (select auth.uid())::text)', bucket || '_read_own', bucket);
        execute format('drop policy if exists %I on storage.objects', bucket || '_insert_own');
        execute format('create policy %I on storage.objects for insert to authenticated with check (bucket_id = %L and (storage.foldername(name))[1] = (select auth.uid())::text)', bucket || '_insert_own', bucket);
        execute format('drop policy if exists %I on storage.objects', bucket || '_update_own');
        execute format('create policy %I on storage.objects for update to authenticated using (bucket_id = %L and (storage.foldername(name))[1] = (select auth.uid())::text) with check (bucket_id = %L and (storage.foldername(name))[1] = (select auth.uid())::text)', bucket || '_update_own', bucket, bucket);
        execute format('drop policy if exists %I on storage.objects', bucket || '_delete_own');
        execute format('create policy %I on storage.objects for delete to authenticated using (bucket_id = %L and (storage.foldername(name))[1] = (select auth.uid())::text)', bucket || '_delete_own', bucket);
    end loop;
end $$;

comment on table public.pace_activities is 'Private activity summaries and metadata; route coordinates live in private object storage.';
comment on table public.pace_sync_devices is 'Per-account backup cursor; Room remains the client source of truth.';
