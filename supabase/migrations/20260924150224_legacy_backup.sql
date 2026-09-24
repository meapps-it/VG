-- Già applicata al progetto qfjwtawsqfwmsmrrgqfi il 24/09/2026.
-- Backup logico privato creato prima della migrazione del nucleo Android.
create schema if not exists legacy_backup_20260924;
revoke all on schema legacy_backup_20260924 from public, anon, authenticated;

create table if not exists legacy_backup_20260924.categorie as table public.categorie;
create table if not exists legacy_backup_20260924.prodotti as table public.prodotti;
create table if not exists legacy_backup_20260924.prodotti_foto as table public.prodotti_foto;
create table if not exists legacy_backup_20260924.clienti as table public.clienti;
create table if not exists legacy_backup_20260924.ordini as table public.ordini;
create table if not exists legacy_backup_20260924.righe_ordine as table public.righe_ordine;
create table if not exists legacy_backup_20260924.spedizioni as table public.spedizioni;
create table if not exists legacy_backup_20260924.storage_objects as
select * from storage.objects where bucket_id in ('articoli','social-videos');

create table if not exists legacy_backup_20260924.backup_manifest as
select now() as created_at, 'before_native_core_phase1'::text as label,
jsonb_build_object(
  'categorie',(select count(*) from public.categorie),
  'prodotti',(select count(*) from public.prodotti),
  'prodotti_foto',(select count(*) from public.prodotti_foto),
  'clienti',(select count(*) from public.clienti),
  'ordini',(select count(*) from public.ordini),
  'righe_ordine',(select count(*) from public.righe_ordine),
  'spedizioni',(select count(*) from public.spedizioni),
  'storage_objects',(select count(*) from storage.objects where bucket_id in ('articoli','social-videos'))
) as counts;
