# V e G 1.0.155 / catalogue refresh — 26 October 2026

The same repository, Pages catalogue, Android WebView and Supabase project are retained.

- Management outlines reduced to 2px with a 1px lower shadow; controls use 1.5px. The six bottom destinations share the available width. Chart controls remain compact. Grid contents can shrink and wrap.
- Catalogue uses cream, pale blue and warm yellow, lightly illustrated cards and a sibling vector logo based on the existing mark. Existing photos are unchanged. Originale/Standard remains visible and filterable. The mobile heading and navigation are compact.
- Article choices are remembered while visiting the catalogue. Legacy cart rows missing required choices are explicitly rejected before checkout; Modifica replaces that row rather than adding a duplicate. Quantity and variants can be changed per cart line.
- Prominent persistent notices show the first missing field, request failures and successful order number. Browser validation and explicit privacy consent remain mandatory.
- Customers can reopen their own explicitly linked request from I miei ordini. Draft amendment state survives reload. Annulla modifica clears the draft and leaves the submitted order intact.
- Only unpaid requests not yet saved/processed by the administrator are editable. Ownership, original version and current status are enforced again inside an atomic database function. Its execution is restricted to service_role; it is SECURITY INVOKER, not SECURITY DEFINER. Audit rows preserve the previous order, address and item rows under owner-only RLS.
- Amendments keep the original order ID, number and server creation time; they replace quantities/variants/address atomically, revalidate current catalogue prices server-side and requeue the existing FCM outbox.

Validation: Node/DOM checks; real rollback-only SQL under service_role (ownership, stale version, revision history and administrator guard); Chromium and WebKit at 360/412/1280px (legacy-size repair, prominent errors/success and in-place amendment surviving reload). Management checks include overflow and all six navigation destinations, plus full authenticated boot. Existing gallery, shipping, catalogue authentication, personal sessions, WhatsApp no-link and FCM tests remain enabled.

Screenshots in the catalogue test artifact use existing repository photographs and isolated demonstration orders; they are not production customer records. Real SQL test records and notification outbox rows were rolled back.

Administrator deletion remains usable: deleting an order removes its transient notification queue entry and leaves the revision audit intact with a null live-order reference. The rollback SQL also verifies this path.
