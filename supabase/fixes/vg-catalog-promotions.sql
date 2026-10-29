create table public.vg_shop_promotion (
 owner_id uuid primary key references auth.users(id) on delete cascade,
 enabled boolean not null default false,
 title text not null default 'Promozioni' check (char_length(title) between 1 and 100),
 body text not null default '' check (char_length(body)<=1200),
 image_url text not null default '' check (char_length(image_url)<=2000 and (image_url='' or image_url like 'https://%')),
 product_ids uuid[] not null default '{}' check (cardinality(product_ids)<=12),
 updated_at timestamptz not null default now(),
 constraint promotion_store_owner check (owner_id='a51e2715-3b5e-4ad3-906e-c664fcdd486d'::uuid)
);
alter table public.vg_shop_promotion enable row level security;
revoke all on public.vg_shop_promotion from anon, authenticated;
grant select,insert,update on public.vg_shop_promotion to authenticated;
create policy promotion_owner_read on public.vg_shop_promotion for select to authenticated using ((select auth.uid())=owner_id);
create policy promotion_owner_insert on public.vg_shop_promotion for insert to authenticated with check ((select auth.uid())=owner_id);
create policy promotion_owner_update on public.vg_shop_promotion for update to authenticated using ((select auth.uid())=owner_id) with check ((select auth.uid())=owner_id);
