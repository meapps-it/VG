-- Repair compatibility with PostgREST on_conflict=(user_id,sku) and
-- on_conflict=(user_id,numero_ordine). A partial index with IS NOT NULL
-- is not inferred for these predicate-free ON CONFLICT targets.
-- Full UNIQUE indexes still permit multiple NULL values; no row changes.
begin;
create unique index if not exists vg_prodotti_user_sku_upsert on public.prodotti(user_id,sku);
create unique index if not exists vg_ordini_user_numero_upsert on public.ordini(user_id,numero_ordine);
drop index if exists public.ux_prodotti_user_sku;
drop index if exists public.ux_ordini_user_numero;
notify pgrst, 'reload schema';
commit;
