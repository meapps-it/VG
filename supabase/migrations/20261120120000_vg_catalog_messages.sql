create table public.vg_catalog_messages (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id),
  customer_id uuid not null references public.vg_shop_customers(id) on delete cascade,
  topic text not null,
  subject text not null,
  sender text not null check (sender in ('customer','shop')),
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index vg_catalog_messages_customer on public.vg_catalog_messages(owner_id,customer_id,created_at desc,id desc);
create index vg_catalog_messages_owner on public.vg_catalog_messages(owner_id,created_at desc,id desc);
create table public.vg_catalog_message_alerts (
  owner_id uuid not null,
  customer_id uuid not null references public.vg_shop_customers(id) on delete cascade,
  email text,
  subscription jsonb,
  primary key(owner_id,customer_id)
);
create unique index vg_catalog_message_alert_endpoint on public.vg_catalog_message_alerts ((subscription->>'endpoint')) where subscription is not null;
alter table public.vg_catalog_messages enable row level security;
alter table public.vg_catalog_message_alerts enable row level security;
revoke all on public.vg_catalog_messages,public.vg_catalog_message_alerts from anon,authenticated;
grant select,insert,update,delete on public.vg_catalog_messages,public.vg_catalog_message_alerts to service_role;
