alter table public.vg_shop_customers
  add column catalog_installed_seen_at timestamptz,
  add column catalog_install_signal text check (catalog_install_signal in ('installed', 'standalone'));
