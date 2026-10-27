-- Transactional edits of unpaid, untouched catalogue requests. History is retained.
create table if not exists public.vg_shop_order_revisions (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null references auth.users(id),
 customer_id uuid references public.vg_shop_customers(id) on delete set null,
 order_id uuid references public.ordini(id) on delete set null,
 previous_order jsonb not null,previous_lines jsonb not null,previous_customer jsonb not null,
 revised_at timestamptz not null default now()
);
alter table public.vg_shop_order_revisions enable row level security;
revoke all on public.vg_shop_order_revisions from anon,authenticated;
grant all on public.vg_shop_order_revisions to service_role;
grant select on public.vg_shop_order_revisions to authenticated;
create policy vg_order_revision_owner_read on public.vg_shop_order_revisions for select to authenticated using ((select auth.uid())=owner_id);
create index if not exists vg_order_revision_owner on public.vg_shop_order_revisions(owner_id,order_id,revised_at);
create index if not exists vg_order_revision_customer on public.vg_shop_order_revisions(customer_id);
create or replace function public.vg_shop_amend_order(p_owner uuid,p_customer uuid,p_order uuid,p_expected timestamptz,p_address jsonb,p_lines jsonb,p_total numeric,p_note text)
returns jsonb language plpgsql security invoker set search_path=public as $$
declare old_order public.ordini%rowtype;result public.ordini%rowtype;old_lines jsonb;old_customer jsonb;
begin
 select o.* into old_order from public.ordini o join public.vg_shop_customer_orders m on m.order_id=o.id and m.owner_id=o.user_id
 where o.id=p_order and o.user_id=p_owner and m.customer_id=p_customer for update of o;
 if not found then raise exception 'Ordine non disponibile per questo account' using errcode='P0001';end if;
 if old_order.stato<>'in_lavorazione' or old_order.pagato or coalesce(old_order.totale_pagato,0)>0 or coalesce(old_order.spese,0)>0 or old_order.tracking_code is not null or position('[STATO:' in coalesce(old_order.note,''))>0 then
  raise exception 'Il negozio ha già preso in gestione questo ordine. Contattaci per modificarlo.' using errcode='P0001';end if;
 if p_expected is null or coalesce(old_order.updated_at,old_order.created_at)<>p_expected then raise exception 'L’ordine è cambiato. Riapri I miei ordini prima di modificarlo.' using errcode='P0001';end if;
 if jsonb_typeof(p_lines)<>'array' or jsonb_array_length(p_lines)<1 or jsonb_array_length(p_lines)>30 or p_total<0 or p_total>=1000000 then raise exception 'Richiesta non valida' using errcode='P0001';end if;
 select coalesce(jsonb_agg(to_jsonb(r)),'[]'::jsonb) into old_lines from public.righe_ordine r where r.ordine_id=p_order;
 select to_jsonb(c) into old_customer from public.clienti c where c.id=old_order.cliente_id and c.user_id=p_owner;
 insert into public.vg_shop_order_revisions(owner_id,customer_id,order_id,previous_order,previous_lines,previous_customer) values(p_owner,p_customer,p_order,to_jsonb(old_order),old_lines,coalesce(old_customer,'{}'::jsonb));
 update public.clienti set nome=p_address->>'name',telefono=p_address->>'phone',indirizzo=p_address->>'address',provincia=p_address->>'province',citta=p_address->>'city',cap=p_address->>'cap' where id=old_order.cliente_id and user_id=p_owner;
 delete from public.righe_ordine where ordine_id=p_order and user_id=p_owner;
 insert into public.righe_ordine(user_id,ordine_id,prodotto_id,quantita,prezzo_unitario,sconto,costo_unitario,guadagno_riga,variante_misura,variante_colore,numero_colore)
 select p_owner,p_order,(v->>'id')::uuid,(v->>'qty')::integer,(v->>'price')::numeric,0,0,0,nullif(v->>'size',''),nullif(v->>'color',''),nullif(v->>'colorNumber','')::integer from jsonb_array_elements(p_lines) v;
 update public.ordini set totale=p_total,note=p_note,updated_at=clock_timestamp() where id=p_order and user_id=p_owner returning * into result;
 update public.vg_shop_customer_orders set items=p_lines where order_id=p_order and owner_id=p_owner and customer_id=p_customer;
 insert into public.vg_shop_notification_outbox(order_id,owner_id) values(p_order,p_owner) on conflict(order_id) do update set sent_at=null,last_error=null,created_at=clock_timestamp();
 return jsonb_build_object('ok',true,'id',result.id,'number',result.numero_ordine,'requestedAt',result.created_at,'updatedAt',result.updated_at,'timeZone','Europe/Rome','amended',true);
end $$;
revoke all on function public.vg_shop_amend_order(uuid,uuid,uuid,timestamptz,jsonb,jsonb,numeric,text) from public,anon,authenticated;
grant execute on function public.vg_shop_amend_order(uuid,uuid,uuid,timestamptz,jsonb,jsonb,numeric,text) to service_role;
notify pgrst,'reload schema';
-- Keep the administrator's existing delete workflow usable; preserve revision audit separately.
alter table public.vg_shop_notification_outbox drop constraint vg_shop_notification_outbox_order_id_fkey;
alter table public.vg_shop_notification_outbox add constraint vg_shop_notification_outbox_order_id_fkey foreign key(order_id) references public.ordini(id) on delete cascade;
