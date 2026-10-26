-- Customer identity is separate from the shared catalog gate and administrator auth.
create table public.vg_shop_customers (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null references auth.users(id),
 phone text not null,
 phone_hash text not null check(length(phone_hash)=64),
 pin_salt text not null check(length(pin_salt)=32),
 pin_hash text not null check(length(pin_hash)=64),
 profile jsonb not null default '{}'::jsonb check(jsonb_typeof(profile)='object'),
 privacy_at timestamptz not null,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(owner_id,phone_hash),unique(id,owner_id)
);
create table public.vg_shop_customer_sessions (
 token_hash text primary key check(length(token_hash)=64),
 owner_id uuid not null references auth.users(id),
 customer_id uuid not null,
 created_at timestamptz not null default now(),expires_at timestamptz not null,
 foreign key(customer_id,owner_id) references public.vg_shop_customers(id,owner_id) on delete cascade
);
create index vg_shop_customer_sessions_account on public.vg_shop_customer_sessions(customer_id,owner_id);
create index vg_shop_customer_sessions_owner on public.vg_shop_customer_sessions(owner_id);
create table public.vg_shop_customer_orders (
 order_id uuid primary key references public.ordini(id) on delete cascade,
 owner_id uuid not null references auth.users(id),customer_id uuid not null,
 items jsonb not null check(jsonb_typeof(items)='array'),created_at timestamptz not null default now(),
 foreign key(customer_id,owner_id) references public.vg_shop_customers(id,owner_id)
);
create index vg_shop_customer_orders_account_date on public.vg_shop_customer_orders(customer_id,owner_id,created_at desc,order_id);
create index vg_shop_customer_orders_owner on public.vg_shop_customer_orders(owner_id);
create table public.vg_shop_customer_attempts (
 id bigint generated always as identity primary key,ip_hash text not null,phone_hash text not null,attempted_at timestamptz not null default now()
);
create index vg_shop_customer_attempts_ip on public.vg_shop_customer_attempts(ip_hash,attempted_at);
create index vg_shop_customer_attempts_phone on public.vg_shop_customer_attempts(phone_hash,attempted_at);
alter table public.vg_shop_customers enable row level security;
alter table public.vg_shop_customer_sessions enable row level security;
alter table public.vg_shop_customer_orders enable row level security;
alter table public.vg_shop_customer_attempts enable row level security;
revoke all on public.vg_shop_customers,public.vg_shop_customer_sessions,public.vg_shop_customer_orders,public.vg_shop_customer_attempts from anon,authenticated;
grant all on public.vg_shop_customers,public.vg_shop_customer_sessions,public.vg_shop_customer_orders,public.vg_shop_customer_attempts to service_role;
grant usage,select on sequence public.vg_shop_customer_attempts_id_seq to service_role;
grant select on public.vg_shop_customers,public.vg_shop_customer_sessions,public.vg_shop_customer_orders to authenticated;
create policy vg_shop_customer_owner_read on public.vg_shop_customers for select to authenticated using(owner_id=(select auth.uid()));
create policy vg_shop_customer_session_owner_read on public.vg_shop_customer_sessions for select to authenticated using(owner_id=(select auth.uid()));
create policy vg_shop_customer_order_owner_read on public.vg_shop_customer_orders for select to authenticated using(owner_id=(select auth.uid()));
alter table public.righe_ordine add column if not exists variante_misura text;
alter table public.righe_ordine add column if not exists variante_colore text;
alter table public.righe_ordine add column if not exists numero_colore integer;
notify pgrst,'reload schema';
