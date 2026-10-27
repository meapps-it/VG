begin;
set local role service_role;
do $$
declare own uuid; cust uuid; cli uuid; ord uuid; revision uuid; removed boolean;
begin
 select owner_id into own from public.vg_shop_customers limit 1;
 if own is null then raise exception 'No catalogue owner available for isolated test'; end if;
 insert into public.vg_shop_customers(owner_id,phone,phone_hash,pin_salt,pin_hash,privacy_at)
 values(own,'999qa-delete-rollback',encode(gen_random_bytes(32),'hex'),repeat('a',32),repeat('b',64),now()) returning id into cust;
 insert into public.clienti(user_id,nome,telefono) values(own,'QA deletion rollback only','999qa') returning id into cli;
 insert into public.ordini(user_id,cliente_id,numero_ordine,stato,totale,pagato,stato_pagamento,note)
 values(own,cli,'QA-DELETE-ROLLBACK','in_lavorazione',100,false,'da_pagare','Isolated rollback test') returning id into ord;
 insert into public.vg_shop_customer_orders(owner_id,customer_id,order_id,items) values(own,cust,ord,'[]');
 insert into public.vg_shop_order_revisions(owner_id,customer_id,order_id,previous_order,previous_lines,previous_customer)
 values(own,cust,ord,'{"qa":true}','[]','{}') returning id into revision;
 if public.vg_delete_catalog_customer(gen_random_uuid(),cust) then raise exception 'Wrong owner deletion accepted'; end if;
 removed:=public.vg_delete_catalog_customer(own,cust);
 if not removed or exists(select 1 from public.vg_shop_customers where id=cust) then raise exception 'Account deletion failed'; end if;
 if not exists(select 1 from public.ordini where id=ord and totale=100) then raise exception 'Order lost'; end if;
 if not exists(select 1 from public.clienti where id=cli) then raise exception 'Delivery customer lost'; end if;
 if not exists(select 1 from public.vg_shop_order_revisions where id=revision and customer_id is null and order_id=ord and previous_order='{"qa":true}'::jsonb) then raise exception 'Revision history lost'; end if;
 if exists(select 1 from public.vg_shop_customer_orders where customer_id=cust) then raise exception 'Account order mapping left behind'; end if;
 if public.vg_delete_catalog_customer(own,cust) then raise exception 'Nonexistent account deletion reported success'; end if;
end $$;
rollback;
select 'PASS: isolated account deletion, owner guard, orders and revision history preserved; all test rows rolled back' as result;
