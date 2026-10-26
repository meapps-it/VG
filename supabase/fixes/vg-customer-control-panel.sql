begin;
alter table public.vg_shop_customers add column if not exists last_login_at timestamptz;
alter table public.vg_shop_customers add column if not exists last_access_at timestamptz;
create table if not exists public.vg_shop_customer_notifications (
 id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id),
 customer_id uuid not null, event_type text not null default 'registered' check(event_type='registered'),
 created_at timestamptz not null default now(), read_at timestamptz, sent_at timestamptz, last_error text,
 foreign key(customer_id,owner_id) references public.vg_shop_customers(id,owner_id) on delete cascade,
 unique(customer_id,event_type)
);
create index if not exists vg_customer_notifications_recent on public.vg_shop_customer_notifications(owner_id,created_at desc);
create table if not exists public.vg_shop_customer_access_events (
 id uuid primary key default gen_random_uuid(),owner_id uuid not null references auth.users(id),customer_id uuid not null,
 event_type text not null check(event_type in ('login','visit')),occurred_at timestamptz not null default now(),
 foreign key(customer_id,owner_id) references public.vg_shop_customers(id,owner_id) on delete cascade
);
create index if not exists vg_customer_access_recent on public.vg_shop_customer_access_events(owner_id,customer_id,occurred_at desc);
alter table public.vg_shop_customer_notifications enable row level security;
alter table public.vg_shop_customer_access_events enable row level security;
revoke all on public.vg_shop_customer_notifications,public.vg_shop_customer_access_events from public,anon,authenticated;
grant all on public.vg_shop_customer_notifications,public.vg_shop_customer_access_events to service_role;
create or replace function public.vg_record_customer_activity() returns trigger language plpgsql security invoker set search_path='' as $$
declare cid uuid;begin
 if tg_table_name='vg_shop_customers' then
  insert into public.vg_shop_customer_notifications(owner_id,customer_id) values(new.owner_id,new.id) on conflict do nothing;
 elsif tg_table_name='vg_shop_customer_sessions' then
  update public.vg_shop_customers set last_login_at=new.created_at where id=new.customer_id and owner_id=new.owner_id;
  insert into public.vg_shop_customer_access_events(owner_id,customer_id,event_type,occurred_at) values(new.owner_id,new.customer_id,'login',new.created_at);
 else
  select customer_id into cid from public.vg_shop_sessions where token_hash=new.session_hash and owner_id=new.owner_id and not is_admin;
  if cid is not null then
   update public.vg_shop_customers set last_access_at=now() where id=cid and owner_id=new.owner_id;
   insert into public.vg_shop_customer_access_events(owner_id,customer_id,event_type) values(new.owner_id,cid,'visit');
  end if;
 end if;return new;
end $$;
revoke all on function public.vg_record_customer_activity() from public,anon,authenticated;
drop trigger if exists vg_customer_registration on public.vg_shop_customers;
create trigger vg_customer_registration after insert on public.vg_shop_customers for each row execute function public.vg_record_customer_activity();
drop trigger if exists vg_customer_login on public.vg_shop_customer_sessions;
create trigger vg_customer_login after insert on public.vg_shop_customer_sessions for each row execute function public.vg_record_customer_activity();
drop trigger if exists vg_customer_visit on public.vg_shop_visits;
create trigger vg_customer_visit after insert on public.vg_shop_visits for each row execute function public.vg_record_customer_activity();
-- Recover historical login dates without generating new registration alerts.
update public.vg_shop_customers c set last_login_at=s.latest from (select customer_id,owner_id,max(created_at) latest from public.vg_shop_customer_sessions group by customer_id,owner_id) s where c.id=s.customer_id and c.owner_id=s.owner_id and c.last_login_at is null;
insert into public.vg_shop_customer_access_events(owner_id,customer_id,event_type,occurred_at)
 select s.owner_id,s.customer_id,'login',s.created_at from public.vg_shop_customer_sessions s
 where not exists(select 1 from public.vg_shop_customer_access_events e where e.customer_id=s.customer_id and e.event_type='login' and e.occurred_at=s.created_at);
update public.vg_shop_customers c set last_access_at=v.latest from (select s.customer_id,s.owner_id,max(v.window_start) latest from public.vg_shop_visits v join public.vg_shop_sessions s on s.token_hash=v.session_hash where s.customer_id is not null group by s.customer_id,s.owner_id) v where c.id=v.customer_id and c.owner_id=v.owner_id and c.last_access_at is null;
create or replace function public.vg_delete_catalog_customer(p_owner uuid,p_customer uuid) returns boolean language plpgsql security invoker set search_path='' as $$
begin
 perform 1 from public.vg_shop_customers where owner_id=p_owner and id=p_customer for update;
 if not found then return false;end if;
 delete from public.vg_shop_visits where owner_id=p_owner and session_hash in(select token_hash from public.vg_shop_sessions where owner_id=p_owner and customer_id=p_customer);
 delete from public.vg_shop_sessions where owner_id=p_owner and customer_id=p_customer;
 -- Remove only the account link. Actual orders, order lines and customer delivery records remain.
 delete from public.vg_shop_customer_orders where owner_id=p_owner and customer_id=p_customer;
 delete from public.vg_shop_customer_attempts where phone_hash in(select phone_hash from public.vg_shop_customers where owner_id=p_owner and id=p_customer);
 delete from public.vg_shop_customers where owner_id=p_owner and id=p_customer;
 return true;
end $$;
revoke all on function public.vg_delete_catalog_customer(uuid,uuid) from public,anon,authenticated;
grant execute on function public.vg_delete_catalog_customer(uuid,uuid) to service_role;
notify pgrst,'reload schema';
commit;
