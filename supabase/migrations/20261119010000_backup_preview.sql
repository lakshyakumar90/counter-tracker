-- Store uploaded object lengths so the signed-in owner can preview their backup
-- without downloading any private track archives.
alter table public.pace_activities
    add column if not exists track_archive_bytes bigint
    check (track_archive_bytes is null or track_archive_bytes between 1 and 104857600);

alter table public.pace_saved_routes
    add column if not exists route_archive_bytes bigint
    check (route_archive_bytes is null or route_archive_bytes between 1 and 104857600);

create or replace function public.pace_backup_preview()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
    select jsonb_build_object(
        'activity_count', (select count(*) from public.pace_activities where user_id = (select auth.uid()) and deleted_at_epoch_millis is null),
        'route_count', (select count(*) from public.pace_saved_routes where user_id = (select auth.uid()) and deleted_at_epoch_millis is null),
        'metadata_bytes_estimate',
            coalesce((select sum(octet_length(to_jsonb(row_data)::text)) from public.pace_activities as row_data where user_id = (select auth.uid())), 0)
          + coalesce((select sum(octet_length(to_jsonb(row_data)::text)) from public.pace_workout_templates as row_data where user_id = (select auth.uid())), 0)
          + coalesce((select sum(octet_length(to_jsonb(row_data)::text)) from public.pace_planned_sessions as row_data where user_id = (select auth.uid())), 0)
          + coalesce((select sum(octet_length(to_jsonb(row_data)::text)) from public.pace_goals as row_data where user_id = (select auth.uid())), 0)
          + coalesce((select sum(octet_length(to_jsonb(row_data)::text)) from public.pace_personal_records as row_data where user_id = (select auth.uid())), 0)
          + coalesce((select sum(octet_length(to_jsonb(row_data)::text)) from public.pace_saved_routes as row_data where user_id = (select auth.uid())), 0),
        'track_archive_bytes',
            coalesce((select sum(track_archive_bytes) from public.pace_activities where user_id = (select auth.uid())), 0)
          + coalesce((select sum(route_archive_bytes) from public.pace_saved_routes where user_id = (select auth.uid())), 0)
    );
$$;

revoke all on function public.pace_backup_preview() from public, anon;
grant execute on function public.pace_backup_preview() to authenticated;
