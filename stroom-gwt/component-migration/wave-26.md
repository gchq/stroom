# Wave 26: CSS step 1, dead weight

Part of `../COMPONENT_MIGRATION_ORDER.md` and step 1 of `../CSS_PLAN.md`; written 28 October 2026,
after wave 25, once the user had taken the plan's decisions (removals go ahead; see the plan).

## Done (28 October 2026)

- **Font Awesome** is loaded only in the visualisation frame (`vis.css`), which is the only thing
  that uses it; the app (`font.css`) no longer loads it.
- **Sheets nothing loads, deleted:** Bootstrap's unminified, grid and reboot copies and their source
  maps (the app loads `bootstrap.min.css`), `stroom-theme-transitions.css` and
  `webkit-scrollbar-old.css`. Kept, as something other than `app.css` loads them: `session-list.css`
  (the session list servlet) and `vis.css` and `vis-legacy.css` (`vis.html`).
- **Unused classes' rules, deleted:** 255 selectors (227 whole rules) naming a class that no file in
  the repo names (the analysis's client scan, widened to every text file, so server HTML and
  scripts count), outside Bootstrap, Font Awesome, GWT's own names and names built at runtime. Names
  built as a block plus a literal ending (e.g. `BLOCK + "--disabled"`, which the analysis's tokens
  missed) are kept. Three sheets left empty went with their imports: `cell-list.css`,
  `imagebutton.css` (and `stroom-theme-transitions.css`, above).
- **Unused custom properties, deleted:** 63 declared in Stroom's own sheets and read nowhere (no
  `var()` in any sheet, the name in no other file), mostly the theme's for menus, items, navigation
  and raised panels that no rule reads now. Kept: the palette (`material-design-colors.css`, whole,
  as the tokens the theme picks from), third-party sheets' (MDL, Swagger UI), and the theme's tokens
  content outside the repo may read (`--vis-*`, `--font-size-*`, `--primary-rgb`, `--feedback-rgb`,
  `--text-color-*`).
- The scripts: `scratchpad/cssa/dead.py`, `vars.py`, `refs.py` and `empty.py` (beside the analysis).

## Left for later steps

- Rules for GWT widgets no longer used (e.g. `gwt-SuggestBox`, now that the app has none): with
  step 2, which replaces the GWT widgets left, and step 4, which deletes GWT's theme sheets.
- Classes set that nothing styles (about 30): listed as hooks or removed with step 3's renames.
