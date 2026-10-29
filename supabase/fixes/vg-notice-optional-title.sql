alter table public.vg_shop_notice drop constraint vg_shop_notice_title_check;
alter table public.vg_shop_notice add constraint vg_shop_notice_title_check check (char_length(title)<=100);
alter table public.vg_shop_notice alter column title set default '';
