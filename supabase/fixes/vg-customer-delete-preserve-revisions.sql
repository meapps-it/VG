-- Retain order amendment history when a catalogue account is deleted.
alter table public.vg_shop_order_revisions alter column customer_id drop not null;
alter table public.vg_shop_order_revisions drop constraint vg_shop_order_revisions_customer_id_fkey;
alter table public.vg_shop_order_revisions add constraint vg_shop_order_revisions_customer_id_fkey foreign key (customer_id) references public.vg_shop_customers(id) on delete set null;
