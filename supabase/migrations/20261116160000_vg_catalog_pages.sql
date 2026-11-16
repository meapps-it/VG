create table public.vg_catalog_pages (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('information', 'special', 'upcoming')),
  title text not null check (char_length(btrim(title)) between 1 and 100),
  body text not null check (char_length(btrim(body)) between 1 and 5000),
  published boolean not null default false,
  updated_at timestamptz not null default now()
);

alter table public.vg_catalog_pages enable row level security;
revoke all on public.vg_catalog_pages from anon, authenticated;
grant select, insert, update, delete on public.vg_catalog_pages to authenticated;
grant select on public.vg_catalog_pages to service_role;

create policy "Catalog owner manages pages"
on public.vg_catalog_pages for all to authenticated
using (owner_id = (select auth.uid()) and owner_id = 'a51e2715-3b5e-4ad3-906e-c664fcdd486d')
with check (owner_id = (select auth.uid()) and owner_id = 'a51e2715-3b5e-4ad3-906e-c664fcdd486d');
