# Wave 40: UI audit wave 6, markup out of controllers and Java

The sixth of the UI audit's waves ([../UI_AUDIT.md](../UI_AUDIT.md), theme T4); written 29 October
2026, after wave 39. Controllers pass data only, fixed parts are in templates, repeated rows are
`SafeHtmlTemplates` in their component, icons are `svg:Icon`, ARIA goes through typed setters, cells
use Stroom's cells, and fixed inline styles are classes (a per-instance value is a custom property
a rule reads).

## Done (29 October 2026)

- **The AI chat** (security-settings-01, 09): two dumb components, `AiMessage` (a message, its
  attachment and its Delete, Copy and time) and `AiDetailsMessage` (the collapsible thinking, data,
  request and progress messages), in `ai-message.css` (BEM). Delete and View data are toolbar
  buttons in toolbars and Copy a Stroom `Button`: no raw `<button>`s or borrowed `toolbar-button`
  classes. The controller passes each message's data; streaming, the pending message's swap and
  the progress line work as before.
- **Sign-in and alerts** (security-settings-06): Login's and the authentication error's images are
  declared icons; `TextBox`/`PasswordBox` set `autocomplete` from the template; Change Password
  declares its strength badge (`PasswordBox` takes it as a template child); `CommonAlert` declares
  its icon and detail, and `LinkButton` has `setAriaExpanded`/`setAriaControls`.
  `DescriptionBuilder` (security-settings-07) moved beside its users and is documented.
- **Info, delete confirmation and annotations** (explorer-documents-shell-05, 07): the Info dialog
  renders its lines from data; the delete confirmation's markup is `DeleteConfirmationDetail`'s
  templates (the confirm dialog takes SafeHtml); the annotation history is a dumb `AnnotationHistory`
  widget declared in `AnnotationEdit.ui.xml`, with its comment decoration in `CommentDecoration`;
  Assign Yourself is a `LinkButton` (`setAriaDisabled` keeps it focusable) and `AssignYourselfLink`
  went; `SettingBlock` declares its icon.
- **Table cells and the pipeline** (dashboard-query-20, 21, workbench-11, data-pipeline-processing-17,
  19, 20): the result table's column filter is a composite of Stroom's `TextFieldCell` and a new
  `ToolbarButtonCell`, keeping its typing, Enter and focus behaviour (BEM classes); the workbench's
  look-alike cell went; result cells' wrap and grouping are classes, not inline styles; the
  pipeline path bar's markup is its component's templates; the data viewer's View Source is a
  declared `LinkButton`.
- **Widget internals** (widgets-2-27 to 31, widgets-1-26, floormap-pathways-stores-12): the
  drop-down's and value spinner's icons and the busy spinner are declared `svg:Icon`s; the drop-down
  declares its feedback; the XSD constraint display and display box have templates; the code
  editor's overview bar and indicator popup have templates, its markers a template with their
  position a custom property; `SafeHtmlUtil`'s coloured and muted text are classes
  (`coloured-text.css`); a popup's given size is a custom property; the floor map swatch's image is
  sized by a class.

- **A story fixed:** the stepping screen's Stepping With Tree checked the first
  `.code-editor__summary` on the page; every code editor now has one (declared, hidden until it has
  indicators), so the first was a hidden editor's. It now checks that the error summary is shown.

- **A width kept:** the drop-down's root is now a wrapper with no box of its own (`display:
  contents`, so its button and feedback lay out as before), so a width given to its element did
  nothing: the view states sheet's picker came out 40px narrow. `DropDownBox.setWidth`/`setHeight`
  size its button, as its classes already go to the button; nothing in the app sizes a picker that
  way, and the story now sizes with `setWidth`.

- **Checked:** compile and checkstyle of every client module, every client test, both GWT compiles,
  and two full runs at 6 workers with the leak check (the first found the stepping story, the
  second all 1,741 passing, with no retries, and the picker's width), then the inputs', selectors',
  data grid header's and app shell's 169 stories after the width fix: all pass, and the only
  screenshot taken is the data grid's header input (its box is the text field cell the app's
  column filter uses, 2px further in).

## Looks that changed

- The column filter box, View Source and Assign Yourself show the standard keyboard focus ring.
- The AI chat's decorative status and summary icons no longer take the toolbar button's hover.

## Left, and why

- `ContextChip` still writes its icon as markup; replacing it means deleting the class and its test,
  outside the finding's lines.
- The delete confirmation stays SafeHtml rather than a component, as `ConfirmEvent` takes SafeHtml.
- The annotation history's rows are still built with `HtmlBuilder` inside its widget (only its
  actions button is a template).
- `RefreshButton`'s four icons stay in Java, as the finding allows.
- `SafeHtmlUtil.Template`'s `divWithClassStyleAndTitle` and `divWithClassAndStyle` have no users;
  they go in a later dead-code pass.
