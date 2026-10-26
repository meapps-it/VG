begin;
set local role service_role;
do $$
declare own uuid:='a51e2715-3b5e-4ad3-906e-c664fcdd486d';cust uuid;cli uuid;ord uuid;prod uuid;v timestamptz;newv timestamptz;result jsonb;rejected boolean;
begin
 insert into public.vg_shop_customers(owner_id,phone,phone_hash,pin_salt,pin_hash,privacy_at) values(own,'999qa-rollback',repeat('a',64),repeat('a',32),repeat('b',64),now()) returning id into cust;
 insert into public.clienti(user_id,nome,telefono) values(own,'QA rollback only','999qa') returning id into cli;
 select id into prod from public.prodotti where user_id=own limit 1;
 insert into public.ordini(user_id,cliente_id,numero_ordine,stato,totale,pagato,stato_pagamento,note) values(own,cli,'QA-ROLLBACK-AMEND','in_lavorazione',100,false,'da_pagare','Richiesta QA') returning id,coalesce(updated_at,created_at) into ord,v;
 insert into public.righe_ordine(user_id,ordine_id,prodotto_id,quantita,prezzo_unitario) values(own,ord,prod,1,100);
 insert into public.vg_shop_customer_orders(owner_id,customer_id,order_id,items) values(own,cust,ord,'[]');
 result:=public.vg_shop_amend_order(own,cust,ord,v,'{"name":"QA nuovo","phone":"999qa","address":"Via QA 1","province":"CN","city":"Beinette","cap":"12081"}',jsonb_build_array(jsonb_build_object('id',prod,'qty',2,'price',140,'size','36','color','Nero','colorNumber',1)),280,'Richiesta QA modificata');
 if (result->>'number')<>'QA-ROLLBACK-AMEND' or not (result->>'amended')::boolean then raise exception 'QA number/result failed';end if;
 if (select count(*) from public.vg_shop_order_revisions where order_id=ord)<>1 then raise exception 'QA audit missing';end if;
 if (select totale from public.ordini where id=ord)<>280 or (select quantita from public.righe_ordine where ordine_id=ord)<>2 then raise exception 'QA rows not updated';end if;
 newv:=(result->>'updatedAt')::timestamptz;
 rejected:=false;begin perform public.vg_shop_amend_order(own,gen_random_uuid(),ord,newv,'{}','[]',0,'');exception when sqlstate 'P0001' then rejected:=true;end;if not rejected then raise exception 'QA foreign customer accepted';end if;
 rejected:=false;begin perform public.vg_shop_amend_order(own,cust,ord,v,'{}','[]',0,'');exception when sqlstate 'P0001' then rejected:=true;end;if not rejected then raise exception 'QA stale version accepted';end if;
 update public.ordini set note='[STATO:Richiesto]' where id=ord returning coalesce(updated_at,created_at) into newv;
 rejected:=false;begin perform public.vg_shop_amend_order(own,cust,ord,newv,'{}','[]',0,'');exception when sqlstate 'P0001' then rejected:=true;end;if not rejected then raise exception 'QA admin order accepted';end if;
delete from public.righe_ordine where ordine_id=ord;
 delete from public.ordini where id=ord;
 if not exists(select 1 from public.vg_shop_order_revisions where customer_id=cust and order_id is null) then raise exception 'QA admin deletion failed to retain revision history';end if;
end $$;
rollback;
select 'PASS atomic amendment, identity, revision history, stale version and administrator guard; all QA data rolled back' as result;
