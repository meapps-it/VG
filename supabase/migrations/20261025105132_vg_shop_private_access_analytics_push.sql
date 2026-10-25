create table if not exists public.vg_shop_password (
 owner_id uuid primary key references auth.users(id),
 salt text not null, password_hash text not null, iterations integer not null default 200000,
 updated_at timestamptz not null default now()
);
create table if not exists public.vg_shop_sessions (
 token_hash text primary key, owner_id uuid not null references auth.users(id),
 is_admin boolean not null default false, created_at timestamptz not null default now(), expires_at timestamptz not null
);
create table if not exists public.vg_shop_login_attempts (
 id bigint generated always as identity primary key, ip_hash text not null, attempted_at timestamptz not null default now()
);
create index if not exists vg_shop_login_recent on public.vg_shop_login_attempts(ip_hash,attempted_at);
create table if not exists public.vg_shop_visits (
 id bigint generated always as identity primary key,
 session_hash text not null references public.vg_shop_sessions(token_hash), owner_id uuid not null references auth.users(id),
 window_start timestamptz not null, unique(session_hash,window_start)
);
create index if not exists vg_shop_visits_recent on public.vg_shop_visits(owner_id,window_start);
create table if not exists public.vg_shop_push_tokens (
 token text primary key, owner_id uuid not null references auth.users(id), updated_at timestamptz not null default now()
);
create table if not exists public.vg_shop_notification_outbox (
 order_id uuid primary key references public.ordini(id), owner_id uuid not null references auth.users(id),
 created_at timestamptz not null default now(), sent_at timestamptz, last_error text
);
alter table public.vg_shop_password enable row level security;
alter table public.vg_shop_sessions enable row level security;
alter table public.vg_shop_login_attempts enable row level security;
alter table public.vg_shop_visits enable row level security;
alter table public.vg_shop_push_tokens enable row level security;
alter table public.vg_shop_notification_outbox enable row level security;
revoke all on public.vg_shop_password,public.vg_shop_sessions,public.vg_shop_login_attempts,public.vg_shop_visits,public.vg_shop_push_tokens,public.vg_shop_notification_outbox from anon,authenticated;
grant all on public.vg_shop_password,public.vg_shop_sessions,public.vg_shop_login_attempts,public.vg_shop_visits,public.vg_shop_push_tokens,public.vg_shop_notification_outbox to service_role;
grant usage,select on sequence public.vg_shop_login_attempts_id_seq,public.vg_shop_visits_id_seq to service_role;
