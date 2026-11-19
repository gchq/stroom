# Wave 31: CSS step 5, Bootstrap removed

Part of `../COMPONENT_MIGRATION_ORDER.md` and step 5 of `../CSS_PLAN.md`; written 29 October 2026,
after wave 30.

## Done (29 October 2026)

- **What Stroom used of it** (the class census of every story, and the code): its element rules
  (Reboot and type: box sizing, the body, headings', paragraphs' and lists' margins, labels,
  buttons' and inputs' resets, code and preformatted text, tables …); the utilities `flex-row`,
  `flex-column`, `flex-grow-1`, `justify-content-center` and `overflow-auto` in eight templates;
  `form-control` on one field (Edit Tags); the badges and `popover-body` in story harnesses; and
  its `:root` variables `--primary`, `--warning` and `--danger` in three sheets.
- **Its element rules** lead `base.css` now, as Bootstrap's (v4.6, MIT, with its notice), less its
  print rules, then `custom-bootstrap.css`'s element rules (`pre`, `label`), then the rest as
  before, so the order is unchanged.
- **The utilities** became the components' own rules: Edit Tags' and Remove Tags' rows and the
  move buttons' column (`explorer-node-edit-tags__move`) are in their sheets, the forms' needless
  `flex-column` went (a form is a column), `flex-grow-1` is the layout helper `dock-max`, and the
  table builder's info box scrolls with `table-builder-processing__info` (in
  `table-builder-processing.css`, after `form-control-border`'s `overflow: hidden`, which
  Bootstrap's `!important` had outranked). `overflow-hidden`, which Bootstrap also defined, is
  `!important` in `layout-helpers.css`, as Bootstrap's was.
- **Edit Tags' field** is a plain Stroom text field (no `form-control`), so it is as tall as the
  others (32px, not Bootstrap's 38px) and shows the focus ring every field does; the only look
  change of the step, with new baselines for its three stories.
- **Its variables:** the theme's `--primary` never applied (the theme class is on `<html>`, as is
  `:root`, and Bootstrap's came later), so the node ping bar's and percent bar cell's colours and the
  advanced quick filter's warning take the theme's new `--status__color--ok`, `--warning` and
  `--danger`, with Bootstrap's values, which they showed.
- **`custom-bootstrap.css`** dissolved: `allow-focus` has its own sheet (where it was), a form
  label's spacing and weight are `form-label.css`'s, and the rest (dropdowns, modals, badges,
  validation, `.col`, the form group's margin, which `form-group.css` always outranked) styled
  nothing Stroom shows. The stories' badges are the change password screen's `password-length` pill,
  and the popup harness's body a hook of its own (`harness-editor-body`).
- **Checked:** all 1,746 stories pass with the leak check (the analytic rule, Edit Tags and main
  menu stories again after `table-builder-processing.css` was added); no screenshot changed but Edit Tags'
  three and the unsteady visualisations. The main menu's two stories no longer break axe's
  `target-size`, so their baseline entries went.
