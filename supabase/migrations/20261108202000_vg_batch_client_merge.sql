create function public.vg_merge_duplicate_client_group(p_keep uuid, p_remove uuid[])
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  source_id uuid;
  merged_client jsonb;
begin
  if p_keep is null or coalesce(cardinality(p_remove), 0) = 0
    or exists (select 1 from unnest(p_remove) as ids(id) where id is null or id = p_keep) then
    raise invalid_parameter_value using message = 'Scegli una scheda da tenere e almeno una da unificare.';
  end if;

  foreach source_id in array p_remove loop
    merged_client := vg_internal.merge_client_records(p_keep, source_id);
  end loop;

  return merged_client;
end;
$$;

revoke execute on function public.vg_merge_duplicate_client_group(uuid, uuid[]) from public, anon;
grant execute on function public.vg_merge_duplicate_client_group(uuid, uuid[]) to authenticated;
