# Shop & Parallelo design QA

final result: passed

## Visual sources and evidence

- Selected visual: `docs/catalog-selected-design.jpg`, derived from the user's selected first design with the three compact category tiles from the third design and a transparent logo. Original ImageGen source: `generated_images/exec-87878d21-fd6e-4181-9284-e26192aed3cf.png` (853 × 1844 pixels).
- Implementation: browser-rendered preview at `http://terminal.local:4173/`, mobile comparison at `/mobile.html` in a 390 × 844 CSS pixel iframe.
- Browser screenshot: `docs/catalog-mobile-preview.jpg` (390 × 844 pixels), cropped from the actual browser capture to the iframe bounds, without browser chrome. Density 1. Source normalized to 390 × 843 pixels.
- Full-view comparison: `docs/catalog-design-comparison.jpg`. Both visual sources were opened together in this comparison before assessment. The logo, hero, category row, search, toolbar, grid and product actions are readable in the comparison; no separate magnification was needed.
- State: default catalog, no filters, 209 existing products. Product images, names and prices are real VG inventory, rather than the mockup's illustrative inventory.

## Comparison history

- P2: mobile hero background initially cut off the bag at the right edge. Fixed mobile background position to `100% center`; captured the revised browser view and compared again. The bag is now fully visible.
- Intentional production adaptations: use legible 15–16 pixel primary inputs/product names and sufficient touch targets rather than shrinking the mock's dense controls. The compact photographic category row replaces duplicated category tabs on narrow screens. Real inventory has variable name lengths, so cards reserve two title lines. Desktop uses four columns; smartphone uses two.

## Required surfaces

- Typography: Georgia display/product titles, system UI controls, dark ink on ivory. Heading hierarchy and two-line hero retained; no new font service required. Longer real product names wrap.
- Spacing/layout: transparent header on a seamless photographic hero; prominent search; three compact 82-pixel category tiles; caramel controls; two-column mobile cards. Controls remain inside the viewport. Larger production tap targets deliberately reduce density versus the illustrative mock.
- Colors: warm ivory `#fcf9f3`, navy `#162335`, caramel `#a85f2c`; outlined product actions and restrained borders. No white background on the logo or header.
- Images: real product photos remain unchanged and use contain; generated hero/category photographs are compressed WebP. Existing transparent SVG brand asset is preserved, with a transparent computed background. Standard outline UI icons are from Tabler Icons 3.31.0 (MIT), bundled locally. No invented product placeholders.
- Copy/content: exact Shop & Parallelo brand, selected headline, no-payment order request messaging. No fake stock, reviews, promotion or payment claims.

## Browser checks

- Desktop page and 390-pixel mobile iframe opened in the supported cloud browser.
- Search by code `294039` returned one real article; product gallery advanced to `2 / 15`.
- Category Scarpe + brand Prada + maximum price €150 returned one item at €145; ascending price sort applied.
- Brand logo loaded with computed background `rgba(0, 0, 0, 0)`.
- No application console errors observed. One unrelated browser extension metadata error was excluded.
- API/DOM regression tests cover image navigation, bidirectional swipe handlers, zoom/close, missing images, URL renewal, cart/order review, multikeyword/accent-insensitive search, category grouping, price filters, sorting, empty results and reset.
- Responsive Chromium regression and live checks are retained in GitHub Actions; deployment must wait for prepublication checks to pass, and must then be confirmed by the live checks.

## Findings and follow-up

No remaining actionable P0/P1/P2 findings in the compared implementation. P3: original inventory photographs have varied lighting and watermarks; they are preserved intentionally, and should be improved through VG product data rather than altered by the storefront.

## Implementation checklist

- Preserve current VG data and private image signing.
- Deploy the same catalog export under the existing SimplexApp domain's `/shop/` path.
- Keep CORS limited to the existing VG origin and `https://simplexapp.it`.
- Confirm both live catalog URLs and run responsive checks before reporting publication complete.
