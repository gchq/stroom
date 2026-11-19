# Wave 20: Finishing the component split

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 19. The "stays" and
short cuts waves 13 to 15 left in the component split (ISSUES.md, "Left from wave 13's audit" and
"Left from wave 15").

## Done (28 October 2026)

- **Navigation:** the explorer's eight buttons, its tree (hidden until the explorer is first shown,
  as before) and the logo's tab index and `aria-hidden` are in `Navigation.ui.xml`; the controller
  wires them through `Navigation.ExplorerAction` (`setOnAction`, `setActionEnabled`, ...), as the
  assets tab does. `Button` has `setAriaHidden` for the template.
- **Main:** the maintenance banner is an `AriaPanel` with `role="alert"` in the template; the selected
  tab's colour sets the theme's `--curve-tab__selected-color` on the main panel, not a generated rule.
  The split panes, the page border, the inner content's size and the landmarks on the widgets it is
  given stay in Java, as they depend on what is known only at runtime.
- **Welcome:** the build information is a table with `<th scope="row">` headers in the template, bold
  and to the start as the cells were.
- **The user's tab:** `UserTabs`, a `TabbedController<UserRef>`, in place of the hand-rolled tabs;
  `TabbedController.read` takes no `DocRef` for what isn't a document.
- **Ask Stroom AI:** the app root owns the chat (made once by `AskStroomAiScreens`, no longer a GIN
  singleton nothing owns); the controller changes the conversation through the component's methods
  (`appendMessages`, `markLastMessage`, `replaceMessage`, `setPartText`, ...) and hears its buttons
  through callbacks; the Copy button is the component's own; the toolbar's dialog puts the focus in
  the message box (a changelog entry; `ToolbarEntryPoint` checks it); `///` javadoc throughout.
- **Quick filters** all use `setOnChange` (it fires with the event, with the same text).
- **Constructor javadoc:** every client `@Inject` constructor documents each parameter (570 lines in
  103 classes).
- **Unused `view` fields** in 16 controllers removed.
- **The settings dialog's scroll rule**, which never matched, is dropped with its stylesheet.

Decided: a screen that is one smart part and nothing else (node groups, volume groups, processor
profiles, annotation tags, some content tab shells) keeps showing that part's widget (UI-GUIDE.md).
