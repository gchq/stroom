# Wave 38: UI audit wave 4, empty wrappers and duplicate frames

The fourth of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), themes T2 and T3); written 29
October 2026, after wave 37.

## Done (29 October 2026)

- **Empty wrappers went** (security-settings-02, data-pipeline-processing-04,
  explorer-documents-shell-06): `ApiKeys`, `Users`, `SigningKeys`, `Credentials`, `Accounts`,
  `DataPreviewTab`, `SourceTab` and `StreamingProcessing` held nothing but a slot for one list or
  viewer. Their controllers show that part's widget as their own (`initWidget(part.asWidget())`, as
  UI-GUIDE.md asks); each wrapper only added `max`, which the parts' roots have.
- **`HeadedPagerPanel` went** (widgets-1-18): it copied `PagerPanel` to put it in a form group. The
  job and node job lists declare a `FormGroup` around a `PagerPanel`, and `setHeading` sets its label.
- **`LinkTabPanel` is declared, not copied** (security-settings-05, floormap-pathways-stores-10):
  User Preferences and the AI settings dialog declare it beside their buttons; Credential Edit is
  one, and its own tab bookkeeping (`tabViewMap`, `addTab`, `changeSelectedTab`) went; the floor
  map's editor and map declare it as their dock, which `FloorMapDockController` attaches to
  (`attach(LinkTabPanel)`), so `FloorMapDock` and the two `setDock` slots went. The dock's tab bar is
  now tied to its panel for screen readers, as every link tab panel's is. `TabbedController` wasn't
  used: it refuses OK on invalid input and would be the whole dialog, without its buttons.
- **Create User declares its group form** (security-settings-04): `CreateUser.ui.xml` declares the
  `NameEntry` beside the other two forms, and `CreateNewGroupController` attaches to one
  (`attach(NameEntry)`) rather than injecting it; Add User Group attaches it to one it makes.
- **A look kept:** in User Preferences and the AI settings the tabs and the tab showing were two rows
  of the dialog's form, which puts a gap between its rows; as one `LinkTabPanel` they lost it (18px).
  Each dialog gives its panel the form's gap (`user-preferences-dialog__tabs`, and a new
  `ask-stroom-ai-config-dialog` sheet), and `LinkTabPanel` hides its toolbar row while it has
  nothing to show, so the gap isn't doubled.
- **Checked:** compile, checkstyle, the client tests, the app's GWT compile, and the stories of every
  changed screen and dialog, the app shell and every document editor (428), with the leak check:
  all pass. Eleven screenshots changed and were taken: the two settings dialogs' tabs sit 2px
  higher and their content 4px higher (the shared panel's row), and Credential Edit's tabs are 5px
  in from the edge, as every link tab panel's are.
