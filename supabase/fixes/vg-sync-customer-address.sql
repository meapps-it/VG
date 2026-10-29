create or replace function public.vg_sync_customer_address(p_owner uuid,p_customer uuid,p_profile jsonb) returns boolean language plpgsql security invoker set search_path=public as $$
begin
 if not exists(select 1 from public.vg_shop_customers where id=p_customer and owner_id=p_owner) then return false; end if;
 if coalesce(p_profile->>'name','')='' or coalesce(p_profile->>'address','')='' or coalesce(p_profile->>'city','')='' or coalesce(p_profile->>'province','')='' or coalesce(p_profile->>'cap','')='' then raise exception 'Indirizzo incompleto'; end if;
 update public.vg_shop_customers set profile=coalesce(profile,'{}'::jsonb)||p_profile,updated_at=now() where id=p_customer and owner_id=p_owner;
 insert into public.vg_shop_customer_details as d(customer_id,owner_id,addresses) values(p_customer,p_owner,jsonb_build_array(p_profile)) on conflict(customer_id) do update set addresses=(select coalesce(jsonb_agg(a),'[]'::jsonb) from(select a from (select p_profile as a,0 as position union all select a,ordinality::int from jsonb_array_elements(d.addresses) with ordinality as old(a,ordinality) where lower(trim(a->>'address')) is distinct from lower(trim(p_profile->>'address')) or lower(trim(a->>'city')) is distinct from lower(trim(p_profile->>'city')) or a->>'cap' is distinct from p_profile->>'cap') entries order by position limit 5) selected),updated_at=now() where d.owner_id=p_owner;
 return true;
end $$;
revoke all on function public.vg_sync_customer_address(uuid,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.vg_sync_customer_address(uuid,uuid,jsonb) to service_role;
