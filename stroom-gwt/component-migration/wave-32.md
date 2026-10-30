# Wave 32: CSS step 6, rationalise

Part of `../COMPONENT_MIGRATION_ORDER.md` and step 6 of `../CSS_PLAN.md`; written 29 October 2026,
after wave 31.

## Done (29 October 2026)

- **`base.css`'s overridden declarations** (three generations of element rules, Bootstrap's,
  GWT's and Stroom's, one over the other): 41 declarations that a later rule in it overrides for
  every one of their selectors (the same selector text, and the same property or a shorthand that
  covers it, e.g. `margin` over `margin-top`) went, and a rule that only repeated a later one.
  `!important` declarations, vendor prefixes and IE hacks were left alone.
- **A shared pattern:** a box holding a control (a list, tree, grid or editor) framed and filled
  as an input is, which 26 templates and `FindAnnotationList` wrote as `form-control-border
  form-control-background`, is one class, `control-box`. `form-control-border` and
  `form-control-background` stay for a box that wants only one.
- **The status colours:** the theme's `--bootstrap-error`, `-warning` and `-success` (and their
  `-rgb`), Stroom's own though named for Bootstrap, are `--status__color--danger`, `--warning` and
  `--success`, which wave 31's copies of the same colours become; `--status__color--ok` stays.
- **Inline styles to classes and custom properties:** the glass laid over the page while dragging
  (`Glass`) sets `--glass__left`, `--glass__top`, `--glass__width` and `--glass__height`, which its
  four classes read; the focus tooltip (`FocusTooltip`) is shown with `focus-tooltip--shown` and
  placed with `--focus-tooltip__left` and `--top`; the tree renderer's pointer is
  `layered-canvas__layer--pointer`; the delete confirmation's gap is `delete-confirm__gap` (and
  its other two classes BEM). What is left inline is custom properties only, as `CssProperties`
  asks.
- **Duplicates and dead rules:** the password box's and value spinner's status lines are the
  shared `sr-only` rather than copies of it; the sign-in page's copy of `.feedback` went; 13 empty
  rules, `theme.css`'s theme transitions (nothing sets `stroom-theme-transitions` since wave 26),
  `button.css`'s `stroom-theme-dark2` (no such theme), `pathways.css`'s `service-name` (only in
  commented-out code) and `DebugPane` with its GWT module (nothing called it) went.
- **What the first full run caught:** the first pass at removing empty rules let a "selector" start
  on a line inside a comment (` * Copyright …`) and run on to the empty rule, so it ate the licence
  header and comments of 21 sheets; in `source-viewer.css` it left a comment open that swallowed
  its rules, and the source viewer's title row fell apart (11 screenshots). Those sheets were
  restored and the pass redone with a selector that can't hold a comment; every sheet is now checked
  for closed comments, balanced braces and its licence header. `line-col-input.css`, left with no
  rules, went.
- **Checked:** all 1,746 stories pass with the leak check, and no screenshot changed but the
  force visualisations' (as under any full run's load).
- **Left as they are:** declarations two unrelated components happen to share (the analysis finds
  about 80 such groups, mostly two or three declarations alike), and the layout helpers used beside
  a component's class (`form max`, `dock-container-vertical max` …), as the plan decided.
