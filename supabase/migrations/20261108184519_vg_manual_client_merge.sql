create schema if not exists vg_internal;
grant usage on schema vg_internal to authenticated;

create table public.vg_client_merges (
  user_id uuid not null references auth.users(id) on delete cascade,
  removed_id uuid not null,
  kept_id uuid not null,
  archived_client jsonb not null,
  merged_at timestamptz not null default now(),
  primary key (user_id, removed_id),
  check (removed_id <> kept_id)
);
alter table public.vg_client_merges enable row level security;
revoke all on public.vg_client_merges from public, anon, authenticated;
grant select (user_id, removed_id, kept_id) on public.vg_client_merges to authenticated;
create policy vg_client_merges_owner_read on public.vg_client_merges
  for select to authenticated using ((select auth.uid()) = user_id);

create function vg_internal.client_full_name_key(first_name text, last_name text)
returns text language sql immutable set search_path = '' as $$
  with parts as (
    select lower(btrim(regexp_replace(coalesce(first_name,''),'[[:space:]]+',' ','g'))) as first,
           lower(btrim(regexp_replace(coalesce(last_name,''),'[[:space:]]+',' ','g'))) as last
  )
  select case when last='' or first=last or right(first,length(last)+1)=' '||last then first
              else btrim(first||' '||last) end from parts;
$$;

