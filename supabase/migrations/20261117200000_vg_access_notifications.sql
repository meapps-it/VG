create table public.vg_shop_access_notifications (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null references auth.users(id),
 customer_id uuid not null references public.vg_shop_customers(id) on delete cascade,
 created_at timestamptz not null default now(), sent_at timestamptz, last_error text
);
alter table public.vg_shop_access_notifications enable row level security;
revoke all on public.vg_shop_access_notifications from public,anon,authenticated;
grant all on public.vg_shop_access_notifications to service_role;
create index vg_shop_access_notifications_recent on public.vg_shop_access_notifications(owner_id,created_at desc);
create or replace function public.vg_record_presence_access(p_owner uuid,p_customer uuid)
returns uuid language plpgsql security invoker set search_path='' as $$
declare previous_at timestamptz; notice uuid;
begin
 perform 1 from public.vg_shop_customers where id=p_customer and owner_id=p_owner and access_status='approved'
 and id<>'d93e6255-a666-42e0-9c6b-4b59a6ff38b5' and (auth_user_id is null or auth_user_id<>p_owner) for update;
 if not found then return null; end if;
 select last_seen_at into previous_at from public.vg_shop_customer_presence where customer_id=p_customer and owner_id=p_owner;
 insert into public.vg_shop_customer_presence(customer_id,owner_id,last_seen_at) values(p_customer,p_owner,now())
 on conflict(customer_id) do update set last_seen_at=excluded.last_seen_at;
 if previous_at is null or previous_at<now()-interval '5 minutes' then
  insert into public.vg_shop_access_notifications(owner_id,customer_id) values(p_owner,p_customer) returning id into notice;
 end if;
 return notice;
end $$;
revoke all on function public.vg_record_presence_access(uuid,uuid) from public,anon,authenticated;
grant execute on function public.vg_record_presence_access(uuid,uuid) to service_role;
notify pgrst,'reload schema';