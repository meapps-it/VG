alter table public.vg_shop_promotion add column if not exists excluded_product_ids uuid[] not null default '{}';
