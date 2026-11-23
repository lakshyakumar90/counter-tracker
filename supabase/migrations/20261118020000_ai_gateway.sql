create table if not exists public.pace_ai_preferences (
    user_id uuid primary key references auth.users (id) on delete cascade,
    opted_in boolean not null default false,
    updated_at timestamptz not null default now()
);
alter table public.pace_ai_preferences enable row level security;
revoke all on table public.pace_ai_preferences from anon;
grant select on table public.pace_ai_preferences to authenticated;
drop policy if exists pace_ai_preferences_owner_read on public.pace_ai_preferences;
create policy pace_ai_preferences_owner_read on public.pace_ai_preferences
    for select to authenticated using ((select auth.uid()) = user_id);

create table if not exists public.pace_ai_usage (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    kind text not null check (kind in ('insight', 'workout')),
    input_tokens integer not null default 0 check (input_tokens >= 0),
    output_tokens integer not null default 0 check (output_tokens >= 0),
    estimated_cost_usd numeric(12, 8) not null default 0 check (estimated_cost_usd >= 0),
    prompt_version text not null check (char_length(prompt_version) between 1 and 64),
    status text not null check (status in ('RESERVED', 'SUCCEEDED', 'FAILED'))
);
create index if not exists pace_ai_usage_user_created_idx on public.pace_ai_usage(user_id, created_at desc);
alter table public.pace_ai_usage enable row level security;
revoke all on table public.pace_ai_usage from anon, authenticated;
grant select on table public.pace_ai_usage to authenticated;
drop policy if exists pace_ai_usage_owner_read on public.pace_ai_usage;
create policy pace_ai_usage_owner_read on public.pace_ai_usage
    for select to authenticated using ((select auth.uid()) = user_id);

create or replace function public.pace_reserve_ai_request(
    p_user_id uuid,
    p_kind text,
    p_daily_limit integer,
    p_monthly_request_limit integer,
    p_user_monthly_request_limit integer,
    p_prompt_version text,
    p_monthly_budget_usd numeric,
    p_reserved_cost_usd numeric
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    reservation_id uuid;
    daily_count integer;
    user_monthly_count integer;
    global_monthly_count integer;
    monthly_cost numeric(12, 8);
begin
    if p_kind not in ('insight', 'workout') or p_daily_limit < 1 or p_monthly_request_limit < 1
       or p_user_monthly_request_limit < 1
       or p_prompt_version is null or char_length(p_prompt_version) not between 1 and 64
       or p_monthly_budget_usd <= 0 or p_reserved_cost_usd < 0 then
        return null;
    end if;
    -- Serialize global monthly reservations across accounts to enforce the developer budget.
    perform pg_advisory_xact_lock(78123211);
    if not exists (select 1 from public.pace_ai_preferences where user_id = p_user_id and opted_in) then
        return null;
    end if;
    select count(*) into daily_count from public.pace_ai_usage
      where user_id = p_user_id and created_at >= date_trunc('day', now());
    select count(*) into user_monthly_count from public.pace_ai_usage
      where user_id = p_user_id and created_at >= date_trunc('month', now());
    select count(*), coalesce(sum(estimated_cost_usd), 0) into global_monthly_count, monthly_cost
      from public.pace_ai_usage where created_at >= date_trunc('month', now());
    if daily_count >= p_daily_limit or user_monthly_count >= p_user_monthly_request_limit
       or global_monthly_count >= p_monthly_request_limit
       or monthly_cost + p_reserved_cost_usd > p_monthly_budget_usd then
        return null;
    end if;
    insert into public.pace_ai_usage(user_id, kind, estimated_cost_usd, prompt_version, status)
      values (p_user_id, p_kind, p_reserved_cost_usd, p_prompt_version, 'RESERVED') returning id into reservation_id;
    return reservation_id;
end;
$$;
revoke all on function public.pace_reserve_ai_request(uuid, text, integer, integer, integer, text, numeric, numeric) from public, anon, authenticated;
grant execute on function public.pace_reserve_ai_request(uuid, text, integer, integer, integer, text, numeric, numeric) to service_role;

create or replace function public.pace_finish_ai_request(
    p_reservation_id uuid,
    p_input_tokens integer,
    p_output_tokens integer,
    p_actual_cost_usd numeric,
    p_succeeded boolean
)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.pace_ai_usage
       set input_tokens = greatest(p_input_tokens, 0),
           output_tokens = greatest(p_output_tokens, 0),
           estimated_cost_usd = greatest(p_actual_cost_usd, 0),
           status = case when p_succeeded then 'SUCCEEDED' else 'FAILED' end
     where id = p_reservation_id and status = 'RESERVED';
$$;
revoke all on function public.pace_finish_ai_request(uuid, integer, integer, numeric, boolean) from public, anon, authenticated;
grant execute on function public.pace_finish_ai_request(uuid, integer, integer, numeric, boolean) to service_role;
