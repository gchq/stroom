# Wave 25: SuggestionBox

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 24. The last GWT
`SuggestBox`es, an expression term's Value, From and To, replaced as agreed that day: not by
`SelectionBox`, whose behaviour stays, but by a Stroom `SuggestionBox`.

## Done (28 October 2026)

- **`SuggestionBox`** (`stroom.item.client`, `suggestion-box.css`): a text field whose value is any
  text, with suggestions as hints; a WAI-ARIA editable combobox with list autocomplete. Typing, a
  click or Down shows the suggestions for what was typed (from a `SuggestionSource`) in a list below
  the field; the focus stays in the field (`aria-activedescendant`); Down and Up move through them,
  Enter or a click puts one in the field, and Escape (caught before a dialog sees it), Tab or leaving
  closes the list keeping what was typed. Keys the list uses don't reach handlers added from outside,
  so Enter choosing a suggestion doesn't also run a term's query. Stories:
  `Widgets/Inputs/SuggestionBox` (`TypingSuggests`, `KeysChooseASuggestion`, `EscapeKeepsWhatWasTyped`,
  `ClickChoosesASuggestion`, `AnyTextIsKept`).
- **Not on the selection box's list:** `SelectionList` moves the real focus into its rows (and has its
  quick filter), which an editable combobox can't; the suggestion list is a plain list box the field
  drives, drawn as the selection list's rows are (the row colours) in the shared popup box.
- **`AsyncSuggestOracle`** is `TermValueSuggestions`, a `SuggestionSource` (debounced fetches while
  typing, at once when asked again for the same text; True and False for a boolean field;
  `TestTermValueSuggestions`).
- **Refused from now:** checkstyle (`GwtWidgetWithStroomVersion`) refuses GWT's `SuggestBox`,
  `SuggestOracle` and `MultiWordSuggestOracle`, and `TestStroomWidgetsOnly` a `g:SuggestBox` in a
  template; UI-GUIDE.md names `SuggestionBox`.
- `term-editor.css`'s `.termEditor-item.textBox` is now the rule for the value's field inside the box.
