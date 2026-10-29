create table public.vg_shop_notice (
 owner_id uuid primary key references auth.users(id) on delete cascade,
 enabled boolean not null default false,
 kind text not null default 'communication' check (kind in ('communication','information','warning')),
 title text not null default 'Comunicazioni' check (char_length(btrim(title)) between 1 and 100),
 body text not null default '' check (char_length(body)<=3000),
 image_url text not null default '' check (char_length(image_url)<=2000 and (image_url='' or image_url like 'https://%')),
 image_path text not null default '' check (image_path='' or image_path ~ '^a51e2715-3b5e-4ad3-906e-c664fcdd486d/catalog-notices/[0-9a-f-]{36}\.webp$'),
 updated_at timestamptz not null default now(),
 constraint notice_store_owner check (owner_id='a51e2715-3b5e-4ad3-906e-c664fcdd486d'::uuid),
 constraint notice_single_image check (image_path='' or image_url='')
);
alter table public.vg_shop_notice enable row level security;
revoke all on public.vg_shop_notice from anon, authenticated;
grant select,insert,update on public.vg_shop_notice to authenticated;
create policy notice_owner_read on public.vg_shop_notice for select to authenticated using ((select auth.uid())=owner_id);
create policy notice_owner_insert on public.vg_shop_notice for insert to authenticated with check ((select auth.uid())=owner_id);
create policy notice_owner_update on public.vg_shop_notice for update to authenticated using ((select auth.uid())=owner_id) with check ((select auth.uid())=owner_id);
