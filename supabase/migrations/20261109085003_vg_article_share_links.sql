create table public.vg_article_share_links (
  owner_id uuid not null references auth.users(id) on delete cascade,
  slug text not null check (slug ~ '^[a-z0-9]{1,48}(-[a-z0-9]{1,32})?$'),
  -- Keep the reservation after deletion so an old URL cannot point to another article.
  product_id uuid not null unique,
  primary key (owner_id, slug)
);

alter table public.vg_article_share_links enable row level security;
revoke all on public.vg_article_share_links from public, anon, authenticated;
grant select, insert on public.vg_article_share_links to authenticated;
grant select on public.vg_article_share_links to service_role;

create policy vg_article_share_links_read on public.vg_article_share_links
  for select to authenticated using (owner_id = (select auth.uid()));

create policy vg_article_share_links_insert on public.vg_article_share_links
  for insert to authenticated with check (
    owner_id = (select auth.uid())
    and exists (
      select 1 from public.prodotti p
      where p.id = product_id and p.user_id = owner_id and p.attivo
        and p.disponibile and p.stato_pubblicazione = 'pubblicato' and not p.is_demo
    )
  );

create function public.vg_claim_article_share_link(p_product_id uuid, p_base text)
returns text
language plpgsql
security invoker
set search_path = ''
as $$
declare
  owner uuid := auth.uid();
  product_key text := replace(p_product_id::text, '-', '');
  base text := p_base;
  candidate text;
  saved text;
begin
  if owner is null or p_base is null or p_base !~ '^[a-z0-9]{1,48}$' then
    raise invalid_parameter_value using message = 'Indirizzo articolo non valido.';
  end if;

  perform 1 from public.prodotti
  where id = p_product_id and user_id = owner and attivo and disponibile
    and stato_pubblicazione = 'pubblicato' and not is_demo
  for update;
  if not found then return null; end if;

  select slug into saved from public.vg_article_share_links
  where product_id = p_product_id and owner_id = owner;
  if found then return saved; end if;

  if base in ('index', 'reset', 'catalogo', '404', 'shop', 'api') then
    base := 'articolo' || base;
  end if;

  foreach candidate in array array[base, base || '-' || left(product_key, 6), base || '-' || product_key] loop
    insert into public.vg_article_share_links(owner_id, slug, product_id)
    values (owner, candidate, p_product_id)
    on conflict do nothing
    returning slug into saved;
    if found then return saved; end if;
  end loop;

  raise exception 'Impossibile assegnare un indirizzo all’articolo.';
end;
$$;

revoke execute on function public.vg_claim_article_share_link(uuid,text) from public, anon;
grant execute on function public.vg_claim_article_share_link(uuid,text) to authenticated;
