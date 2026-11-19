# Wave 27: CSS step 2, Stroom's own text widgets

Part of `../COMPONENT_MIGRATION_ORDER.md` and step 2 of `../CSS_PLAN.md` (the user's direction:
replace the GWT widgets left, even if only by wrapping GWT's, with BEM CSS); written 28 October
2026, after wave 26.

## Done (28 October 2026)

- **What was left:** of the GWT widgets the client code still used, only `Label`, `HTML`,
  `InlineLabel`, `Frame` and `Anchor` put GWT's classes (`gwt-Label` and the like) on the page; the
  layout panels (`FlowPanel`, `SimplePanel`, `HTMLPanel`, `ScrollPanel`, `FocusPanel`,
  `ResizeLayoutPanel`) set none, and the two `PopupPanel`s (`SelectionPopup`, `SuggestionBox`) set
  their own. `SuggestBox` went in wave 25.
- **Stroom's versions** (`stroom.widget.text.client`, module `stroom.widget.text.Text`), each
  extending GWT's and setting its own BEM class in place of GWT's: `Label` (`text-label`,
  `text-label.css`: on one line, as `gwt-Label` was), `Html` (`html-text`), `InlineLabel`
  (`inline-label`), `Frame` (`frame`, `frame.css`: no border, as every Stroom frame had turned GWT's
  bevel off) and `Link` (`link`, for GWT's `Anchor`: a real link to an address). As with GWT's, a
  template's `styleName` replaces the class and `addStyleNames` adds to it, so every label wraps, or
  doesn't, as before.
- **The code moved** (268 files, by `scratchpad/cssa/textwidgets.py`): templates' `g:` tags to
  `tx:`, Java's imports and the renamed types (`HTML` to `Html`, `Anchor` to `Link`) in code only.
  The rules on `.gwt-Label` (user tasks, setting blocks, inline label icons) are on `.text-label`;
  GWT's own rules for these widgets (`standard.css`'s `.gwt-Frame`, `.gwt-HTML`, `.gwt-Label`, and
  `stroom.css`'s unused `.gwt-Label-disabled`) are gone.
- **Refused from now:** checkstyle (`GwtWidgetWithStroomVersion`) and `TestStroomWidgetsOnly` refuse
  GWT's `Label`, `InlineLabel`, `HTML`, `InlineHTML`, `Frame` and `Anchor`.
