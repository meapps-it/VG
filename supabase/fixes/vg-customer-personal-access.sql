-- Personal catalogue access: new and existing customers require owner approval.
begin;
alter table public.vg_shop_customers add column if not exists auth_user_id uuid references auth.users(id);
alter table public.vg_shop_customers add column if not exists email text;
alter table public.vg_shop_customers add column if not exists access_status text not null default 'pending' check(access_status in ('pending','approved','blocked'));
create unique index if not exists vg_shop_customers_google_identity on public.vg_shop_customers(owner_id,auth_user_id) where auth_user_id is not null;
alter table public.vg_shop_sessions add column if not exists customer_id uuid;
do $$ begin
 if not exists(select 1 from pg_constraint where conname='vg_shop_sessions_customer_owner_fk') then
 alter table public.vg_shop_sessions add constraint vg_shop_sessions_customer_owner_fk foreign key(customer_id,owner_id) references public.vg_shop_customers(id,owner_id);
 end if;
end $$;
-- Immediately revoke shared-password sessions; administrator previews remain valid.
update public.vg_shop_sessions set expires_at=now() where not is_admin and customer_id is null and expires_at>now();
notify pgrst,'reload schema';
commit;
