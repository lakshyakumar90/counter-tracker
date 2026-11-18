-- Public account metadata only. Fitness records and GPS tracks remain local until P17.
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    display_name text check (display_name is null or char_length(display_name) <= 60),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
revoke all on table public.profiles from anon;
grant select, update on table public.profiles to authenticated;

drop policy if exists "Pace users read own profile" on public.profiles;
create policy "Pace users read own profile"
    on public.profiles for select to authenticated
    using ((select auth.uid()) = id);

drop policy if exists "Pace users update own profile" on public.profiles;
create policy "Pace users update own profile"
    on public.profiles for update to authenticated
    using ((select auth.uid()) = id)
    with check ((select auth.uid()) = id);

create or replace function public.create_pace_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, display_name)
    values (
        new.id,
        nullif(left(coalesce(new.raw_user_meta_data ->> 'display_name', ''), 60), '')
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

revoke all on function public.create_pace_profile() from public, anon, authenticated;

drop trigger if exists on_pace_auth_user_created on auth.users;
create trigger on_pace_auth_user_created
    after insert on auth.users
    for each row execute procedure public.create_pace_profile();