create function vg_internal.merge_client_records(p_keep uuid,p_remove uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  owner_id_value uuid := auth.uid();
  target public.clienti%rowtype;
  source public.clienti%rowtype;
  name_key text;
  target_note text;
  source_note text;
  target_meta text;
  source_meta text;
  alternate_address text;
  use_source_address boolean;
begin
  if owner_id_value is null or p_keep is null or p_remove is null or p_keep=p_remove then
    raise invalid_parameter_value using message='Seleziona due schede cliente diverse';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(owner_id_value::text||':client-merge',0));
  perform 1 from public.clienti where user_id=owner_id_value and id in (p_keep,p_remove) order by id for update;
  select * into target from public.clienti where user_id=owner_id_value and id=p_keep;
  if not found then raise exception 'La scheda da mantenere non è più disponibile. Aggiorna i dati.'; end if;
  if exists (select 1 from public.vg_client_merges where user_id=owner_id_value and removed_id=p_remove and kept_id=p_keep) then
    return to_jsonb(target);
  end if;
  select * into source from public.clienti where user_id=owner_id_value and id=p_remove;
  if not found or source.is_demo is distinct from target.is_demo then
    raise exception 'Le due schede non sono disponibili nello stesso archivio.';
  end if;
  name_key := vg_internal.client_full_name_key(target.nome,target.cognome);
  if position(' ' in name_key)=0 or name_key<>vg_internal.client_full_name_key(source.nome,source.cognome) then
    raise exception 'Le schede non hanno più lo stesso nome e cognome. Aggiorna i dati.';
  end if;
  perform 1 from public.vg_shop_customers where owner_id=owner_id_value and cliente_id in (p_keep,p_remove) order by id for update;
  if exists (select 1 from public.vg_shop_customers where owner_id=owner_id_value and cliente_id=p_keep)
    and exists (select 1 from public.vg_shop_customers where owner_id=owner_id_value and cliente_id=p_remove) then
    raise exception 'Le due schede sono collegate ad account catalogo distinti. L’unione è bloccata.';
  end if;

  target_note := nullif(btrim(regexp_replace(coalesce(target.note,''),'\[VGCLIMETA\].*?\[/VGCLIMETA\]','','gis')),'');
  source_note := nullif(btrim(regexp_replace(coalesce(source.note,''),'\[VGCLIMETA\].*?\[/VGCLIMETA\]','','gis')),'');
  target_meta := substring(target.note from '(?is)(\[VGCLIMETA\].*?\[/VGCLIMETA\])');
  source_meta := substring(source.note from '(?is)(\[VGCLIMETA\].*?\[/VGCLIMETA\])');
  use_source_address := nullif(btrim(target.indirizzo),'') is null and nullif(btrim(source.indirizzo),'') is not null;
  if nullif(btrim(target.indirizzo),'') is not null and nullif(btrim(source.indirizzo),'') is not null
    and row(target.indirizzo,target.cap,target.citta,target.provincia,target.paese)
      is distinct from row(source.indirizzo,source.cap,source.citta,source.provincia,source.paese) then
    alternate_address := 'Altro indirizzo: '||concat_ws(', ',source.indirizzo,source.cap,source.citta,source.provincia,source.paese);
  end if;

  update public.clienti set
    telefono=coalesce(nullif(btrim(target.telefono),''),source.telefono),
    email=coalesce(nullif(btrim(target.email),''),source.email),
    indirizzo=coalesce(nullif(btrim(target.indirizzo),''),source.indirizzo),
    cap=case when use_source_address then coalesce(nullif(btrim(source.cap),''),target.cap) when alternate_address is not null then target.cap else coalesce(nullif(btrim(target.cap),''),source.cap) end,
    citta=case when use_source_address then coalesce(nullif(btrim(source.citta),''),target.citta) when alternate_address is not null then target.citta else coalesce(nullif(btrim(target.citta),''),source.citta) end,
    provincia=case when use_source_address then coalesce(nullif(btrim(source.provincia),''),target.provincia) when alternate_address is not null then target.provincia else coalesce(nullif(btrim(target.provincia),''),source.provincia) end,
    paese=case when use_source_address then coalesce(source.paese,target.paese) else coalesce(target.paese,source.paese) end,
    note=nullif(concat_ws(E'\n\n',target_note,case when source_note is distinct from target_note then source_note end,
      case when nullif(btrim(target.telefono),'') is not null and source.telefono is distinct from target.telefono then 'Altro telefono: '||nullif(btrim(source.telefono),'') end,
      case when nullif(btrim(target.email),'') is not null and source.email is distinct from target.email then 'Altra email: '||nullif(btrim(source.email),'') end,
      alternate_address,coalesce(target_meta,source_meta)),''),
    updated_at=now()
  where id=p_keep and user_id=owner_id_value returning * into target;

  update public.ordini set cliente_id=p_keep where cliente_id=p_remove and user_id=owner_id_value;
  update public.spedizioni set cliente_id=p_keep where cliente_id=p_remove and user_id=owner_id_value;
  update public.vg_shop_customers set cliente_id=p_keep,updated_at=now() where cliente_id=p_remove and owner_id=owner_id_value;
  update public.vg_client_merges set kept_id=p_keep where kept_id=p_remove and user_id=owner_id_value;
  insert into public.vg_client_merges(user_id,removed_id,kept_id,archived_client)
  values(owner_id_value,p_remove,p_keep,to_jsonb(source));
  delete from public.clienti where id=p_remove and user_id=owner_id_value;
  return to_jsonb(target);
end;
$$;

revoke execute on function vg_internal.merge_client_records(uuid,uuid) from public, anon;
grant execute on function vg_internal.merge_client_records(uuid,uuid) to authenticated;

create function public.vg_merge_duplicate_clients(p_keep uuid,p_remove uuid)
returns jsonb language sql security invoker set search_path = '' as $$
  select vg_internal.merge_client_records(p_keep,p_remove);
$$;
revoke execute on function public.vg_merge_duplicate_clients(uuid,uuid) from public, anon;
grant execute on function public.vg_merge_duplicate_clients(uuid,uuid) to authenticated;

create function vg_internal.prevent_merged_client_reinsert()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if exists(select 1 from public.vg_client_merges where user_id=new.user_id and removed_id=new.id) then
    raise exception 'Questa scheda è già stata unificata. Aggiorna i dati dal cloud.';
  end if;
  return new;
end;
$$;
revoke execute on function vg_internal.prevent_merged_client_reinsert() from public, anon, authenticated;
create trigger vg_prevent_merged_client_reinsert before insert on public.clienti
for each row execute function vg_internal.prevent_merged_client_reinsert();
