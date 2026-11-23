-- AI is disabled until an operator deliberately enables it in the control plane.
create table if not exists public.pace_ai_control (
    id boolean primary key default true check (id),
    enabled boolean not null default false,
    updated_at timestamptz not null default now()
);
alter table public.pace_ai_control enable row level security;
revoke all on table public.pace_ai_control from public, anon, authenticated;
grant select on table public.pace_ai_control to service_role;
insert into public.pace_ai_control (id, enabled)
values (true, false)
on conflict (id) do nothing;
