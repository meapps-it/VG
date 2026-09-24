-- Riepilogo idempotente della migrazione già applicata al progetto remoto.
-- Conserva campi e dati legacy; gli account nuovi partono senza preset.
begin;

create table if not exists public.marche (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  nome text not null check (length(btrim(nome)) > 0),
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(id,user_id)
);

create table if not exists public.fornitori (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  nome text not null check (length(btrim(nome)) > 0),
  referente text, telefono text, email text, sito_web text, link_catalogo text,
  indirizzo text, note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(id,user_id)
);

create unique index if not exists ux_marche_user_nome_ci on public.marche(user_id,lower(btrim(nome)));
create unique index if not exists ux_fornitori_user_nome_ci on public.fornitori(user_id,lower(btrim(nome)));

alter table public.categorie add column if not exists sort_order integer not null default 0;
alter table public.categorie add column if not exists updated_at timestamptz not null default now();

alter table public.prodotti
  add column if not exists codice text,
  add column if not exists marca_id uuid,
  add column if not exists fornitore_id uuid,
  add column if not exists descrizione_app text,
  add column if not exists costi_aggiuntivi numeric(12,2) not null default 0,
  add column if not exists link_prodotto text,
  add column if not exists note text,
  add column if not exists disponibile boolean not null default true;

alter table public.marche enable row level security;
alter table public.fornitori enable row level security;

drop policy if exists marche_select_own on public.marche;
drop policy if exists marche_insert_own on public.marche;
drop policy if exists marche_update_own on public.marche;
drop policy if exists marche_delete_own on public.marche;
create policy marche_select_own on public.marche for select to authenticated using ((select auth.uid())=user_id);
create policy marche_insert_own on public.marche for insert to authenticated with check ((select auth.uid())=user_id);
create policy marche_update_own on public.marche for update to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
create policy marche_delete_own on public.marche for delete to authenticated using ((select auth.uid())=user_id);

drop policy if exists fornitori_select_own on public.fornitori;
drop policy if exists fornitori_insert_own on public.fornitori;
drop policy if exists fornitori_update_own on public.fornitori;
drop policy if exists fornitori_delete_own on public.fornitori;
create policy fornitori_select_own on public.fornitori for select to authenticated using ((select auth.uid())=user_id);
create policy fornitori_insert_own on public.fornitori for insert to authenticated with check ((select auth.uid())=user_id);
create policy fornitori_update_own on public.fornitori for update to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
create policy fornitori_delete_own on public.fornitori for delete to authenticated using ((select auth.uid())=user_id);

update storage.buckets set public=false where id='articoli';
commit;
