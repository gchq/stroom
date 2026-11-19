# Wave 33: CSS step 7, enforced in the build

Part of `../COMPONENT_MIGRATION_ORDER.md` and step 7 of `../CSS_PLAN.md`; written 29 October 2026,
after wave 32. The CSS plan's last step.

## Done (29 October 2026)

- **`TestStylesheets`** (`stroom-core-client`'s tests, beside `TestStroomWidgetsOnly`), over the
  sheets `app.css`, `vis.css` and `vis-legacy.css` load and the code that sets classes (the client
  modules, the shared code they compile, and the pages):
  - every sheet is loaded by a page, and every import is there;
  - every sheet is well formed (comments closed, braces balanced), the check wave 32's broken pass
    lacked;
  - every class in Stroom's sheets, and every class the code sets (in a template's class
    attributes, as a literal given to the methods that set or look for one, or in HTML built in a
    string), is named BEM style; Ace's names, the annotation types Stroom gives Ace's gutter and
    GWT's `popupContent` aren't Stroom's to choose;
  - every class the code sets is styled, or is a block whose elements or modifiers are, or is in
    the test's `HOOKS` (41 classes that code or stories find an element by);
  - every block a sheet styles is named somewhere in the code (as itself or by one of its elements:
    names built at runtime, such as `BLOCK + "__segment"` or the conditional formatting styles'
    `cf-red`, count).
  A rule for an element or modifier nothing sets any more can't be found by reading the code (so
  many names are built at runtime); the class census finds it.
- **Found and fixed on the way:** the last camel-case names, two renamed as stories and scripts
  select them (`conditionalFormatTypeSelection` is `conditional-formatting-rule-editor__type`,
  `tabLayout-settingsButton` `tab-layout__settings-button`) and three dropped (`documentAssetTree`,
  `explorerTree-container`, `taskManager`); classes no sheet ever defined (`flex-grow-2`,
  `overflow-y-auto`, `dock-container`, `svg-icon` on two permission cells, Font Awesome's classes on
  the tick box's tick, which Font Awesome no longer styles outside a visualisation, and the empty
  `marker` child of every glass); `session-list.css`, which no page loads (an old server page's);
  and `form-control-background`, which nothing sets since `control-box`.
- UI-GUIDE.md says what the build checks.
- **Checked:** all 1,746 stories pass with the leak check; no screenshot changed but the
  visualisations' (the force ones, and a line chart story by 56 pixels, as under load).
