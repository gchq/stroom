# CSS: analysis and plan

Written 28 October 2026, from the code as it then stood (branch `worktree-remove_gwtp`, after wave
21), by a scripted inventory of every stylesheet `app.css` loads and every class the client code sets
(Java string literals and JSNI, templates, the app's HTML and scripts, the workbench's runner),
cross-referenced. The aim: no old GWT class names, no Bootstrap, no `standard.css`,
`standard-mod.css` or `stroom.css`; every rule deliberate and either a component's or widget's (its
own BEM block, in its own stylesheet), layout, or shared (variables, a few documented patterns);
nothing unused; no duplicates.

## What there is

`app.css` loads 199 stylesheets (`stroom-core`'s `app.html` and the Markdown preview load it).

| Kind | Sheets | Rules | Classes |
|---|---|---|---|
| Bootstrap (`bootstrap.min.css`, and Stroom's `custom-bootstrap.css`) | 2 | 2,058 | 1,569 |
| Legacy shared (`standard.css`, `standard-mod.css`, `stroom.css`, `components.css`) | 4 | 548 | 449 |
| Theme, fonts, layout, scrollbars | 9 | 31 | 20 |
| Font Awesome (through `font.css`) | 1 | about 800 | 813 |
| Components and widgets (one sheet each, mostly) | 184 | 2,615 | 2,262 |

Outside Bootstrap, of 2,455 classes defined: 2,042 have BEM names, 280 are camelCase, 63 other, 70
are GWT's (`gwt-*`). By use: 1,216 set by the code, 63 used by third-party scripts (Ace, Prism), 64
GWT's own, 54 maybe built at runtime (a prefix in the code, e.g. `cf-` + colour), and 1,058 unused,
of which 813 are Font Awesome's.

## Findings

1. **Font Awesome is loaded into the app for nothing.** `font.css` imports it, and both `app.css`
   and the visualisation frame's `vis.css` import `font.css`; only visualisations use its icons (the
   map's markers). 813 classes and the font, on every page.
2. **Bootstrap is used directly in about 20 places**, but its element rules shape everything.
   * Its classes set by the code: `flex-grow-1` (5), `flex-column` (3), `flex-row` (2),
     `justify-content-center`, `overflow-auto` (1 each), and in stories `badge`, `badge-pill`,
     `popover-body`, `p-1`. Stroom's own `custom-bootstrap.css` adds `allow-focus` (73 uses),
     `form-group` (8), `form-control` (1) and modal, label and badge colours.
   * Its 139 element rules (the reboot: `box-sizing`, the body's font, headings' and paragraphs'
     margins, tables, form controls, buttons, links) are what the app's look rests on, with
     `stroom.css`'s 95 element rules over them. Removing Bootstrap means writing down, in a Stroom
     base sheet, the element defaults the app relies on, and checking every screen.
3. **`standard.css` and `standard-mod.css` are GWT's standard theme and Stroom's changes to it.**
   Most of their rules are for widgets the app no longer uses (`TabBar`, `StackPanel`,
   `DisclosurePanel`, `RichTextArea`, `DialogBox`, `Tree`, `PushButton`, `ToggleButton`, the
   decorated panels): 73 and 29 of their classes are unused. The GWT widgets still used, which set
   `gwt-*` names themselves, are `Label`, `InlineLabel`, `HTML`, `Anchor`, `Frame`, `PopupPanel`,
   `SuggestBox` and Stroom's copies of `TextBox`, `TextArea` and `PasswordTextBox`.
4. **`stroom.css` and `components.css` mix four things:** element rules (95, in `stroom.css`), the
   layout helpers (`max` 282 uses, `dock-max` 108, `dock-container-vertical` 88, `dock-min` 86,
   `w-100` 75, `form` 162, `form-padding` 50, `overflow-hidden` 33, `align-center`, `h-100`),
   shared patterns (`form-control-border` 32, `form-control-background` 26, `sr-only` 12,
   `default-min-sizes` 40), and old component classes (`activityButton`, `contentstore-details-*`,
   `docRefLink*`, `nodePingBar-*`, `resizableAcceptRejectContent-*` …): 40 and 48 of their classes
   unused.
5. **Unused:** 247 classes outside Font Awesome (73 in `standard.css`, 47 `components.css`, 38
   `stroom.css`, 29 `standard-mod.css`, 18 `conditional-formatting.css`, 12 `dashboard.css`, then
   a few in each of 20 others), and 54 that a prefix in the code may build, to check by hand. Ten
   files in `css/` aren't loaded at all (Bootstrap's other builds, `session-list.css`,
   `stroom-theme-transitions.css`, `webkit-scrollbar-old.css`; `vis.css` and `vis-legacy.css` are
   the visualisation frame's). About 300 of the 741 custom properties look unused (the Material
   palette is most of them; to be counted exactly).
6. **Names that aren't BEM** (outside Ace's and Prism's, which their scripts set): in
   `stroom-annotation.css` (38 used: `annotationContainer`, `annotationTitle` …), `dashboard.css`
   (23: `dashboard__designMode`, `tabLayout-*`, `column-*`), `components.css` (30), `stroom.css`
   (10), `term-editor.css` (8: `termEditor-*`, `docRef`, `fieldType`), `pathways.css`,
   `data-grid.css` (`dataGridDisabledCell`, used by five cells), `selection-box.css`,
   `sign-in-page.css` and `change-password.css` (`userNameTextBox`, `emailTextBox`, …).
7. **Sheets that hold several blocks:** `stroom-floormap.css` (104 classes, many blocks),
   `pathways.css` (64), `stroom-annotation.css` (47), `dashboard.css` (40), `ask-stroom-ai.css`
   (27 `ai-*` beside its block), `conditional-formatting.css` (29 `cf-*`), `term-editor.css`,
   `change-password.css`, `icon-colours.css`; and about 30 component sheets that style another
   block inside theirs (e.g. `calendar.css` styling `toolbar-button`, `form-group.css` styling
   `tick-box`), which belongs to a modifier of the other block or to the parent's element.
8. **Classes set that nothing styles** (about 30, outside the workbench's own): some are hooks for
   scripts or plays, some stale (`secret-form`, `date-time-view`, `form-page`,
   `navigation-header-spinner`, `dialog--resizable` …).
9. **Duplicates:** 121 groups of rules with the same declarations in any order (317 rules). Most
   are GWT theme leftovers; the rest are shared patterns written out again: `width:100%;
   height:100%` (15 rules, which `max` already is), flex column (7, as `dock-container-vertical`),
   flex row centred (6), the focus ring (5), the icon size (5), the selected row's colours (4),
   disabled (3).
10. **Combinations set together:** `form max` (87), `dock-container-vertical max` (34), `form
    form-padding max` (27), `dashboard-panel max overflow-hidden` (16), `form-control-background
    form-control-border max` (11), `default-min-sizes max` (10), `stroom-control-label w-100` (9),
    `allow-focus w-100` (7) …: candidates for a named class each, or for the component's block to
    hold the layout itself.
11. **Inline styles left:** 24 calls in 6 files.

## Target

Stylesheets in four layers, imported in this order, each deliberate:

1. **Tokens**: the theme's variables (`theme-root.css`, `theme-dark.css`) and the palette, with
   nothing unused.
2. **Base**: a short Stroom sheet of element defaults (box sizing, the body's font and colours,
   headings, tables, form controls' resets, links), replacing Bootstrap's reboot and the element
   rules of `standard*.css` and `stroom.css`, each rule there because the app needs it.
3. **Layout and shared patterns**: the layout helpers and the few shared patterns (focus ring,
   bordered field, `sr-only`), documented in one place each, with names decided (see below).
4. **Components and widgets**: one stylesheet per BEM block, holding only that block, its elements
   and modifiers; another block appearing inside is styled by a modifier of that block, not from
   outside. Third-party scripts' markup (Ace's themes, Prism's tokens) in its own sheets, exempt
   from BEM.

And a check, run with the build as `TestStroomWidgetsOnly` is: every class the code sets is defined
(or a listed hook), every class defined is set (or listed as built at runtime or third-party's),
every name is BEM, each component sheet holds only its block, and no two rules have the same
declarations.

## Plan

Each step is checked by the screenshot diff of a full workbench run, so first:

0. **Make the screenshots trustworthy.** Done in wave 22 (28 October 2026): two visualisation
   stories still flip between two states (ISSUES.md). About 90 differed from their baselines in every run (see
   ISSUES.md): stale baselines from earlier waves' deliberate changes, to look at and take again, and
   the visualisations' random layouts, to seed or leave out. Until then a change to them can't be
   seen.
1. **Dead weight** (done in wave 26, 28 October 2026; no look change): load Font Awesome only in the visualisation frame; delete the
   ten unloaded files; delete the unused classes' rules (the 54 maybe built at runtime checked by
   hand), the rules for GWT widgets no longer used, the unused custom properties, and the classes
   set that nothing styles (or list them as hooks).
2. **Replace the remaining GWT widgets first** (done in wave 27, 28 October 2026) (the user's direction, 28 October 2026): every GWT
   widget still used gets a Stroom widget of its own, even if it only wraps GWT's (as Stroom's
   `TextBox` extends GWT's), with its own BEM block and stylesheet, so no screen relies on a `gwt-*`
   class (e.g. `.gwt-Label`'s `nowrap`, which cut off Replace Secret's warning). The inventory is
   ISSUES.md's "GWT widgets and raw controls used where a Stroom component should be". This leaves
   `standard.css` and `standard-mod.css` styling nothing Stroom shows, so step 4 can delete them
   rather than move their rules.
3. **Rename what isn't BEM** (done in wave 28, 29 October 2026, but for the shared sheets' own
   classes, which step 4 names as it moves them), block by block (annotation, dashboard, term editor, pathways, floor
   map, conditional formatting, AI messages, sign-in and password, data grid's cells), and split
   the sheets that hold several blocks; move styling of one block from another's sheet to a
   modifier. As wave 17 did for the view-named classes.
4. **Dissolve `components.css`, `stroom.css`, `standard.css` and `standard-mod.css`** (the last three
   done in wave 29, 29 October 2026, into `base.css`, `layout-helpers.css` and components' sheets;
   `components.css` in wave 30, the same day: a sheet per block, and every class Stroom names BEM): element
   rules to the base sheet, layout helpers and shared patterns to theirs, old component classes to
   their components' sheets with BEM names, and the live GWT widgets' styling onto classes Stroom
   sets (so nothing relies on `gwt-*`).
5. **Remove Bootstrap** (done in wave 31, 29 October 2026; its element rules lead `base.css`): its few classes in the code replaced by layout helpers, `custom-bootstrap.css`'s
   rules moved to their components, the element defaults the app relies on written into the base
   sheet (found by diffing every screen with and without it), then `bootstrap.min.css` gone. The
   riskiest step, so on its own, with a full screenshot review.
6. **Rationalise** (done in wave 32, 29 October 2026): duplicates merged into the shared patterns or removed; the common combinations
   given a named class each, or folded into the components' blocks; the remaining inline styles moved
   to classes and custom properties.
7. **Enforce**: the check above, in the build.

## Decisions (taken 28 October 2026)

* **Removals go ahead now**, with a full screenshot diff around each step; other branches that touch
  the same sheets will need merging.
* **The layout helpers keep their short names** (`max`, `dock-max`, `w-100` …), moved into one layout
  sheet as deliberate, documented utilities.
* **Combinations fold into components:** each component's own BEM class takes the declarations; only
  a pattern shared by many different widgets becomes a named shared class.
* **Looks:** every changed screenshot is looked at; small shifts that don't harm the look are
  accepted with new baselines, and anything that looks worse is fixed.
