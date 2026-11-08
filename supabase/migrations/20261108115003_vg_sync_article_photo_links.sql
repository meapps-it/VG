create or replace function public.vg_sync_article_photo_links(
  p_product_id uuid,
  p_paths text[],
  p_previous_paths text[]
) returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_owner uuid := auth.uid();
  v_current text[];
begin
  if v_owner is null then
    raise insufficient_privilege using message = 'Accesso richiesto';
  end if;
  if p_paths is null or p_previous_paths is null or cardinality(p_paths) > 20
    or exists (select 1 from unnest(p_paths) as photo(path) where path is null or btrim(path) = '')
    or cardinality(p_paths) <> (select count(distinct path) from unnest(p_paths) as photo(path)) then
    raise invalid_parameter_value using message = 'Elenco foto non valido';
  end if;

  perform 1 from public.prodotti where id = p_product_id and user_id = v_owner for update;
  if not found then
    raise insufficient_privilege using message = 'Articolo non disponibile';
  end if;

  select coalesce(array_agg(path order by ordine nulls last, id), '{}'::text[])
  into v_current
  from public.prodotti_foto where prodotto_id = p_product_id and user_id = v_owner;

  if v_current = p_paths then return true; end if;
  if v_current <> p_previous_paths then
    raise sqlstate 'P0001' using message = 'Le foto sono cambiate su un altro dispositivo. Riapri l’articolo e riprova.';
  end if;
  if exists (
    select 1 from unnest(p_paths) as photo(path)
    where not (photo.path = any(v_current)) and not exists (
      select 1 from storage.objects o
      where o.bucket_id = 'articoli' and o.name = photo.path and o.owner_id = v_owner::text
    )
  ) then
    raise invalid_parameter_value using message = 'Una foto caricata non è disponibile';
  end if;

  delete from public.prodotti_foto where prodotto_id = p_product_id and user_id = v_owner;
  insert into public.prodotti_foto(user_id, prodotto_id, path, ordine)
  select v_owner, p_product_id, photo.path, (photo.position - 1)::integer
  from unnest(p_paths) with ordinality as photo(path, position);
  return true;
end;
$$;

revoke execute on function public.vg_sync_article_photo_links(uuid, text[], text[]) from public, anon;
grant execute on function public.vg_sync_article_photo_links(uuid, text[], text[]) to authenticated;
