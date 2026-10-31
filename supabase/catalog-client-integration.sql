
create schema if not exists vg_private;
revoke all on schema vg_private from public,anon,authenticated;
alter table public.vg_shop_customers add column if not exists cliente_id uuid references public.clienti(id) on delete set null;
create index if not exists vg_shop_customers_cliente_idx on public.vg_shop_customers(owner_id,cliente_id);
create or replace function vg_private.phone_key(v text) returns text language sql immutable set search_path='' as $$
 select case when length(d)=14 and left(d,4)='0039' then substr(d,5) when length(d)=12 and left(d,2)='39' then substr(d,3) else d end from (select regexp_replace(coalesce(v,''),'[^0-9]','','g') d) s;
$$;
create or replace function vg_private.link_catalog_customer() returns trigger language plpgsql security definer set search_path='' as $$
declare target uuid; ph text; display_name text; explicit_link boolean:=false;
begin
 ph:=vg_private.phone_key(coalesce(nullif(new.profile->>'phone',''),new.phone));
 if length(ph)<6 or coalesce(new.phone,'') like 'google:%' then ph:=''; end if;
 if tg_op='UPDATE' then explicit_link:=new.cliente_id is distinct from old.cliente_id and new.cliente_id is not null; end if;
 perform pg_advisory_xact_lock(hashtextextended(new.owner_id::text||':'||coalesce(nullif(ph,''),new.id::text),0));
 target:=new.cliente_id;
 if target is not null and not exists(select 1 from public.clienti where id=target and user_id=new.owner_id) then raise exception 'Scheda cliente non valida'; end if;
 if not explicit_link and ph<>'' and (target is null or tg_op='INSERT') then
  select id into target from public.clienti where user_id=new.owner_id and vg_private.phone_key(telefono)=ph order by created_at,id limit 1;
 end if;
 display_name:=coalesce(nullif(trim(new.profile->>'name'),''),nullif(new.email,''),'Cliente catalogo');
 if target is null then
  insert into public.clienti(user_id,nome,telefono,email,indirizzo,citta,provincia,cap,is_demo)
  values(new.owner_id,display_name,nullif(ph,''),nullif(new.email,''),nullif(new.profile->>'address',''),nullif(new.profile->>'city',''),nullif(new.profile->>'province',''),nullif(new.profile->>'cap',''),false) returning id into target;
 else
  update public.clienti set nome=case when nullif(trim(new.profile->>'name'),'') is not null then display_name else nome end,
   cognome=case when nullif(trim(new.profile->>'name'),'') is not null then '' else cognome end,
   telefono=coalesce(nullif(ph,''),telefono),email=coalesce(nullif(new.email,''),email),
   indirizzo=coalesce(nullif(new.profile->>'address',''),indirizzo),citta=coalesce(nullif(new.profile->>'city',''),citta),
   provincia=coalesce(nullif(new.profile->>'province',''),provincia),cap=coalesce(nullif(new.profile->>'cap',''),cap),updated_at=now()
  where id=target and user_id=new.owner_id;
 end if;
 new.cliente_id:=target;
 return new;
end $$;
revoke all on function vg_private.phone_key(text),vg_private.link_catalog_customer() from public,anon,authenticated;
create trigger vg_link_catalog_customer before insert or update of phone,profile,email,cliente_id on public.vg_shop_customers for each row execute function vg_private.link_catalog_customer();

create or replace function public.vg_merge_catalog_client(p_owner uuid,p_customer uuid,p_target uuid) returns jsonb language plpgsql security invoker set search_path='' as $$
declare source_id uuid; result jsonb; old_row public.clienti%rowtype; target_row public.clienti%rowtype;
begin
 perform pg_advisory_xact_lock(hashtextextended(p_owner::text||':client-merge',0));
 select cliente_id into source_id from public.vg_shop_customers where id=p_customer and owner_id=p_owner for update;
 if not found then raise exception 'Account non trovato'; end if;
 select * into target_row from public.clienti where id=p_target and user_id=p_owner for update;
 if not found then raise exception 'Scheda cliente non trovata'; end if;
 if source_id=p_target then return jsonb_build_object('ok',true,'clientId',p_target,'removedClientId',null); end if;
 select * into old_row from public.clienti where id=source_id and user_id=p_owner for update;
 if source_id is not null then
  update public.clienti set
   note=concat_ws(E'\n\n',nullif(target_row.note,''),case when old_row.note is distinct from target_row.note then nullif(old_row.note,'') end),
   telefono=coalesce(nullif(target_row.telefono,''),old_row.telefono),
   email=coalesce(nullif(target_row.email,''),old_row.email),
   indirizzo=coalesce(nullif(target_row.indirizzo,''),old_row.indirizzo),
   citta=coalesce(nullif(target_row.citta,''),old_row.citta),
   provincia=coalesce(nullif(target_row.provincia,''),old_row.provincia),
   cap=coalesce(nullif(target_row.cap,''),old_row.cap),updated_at=now()
  where id=p_target and user_id=p_owner;
  update public.ordini set cliente_id=p_target where cliente_id=source_id and user_id=p_owner;
  update public.spedizioni set cliente_id=p_target where cliente_id=source_id and user_id=p_owner;
  update public.vg_shop_customers set cliente_id=p_target,updated_at=now() where cliente_id=source_id and owner_id=p_owner;
 else
  update public.vg_shop_customers set cliente_id=p_target,updated_at=now() where id=p_customer and owner_id=p_owner;
 end if;
 if source_id is not null then delete from public.clienti where id=source_id and user_id=p_owner; end if;
 return jsonb_build_object('ok',true,'clientId',p_target,'removedClientId',source_id);
end $$;
revoke all on function public.vg_merge_catalog_client(uuid,uuid,uuid) from public,anon,authenticated;
grant execute on function public.vg_merge_catalog_client(uuid,uuid,uuid) to service_role;
update public.vg_shop_customers set profile=profile where cliente_id is null and owner_id='a51e2715-3b5e-4ad3-906e-c664fcdd486d';
