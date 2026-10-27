begin;
create table if not exists public.vg_shop_password_resets (
 customer_id uuid primary key,owner_id uuid not null references auth.users(id),code_hash text not null check(length(code_hash)=64),
 expires_at timestamptz not null,used_at timestamptz,created_at timestamptz not null default now(),
 foreign key(customer_id,owner_id) references public.vg_shop_customers(id,owner_id) on delete cascade
);
alter table public.vg_shop_password_resets enable row level security;
revoke all on public.vg_shop_password_resets from public,anon,authenticated;
grant all on public.vg_shop_password_resets to service_role;
create or replace function public.vg_start_password_reset(p_owner uuid,p_customer uuid,p_code_hash text,p_salt text,p_disabled_hash text) returns boolean language plpgsql security invoker set search_path='' as $$
begin
 perform 1 from public.vg_shop_customers where owner_id=p_owner and id=p_customer and phone<>'' for update;
 if not found then return false;end if;
 insert into public.vg_shop_password_resets(customer_id,owner_id,code_hash,expires_at) values(p_customer,p_owner,p_code_hash,now()+interval '1 hour')
 on conflict(customer_id) do update set code_hash=excluded.code_hash,expires_at=excluded.expires_at,used_at=null,created_at=now();
 update public.vg_shop_customers set pin_salt=p_salt,pin_hash=p_disabled_hash,updated_at=now() where owner_id=p_owner and id=p_customer;
 update public.vg_shop_customer_sessions set expires_at=now() where owner_id=p_owner and customer_id=p_customer and expires_at>now();
 update public.vg_shop_sessions set expires_at=now() where owner_id=p_owner and customer_id=p_customer and expires_at>now();
 delete from public.vg_shop_customer_attempts where phone_hash in(select phone_hash from public.vg_shop_customers where owner_id=p_owner and id=p_customer);
 return true;
end $$;
create or replace function public.vg_complete_password_reset(p_owner uuid,p_phone_hash text,p_code_hash text,p_salt text,p_password_hash text) returns boolean language plpgsql security invoker set search_path='' as $$
declare cid uuid;begin
 select id into cid from public.vg_shop_customers where owner_id=p_owner and phone_hash=p_phone_hash for update;
 if cid is null then return false;end if;
 perform 1 from public.vg_shop_password_resets where owner_id=p_owner and customer_id=cid and code_hash=p_code_hash and expires_at>now() and used_at is null for update;
 if not found then return false;end if;
 update public.vg_shop_password_resets set used_at=now() where customer_id=cid and owner_id=p_owner;
 update public.vg_shop_customers set pin_salt=p_salt,pin_hash=p_password_hash,updated_at=now() where id=cid and owner_id=p_owner;
 update public.vg_shop_customer_sessions set expires_at=now() where owner_id=p_owner and customer_id=cid and expires_at>now();
 update public.vg_shop_sessions set expires_at=now() where owner_id=p_owner and customer_id=cid and expires_at>now();
 return true;
end $$;
revoke all on function public.vg_start_password_reset(uuid,uuid,text,text,text),public.vg_complete_password_reset(uuid,text,text,text,text) from public,anon,authenticated;
grant execute on function public.vg_start_password_reset(uuid,uuid,text,text,text),public.vg_complete_password_reset(uuid,text,text,text,text) to service_role;
notify pgrst,'reload schema';
commit;
