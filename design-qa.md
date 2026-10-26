# Shop & Parallelo — selected direction 2

final result: passed

## Visual target and rendered evidence

- Selected target: `/workspace/scratch/cbfc8f33e07e/generated_images/exec-8ab03bb6-6e91-46ba-ab36-c5783409836e.png` (the user's option 2, Prodotti subito).
- Implementation: `https://meapps-it.github.io/VG/catalogo-nuovo.html`.
- Browser-rendered screenshot: `/workspace/scratch/vg-catalogo-prodotti-subito.jpg`.
- Full-view comparison: `/workspace/scratch/vg-direction2-comparison-final.jpg`.
- Focused header comparison: `/workspace/scratch/vg-direction2-header-comparison.jpg`.
- Earlier comparison: `/workspace/scratch/vg-direction2-comparison-before.jpg`.
- Logical target viewport: 390 x 844. Source raster: 853 x 1844, normalized to 390 x 844. Live browser iframe: 405px including a 15px desktop scrollbar, 390px content width, captured at 1x. Screenshot cropped from the actual browser capture without retouching product content. The 26px administrator notice is excluded only in comparison images to align with the customer-state mockup; it remains in the delivered real screenshot.
- State: catalogue loaded, all qualities/categories, no search, 207 real published products. Mockup exemplar order and sample data differ from live alphabetical inventory. Real names, prices, quality, original photo watermarks and unavailable-photo states take precedence over generated examples.

## Comparison history and findings

1. Initial comparison found P2 vertical spacing drift: the header and metadata made the first product row start at 259px after excluding the administrator notice, compared with approximately 235px in the normalized mockup. Header spacing, logo width, control gaps and metadata rhythm were tightened.
2. Post-fix capture shows the catalogue at y=265px including the administrator notice, or y=239px for the customer surface. Two 176.5px product columns, 9px gap and original contain-fit photographs match the selected product-first structure. There is no hero/banner or introductory marketing block. The focused comparison confirms logo/actions, search/filter row, segmented quality control and category navigation placement.
3. No actionable P0/P1/P2 visual differences remain. Production product names and metadata remain more readable than the small generated specimen text; dynamic wrapping and preservation of actual product data are intentional.

## Required fidelity surfaces

- Typography: device system sans-serif retained. Mobile product names 13px, quality 11px, prices 17px; readable hierarchy preserved. No display/serif font introduced.
- Layout: compact header, search with adjacent filter control, three-part quality control, horizontally scrollable real categories, result count and retained sorting, then two image-led mobile columns. Desktop retains responsive multi-column layout.
- Colours: white surface, charcoal text, pale warm-grey selected quality control and understated bronze action/category accents follow the selected concept. No former ivory hero or decorative thumbnail strip remains.
- Image quality/assets: existing vector logo and existing licensed icon assets reused. Actual Supabase photographs are unchanged, contain-fit and opened through the working gallery. Generated concept images were not installed as product photos. Existing unavailable-photo records remain truthful placeholders.
- Copy/content: Shop & Parallelo retained. Actual category/brand values, quality, prices and counts remain backend-controlled. Selected concept's erroneous example prices/quality were not copied into inventory.

## Interaction and browser checks

- Live quality filter: Originale returns 68 articles; combined Originale + Louis Vuitton + Alma search returns four.
- Live Alma PM gallery: 1/8 -> 2/8; add-to-cart gives EUR 290. Test cart was cleared; no production order was submitted.
- Existing complete catalogue regression suite passes at 360, 412 and 1280px: gallery, thumbnails, swipe, zoom, URL refresh, shipping, contact links, form validation and isolated checkout.
- Added assertions cover no hero, visible header WhatsApp, first product row before y=270px and exactly two mobile columns.
- API/order/access/FCM isolated tests pass. No backend, pricing, notification or Android package change was required by this visual iteration.
- Browser console inspected: only browser-extension metadata errors were present; no application error was observed.
- GitHub Actions catalogue run: 40247106995, success. GitHub Pages run: 40247106432, success.

## Follow-up polish

P3: catalogue records with long names can occupy more lines than the illustrative mockup. Existing articles without source photographs continue to show their missing-photo state. Neither was concealed or replaced with invented product imagery.