# V e G 1.0.156 — legacy article metadata and order round trips

The existing catalogue, photographs and saved sale prices remain in place. No article or database row is rewritten by these compatibility changes.

- VGMETA reads accept normal JSON, JSON serialized as a string and the escaped object bodies present in older records. Parsing uses bounded JSON decoding and rejects arrays/primitives/malformed content; no evaluation is used.
- Both the management importer and the catalogue recover stored brand, sizes, colours and existing photo references. Photo references still pass the same owner/SKU and path checks before private signing. A missing Storage object remains a missing photo.
- Public catalogue descriptions use public description fields and exclude supplier-cost lines; internal VGMETA content is never returned as a public description.
- Cloud order rows with quantity greater than one become equivalent unit rows, matching the management editor's existing representation. Unit prices, per-article size/colour and total discount cents are preserved. Saved order header totals are not recalculated during import.
- Individual sizes from a customer's request are shown in the editor and satisfy size requirements without forcing one new global size. The global size remains required for unsized shoe/belt rows.
- Unavailable cart items stay visible with a Remove control. Checkout refuses an incomplete order rather than silently omitting those items.
- Updated-order FCM messages identify the modification and preserve the same order ID, high-priority channel and notification tag. Already delivered events remain deduplicated.

Tests cover escaped metadata, malformed input, private photo-path restrictions, supplier cost exclusion, quantity/discount round trips, distinct sizes, per-row size validation, unavailable-cart repair and new/updated/deduplicated FCM payloads. Existing browser, article, publication and order tests remain enabled.

Remaining data limitation: the read-only inspection found 47 published article records without photo links in prodotti_foto. The sampled legacy references for 315074, 315874 and SB1066 also have no matching objects in the articoli bucket. Decoding their metadata does not recreate the missing originals.
