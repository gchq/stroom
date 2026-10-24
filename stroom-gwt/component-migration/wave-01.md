# Wave 1: Conventions and simple leaves

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

Set the leaf contract (no-arg constructor, setOnX, setReadOnly, focus(), Binder made with GWT.create) on the smallest leaf widgets and cells that are not in flight, so every later wave has a model to copy. Buttons gain setOnClick, IconBox's setReadonly is renamed setReadOnly, and RefreshButton/Spinner* get setRefreshing in place of their own task counting.

## CSS and template notes for the wave

- Runtime values as custom properties: no shared setter exists yet. Two private JSNI copies do element.style.setProperty(name, value): DataGridDom.setCssVariable (stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGridDom.java:108, package-private) and ClassificationLabel.setStyleProperty (stroom-core-client/src/main/java/stroom/data/client/view/ClassificationLabel.java:84). GWT's Style.setProperty can't set a '--' name. Add one public ElementUtil.setCssVariable(Element, String, String) in stroom.widget.util.client.ElementUtil and use it for Button's ripple and LayeredCanvas. Cells use SafeStylesUtils.fromTrustedNameAndValue("--name", value) in their SafeHtml style attribute.
- Custom property naming follows the existing --block__property form (--data-grid__columns, --classification-label__background-color): --ripple__left/--ripple__top (Button.css .ripple), --layered-canvas__width/--layered-canvas__height (new layered-canvas.css), --percent-bar-cell__width (PercentBarCell.css), --colour-swatch-cell__background-color (ColourSwatchCell.css), --expander-cell__indent (ExpanderCell.css).
- New file layered-canvas.css (stroom-app/src/main/resources/ui/css/, @import from app.css as icon-box.css is at app.css:61): .layered-canvas sizes from the two variables; .layered-canvas__layer { position: absolute; left: 0; top: 0; outline: none; width/height from the inherited variables }. No existing class covers absolute 0,0 placement; .max (stroom.css:793) is only width/height 100%.
- Convention to agree for this wave: a widget that is its own single element (extends ButtonBase, FlowPanel or Widget: ToolbarButton, LinkButton, RefreshButton, Toolbar, ToolbarRow, FormLabel, FormGroupHelpButton) keeps its root block class in its constructor, as there is nothing else to template; a widget with fixed inner markup gets a template.
- Template for an element-root widget: CurveTabSelector (stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/CurveTabSelector.java:30-57) is the existing pattern (UiBinder<DivElement, X>, createAndBindUi(this), setElement). ButtonBase subclasses (Button, FabButton) only take their element through super(element), so they bind a static Binder to a small Parts holder inside the super(...) call, or move to FocusWidget, which has a no-arg constructor.
- Cells (TextFieldCell, ActionCell, the info cells, TickBoxCell, ExpanderCell, ValueSpinnerCell and the explorer, dashboard, AI and annotation cells) have no UiBinder template: SafeHtmlTemplates stay for grid cells and rows (COMPONENT_MODEL_DESIGN.md, Components, controllers and models). Their only Done-means work is rule 3 (custom properties instead of inline styles).
- No wave 1 unit has a GIN binding (no bindPresenterWidget or bind lines name them), so nothing goes from the GIN modules.
- Record fixes: unit 87's template (stroom/main/client/view/Spinner.ui.xml) belongs to unit 486; ValueSpinnerCell has none. ToolTipCell (unit 61) is unused and never closes its <div>.

## `ToolbarButton`

widget, dumb; complexity 2, effort S; used by 57.

- **Component:** `stroom.widget.button.client.ToolbarButton`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it is a single <button> whose only fixed markup is its root block class; its content is the SvgImage given by svg="...")`
- **Names:** No clash. Keeps its name. ToolbarToggleButton merges nothing; VisToolbarButton (another area) is a different class.

**Stays in Java:** ToolbarButton.java:69 - root block class toolbar-button (single-element widget convention, see cssNotes); ToolbarButton.java:115-130 - aria-label and aria-keyshortcuts are built from the title and the user's key binding; ToolbarButton.java:143-154 - toolbar-button__icon and aria-hidden go on the SVG of the SvgImage given; ToolbarButton.java:180-182 - aria-disabled is state; ToolbarToggleButton.java:38 - aria-pressed is state; ButtonView/ToggleButtonView implements stay until callers are off them (rule 5).

## `Button`

widget, tidy; complexity 2, effort S; used by 43.

- **Component:** `stroom.widget.button.client.Button`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/button/client/Button.ui.xml (new; root <button>, UiBinder<ButtonElement, Button.Parts> bound in the super(...) argument through a static binder and a small Parts holder, since ButtonBase only takes its element in its constructor; or Button extends FocusWidget and uses setElement as CurveTabSelector does)`
- **Goes:** ButtonView.java and ToggleButtonView.java do NOT go in this wave: ButtonView has fan-in 60 through presenters' view interfaces (rule 5); they go with the last of those
- **Names:** No clash for stroom.widget.button.client.Button (GWT's com.google.gwt.user.client.ui.Button is external, imported explicitly where used). Keeps its name.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| Button.java:158-159 | `ripple.getStyle().setLeft(x, Unit.PX); ripple.getStyle().setTop(y, Unit.PX)` | dynamic | Set --ripple__left and --ripple__top (px) on the ripple element with a shared custom-property setter (see cssNotes), and in Button.css:639 .ripple add left: var(--ripple__left); top: var(--ripple__top) |

| Into the template | What | Detail |
| --- | --- | --- |
| Button.java:76 | css-class | <button class="Button Button--base Button--contained has-text"> (the duplicated 'Button' in the string can go) |
| Button.java:78-82 | structure | <span ui:field="backgroundText" class="Button__backgroundText" aria-hidden="true"/> |
| Button.java:84-86 | structure | <div ui:field="rippleContainer" class="ripple-container"/> |
| Button.java:88-100 | structure | <div class="Button__content"><span class="Button__spinner"><span class="Button__spinner-ring" aria-hidden="true"/></span>...</div> |
| Button.java:102-106 | aria | <span ui:field="icon" class="Button__icon svgIcon" aria-hidden="true" focusable="false"/> |
| Button.java:108-110 | structure | <span ui:field="text" class="Button__text"/> |
| Button.java:112 | text | Default caption 'Close' in both text spans (callers override it with text=...) |

**Stays in Java:** Button.java:136-149 - Button--down is pointer state; Button.java:156-166 - each ripple div is made per press (runtime); Button.java:173-195 - Button--spinning, Button--loading and aria-busy are state; Button.java:199-205 - Button--disabled is state; Button.java:209-217 - text and icon (Button__icon--visible + SVG) are set by callers; Button.java:224-244 - TaskMonitorFactory goes to setRefreshing/setLoading plus the shared adapter (Before the waves), not template matter.

## `TickBoxCell`

cell, dumb; complexity 2, effort S; used by 19.

- **Component:** `stroom.cell.tickbox.client.TickBoxCell`
- **Controller:** `none`
- **Template:** `none` → `none (a grid cell: SafeHtmlTemplates at TickBoxCell.java:371-373 stay)`
- **Names:** No clash. Keeps its name.

**Stays in Java:** TickBoxCell.java:324-337 - title (Ticked/Half-Ticked/Not Ticked), aria-checked and aria-readonly follow the row's state; TickBoxCell.java:127 - aria-label is per row (data).

## `AbstractSettingsWidget`

widget, tidy; complexity 1, effort S; used by 17.

- **Component:** `stroom.planb.client.view.AbstractSettingsWidget`
- **Controller:** `none`
- **Template:** `none` → `none (an abstract base with no markup; its 17 subclasses have their own templates)`
- **Goes:** The ChangeUiHandlers field and setUiHandlers/getUiHandlers (AbstractSettingsWidget.java:25-33) go, replaced by setOnChange(Runnable); ChangeUiHandlers itself (stroom.document.client.event, 64 users) stays; The abstract asWidget() (:35) goes if it extends stroom.widget.component.client.Component (subclasses call initWidget instead of holding a widget field)
- **Names:** Not merged: an Abstract... base usually merges into its one or two subclasses, but this one has 17 (GeneralSettingsWidget, RetentionSettingsWidget, SnapshotSettingsWidget, CondenseSettingsWidget, PublishingSettingsWidget, SharedFileStoreSettingsWidget and 11 key/value schema widgets). It keeps its name as their shared base, extending the thin Component.

**Stays in Java:** Nothing: no markup, no styles.

## `RefreshButton`

widget, tidy; complexity 2, effort S; used by 13.

- **Component:** `stroom.data.pager.client.RefreshButton`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it extends ToolbarButton, a ButtonBase whose <button> is made in ToolbarButton's constructor, and its four icons are SvgImage SVGs that a template can't declare)`
- **Names:** No clash. Keeps its name and package (stroom.data.pager.client was not moved by the merge).

**Stays in Java:** RefreshButton.java:65 - root block class refresh-button (single-element convention); RefreshButton.java:71-90 - the four icons (refresh, circle, spinning, pause) with refresh-button__icon--<name> and aria-hidden on each SVG; RefreshButton.java:68,160-167 - title (Refresh / Resume Update / Pause Update / Not Updating) follows state; RefreshButton.java:105,119,150 - --pausable, --paused, --refreshing are state; RefreshButton.java:61-64 - static Action.REFRESH command registration stays (app-wide, idempotent); RefreshButton.java:124-138 - task counting goes to setRefreshing plus the shared adapter.

## `LinkButton`

widget, tidy; complexity 1, effort S; used by 11.

- **Component:** `stroom.widget.button.client.LinkButton`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it is a single <button> whose only fixed markup is its root class; text and ariaLabel come from its users' templates)`
- **Names:** No clash. Keeps its name.

**Stays in Java:** LinkButton.java:45 - root class linkButton (single-element convention); LinkButton.java:59-75 - aria-label and aria-haspopup are setters used as template attributes (ariaLabel=, opensDialog=), already declarable; Add setOnClick(Runnable) beside ClickHandler (rule 5).

## `ExpanderCell`

cell, dumb; complexity 1, effort S; used by 11.

- **Component:** `stroom.cell.expander.client.ExpanderCell`
- **Controller:** `none`
- **Template:** `none` → `none (a grid cell: SafeHtmlTemplates at ExpanderCell.java:145-152 stay)`
- **Names:** No clash (stroom.util.shared.Expander is its value type, a different name). Keeps its name.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ExpanderCell.java:108,147,150 | `SafeStylesUtils.fromTrustedString("padding-left:" + padding + "px;") into style="{1}"` | dynamic | style holds only --expander-cell__indent (padding + "px"); ExpanderCell.css:17 .expanderCell gets padding-left: var(--expander-cell__indent, 0) |

**Stays in Java:** ExpanderCell.java:114-125 - icon, active class and title (Expand/Collapse) follow the row's state.

## `SpinnerLarge`

widget, dumb; complexity 1, effort S; used by 8.

- **Component:** `stroom.widget.spinner.client.SpinnerLarge`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/spinner/client/SpinnerLarge.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/spinner/client/SpinnerLarge.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/resources/stroom/widget/spinner/client/SpinnerLarge.ui.xml (moved beside the Java)`
- **Names:** No clash. Keeps its name.

| Into the template | What | Detail |
| --- | --- | --- |
| SpinnerLarge.java:57 | text | Initial status text 'Loading...' in <span ui:field="status" class="sr-only">Loading...</span> (it starts shown) |

**Stays in Java:** SpinnerLarge.java:96-103 - status text put in and cleared as it shows and hides (live region), busy-spinner--active is state; SpinnerLarge.java:110-114 - busy-spinner--soft is a setter (soft="true" in templates); SpinnerLarge.java:67-86 - task counting goes to the shared adapter (Before the waves).

## `ActionCell`

cell, dumb; complexity 1, effort S; used by 7.

- **Component:** `stroom.cell.info.client.ActionCell`
- **Controller:** `none`
- **Template:** `none` → `none (a grid cell rendered with SafeHtml via RowActions.button; SafeHtmlTemplates stay for cells)`
- **Names:** No clash. Keeps its name.

**Stays in Java:** ActionCell.java:48,58 - default name 'Actions...' is a CellIcon constructor argument; ActionCell.java:118-125 - per-row name from nameFunction (data); classes svgCell-button/svgCell-icon in SafeHtml.

## `IconBox`

widget, tidy; complexity 1, effort S; used by 4.

- **Component:** `stroom.svg.client.IconBox`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/svg/client/IconBox.ui.xml (new; IconBox becomes a Composite, best the thin stroom.widget.component.client.Component so setReadonly becomes the inherited final setReadOnly + onReadOnlyChange)`
- **Goes:** Nothing deleted; setReadonly(boolean) (IconBox.java:91) is replaced by setReadOnly, and its 4 callers (DateTimeBox.java:172,184, TimeBox.java:128,139, BaseSelectionBox.java:293,314, ScheduleBox) move in the same commit
- **Names:** No clash: IconBox is the only class of that name. Keeps its name and package (widget).

| Into the template | What | Detail |
| --- | --- | --- |
| IconBox.java:48 | css-class | Root block class: <g:HTMLPanel styleName="icon-box"> |
| IconBox.java:62-63 | structure | The icon holder is fixed markup, not a new SimplePanel(): <div ui:field="icon" class="icon-box__icon" aria-hidden="true"></div> after a <span ui:field="fieldSlot"/> that setWidget replaces with the field (HTMLPanel.addAndReplaceElement), so the DOM order (field, then icon) is unchanged |
| IconBox.java:67 | aria | aria-hidden="true" on the icon div |
| IconBox.java:77 | css-class | icon-box--drop-down can be a template-settable modifier (a dropDown="true" setter), though callers today set it through setDropDownWidget |

**Stays in Java:** IconBox.java:59 - icon-box__field is added to the caller's input, found at runtime (findField); IconBox.java:64-66 - the SVG is the SvgImage passed in (data); icon-box__image must go on the SVG element itself, so it is set after inserting it; IconBox.java:93-97 - icon-box--readonly is state; IconBox.java:82-88 - the click goes out through a new setOnIconClick(Runnable) beside addClickHandler (rule 5: ClickHandler stays until its 4 callers move).

## `FabButton`

widget, tidy; complexity 1, effort S; used by 4.

- **Component:** `stroom.widget.button.client.FabButton`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/button/client/FabButton.ui.xml (new; root <div>, bound through a static binder and Parts holder inside super(...), as for Button)`
- **Names:** No clash. Keeps its name.

| Into the template | What | Detail |
| --- | --- | --- |
| FabButton.java:51 | css-class | <div class="stroom-button-fab"> |
| FabButton.java:53-56 | structure | <button ui:field="face" class="face" type="button"> |
| FabButton.java:107 | structure | The icon wrapper <div ui:field="icon" class="svgIcon" aria-hidden="true"/> inside the face; only the SvgImage's class and SVG are added in setIcon |

**Stays in Java:** FabButton.java:84-89 - face--disabled and aria-disabled are state; FabButton.java:98-102 - aria-label follows the title callers give; FabButton.java:107 - svgImage.getClassName() and its SVG (data); FabButton.java:185-189 - face--down is pointer state; FabButton.java:66 - tabindex removal after attach (GWT FocusWidget behaviour).

## `LayeredCanvas`

widget, tidy; complexity 1, effort S; used by 4.

- **Component:** `stroom.widget.htree.client.LayeredCanvas`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because its layers are canvases made on demand by name, getLayer)`
- **Names:** No clash. Keeps its name. Its private constructor plus createIfSupported() stays (callers PipelineTreePanel.java:134, ExpressionTreePanel.java:85 handle null); a public no-arg constructor is only needed if it moves into a template.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| LayeredCanvas.java:36-38 | `style.setWidth(100, PX); style.setHeight(100, PX) on the root` | dynamic | Root gets class layered-canvas and the custom properties --layered-canvas__width / --layered-canvas__height (default 100px in the rule); .layered-canvas { width: var(--layered-canvas__width, 100px); height: var(--layered-canvas__height, 100px); } in a new layered-canvas.css (imported from app.css) |
| LayeredCanvas.java:54-56,59 | `setPosition(ABSOLUTE); setLeft(0); setTop(0); setOutlineStyle(NONE) on each layer` | static | New class layered-canvas__layer in layered-canvas.css: position: absolute; left: 0; top: 0; outline: none (no existing class does absolute 0,0 without also setting size; .max in stroom.css:793 is only width/height 100%) |
| LayeredCanvas.java:57-58 | `layer style.setWidth(width, PX); setHeight(height, PX)` | dynamic | layered-canvas__layer uses width: var(--layered-canvas__width, 100px); height: var(--layered-canvas__height, 100px), inherited from the root, so a layer needs no style of its own |
| LayeredCanvas.java:83-90 | `setSize: root and every layer setWidth/setHeight` | dynamic | setSize sets only --layered-canvas__width/--layered-canvas__height on the root (the layers inherit them); setCoordinateSpaceWidth/Height stay (canvas attributes, not style) |

**Stays in Java:** LayeredCanvas.java:113-116 - tabindex -1 and aria-hidden on each layer, made at runtime (after attach, as FocusWidget resets tabindex); LayeredCanvas.java:60-61,91-92 - canvas coordinate space is data.

## `SpinnerSmall`

widget, dumb; complexity 1, effort S; used by 3.

- **Component:** `stroom.widget.spinner.client.SpinnerSmall`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/spinner/client/SpinnerSmall.ui.xml (new; UiBinder<DivElement, SpinnerSmall> and setElement, CurveTabSelector pattern)`
- **Names:** No clash. Keeps its name.

| Into the template | What | Detail |
| --- | --- | --- |
| SpinnerSmall.java:54-57 | css-class | <div class="busy-spinner busy-spinner--small"> |
| SpinnerSmall.java:51-53 | structure | <div class="busy-spinner__outer"> |
| SpinnerSmall.java:47-48 | structure | <div ui:field="inner" class="busy-spinner__inner" title="Loading..."/> |
| SpinnerSmall.java:50 | text | title="Loading..." |

**Stays in Java:** SpinnerSmall.java:49 - the SVG (SvgImage.SPINNER_SPINNING) is inserted with SvgImageUtil.setSvgAsInnerHtml(inner, ...), as no template widget takes an SvgImage; SpinnerSmall.java:68-81 - busy-spinner--active and --spinning are state; SpinnerSmall.java:85-104 - task counting goes to the shared adapter.

## `SettingBlock`

widget, dumb; complexity 1, effort S; used by 3.

- **Component:** `stroom.annotation.client.SettingBlock`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/annotation/client/SettingBlock.ui.xml (new)`
- **Names:** No clash. Keeps its name. Its exception text at :124 says 'FormGroup' (copy-paste); fix to SettingBlock.

| Into the template | What | Detail |
| --- | --- | --- |
| SettingBlock.java:48,54,72 | structure | Root <g:FlowPanel ui:field="formGroupPanel" styleName="setting-block icon-colour__grey"> |
| SettingBlock.java:56-58 | structure | <g:SimplePanel ui:field="icon" styleName="setting-block-icon icon-colour__grey svgIcon"/> as the first child; the child setting is added after it (panel.add), replacing the clear-and-re-add in updateFormGroupPanel (:137-143) |

**Stays in Java:** SettingBlock.java:57 - the ELLIPSES_VERTICAL SVG is inserted in Java; SettingBlock.java:60,92-105 - role=button, tabindex, aria-label, aria-haspopup or aria-hidden follow the title (set by template attribute title=..., but some blocks have none); SettingBlock.java:117 - setting-block--readonly is state; SettingBlock.java:127 - allow-focus on whatever child it is given.

## `ValueSpinnerCell`

cell, dumb; complexity 3, effort S; used by 2.

- **Component:** `stroom.cell.valuespinner.client.ValueSpinnerCell`
- **Controller:** `none`
- **Template:** `none` → `none (a grid cell: SafeHtmlTemplates at ValueSpinnerCell.java:590-617 and STEPS_HTML at :65-70 stay). The wave record's template stroom-core-client/src/main/resources/stroom/main/client/view/Spinner.ui.xml is wrong: that file belongs to unit 486 (main.client.view.Spinner)`
- **Names:** ValueSpinnerCell: no clash. Its helper stroom.cell.valuespinner.client.Spinner (a stepping model, not a widget) shares its simple name with stroom.widget.valuespinner.client.Spinner and stroom.main.client.view.Spinner. Either merge it into the widget's Spinner (the review's note; they differ: the widget one has HandlerManager, minStep, delta, wrapValues) or rename it SpinStepper (no clash in all_simple_names.json).

**Stays in Java:** ValueSpinnerCell.java:67-68 - 'Decrease value'/'Increase value' titles are in static SafeHtml, fine for a cell; ValueSpinnerCell.java:421,480-483,499 - aria-valuenow, aria-invalid, aria-describedby and status text are state.

## `FindResultCell`

cell, dumb; complexity 1, effort S; used by 2.

- **Component:** `stroom.explorer.client.presenter.FindResultCell`
- **Controller:** `none`
- **Template:** `none` → `none (cell, SafeHtml)`
- **Names:** No clash. Keeps its name and package.

**Stays in Java:** FindResultCell.java:40-68 - all content is data.

## `QueryCell`

cell, tidy; complexity 1, effort S; used by 2.

- **Component:** `stroom.dashboard.client.query.QueryCell`
- **Controller:** `none`
- **Template:** `none` → `none (cell, SafeHtmlTemplates at QueryCell.java:60-71 stay)`
- **Names:** No clash. Keeps its name.

**Stays in Java:** QueryCell.java:48-56 - name, time and expression are data; classes are passed as template arguments (could be literals in the @Template, a tidy only).

## `AssignYourselfLink`

widget, tidy; complexity 1, effort S; used by 2.

- **Component:** `stroom.annotation.client.AssignYourselfLink`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/annotation/client/AssignYourselfLink.ui.xml (new; it becomes a widget, UiBinder<DivElement, AssignYourselfLink> and setElement, instead of a static helper over a g:Label)`
- **Goes:** The static init/runIfEnabled/update helpers (AssignYourselfLink.java:43-83) become instance methods setOnAssign(Runnable) and setState(alreadyAssigned, readOnly); the g:Label in AnnotationEditViewImpl.ui.xml:55 and ChangeAssignedToViewImpl.ui.xml:12 becomes <annotation:AssignYourselfLink ui:field="assignYourself"/>
- **Names:** No clash. Keeps its name; it changes from a final utility class to a dumb widget.

| Into the template | What | Detail |
| --- | --- | --- |
| AnnotationEditViewImpl.ui.xml:55; ChangeAssignedToViewImpl.ui.xml:12 | css-class | class="annotationAssignYourself" on the template root |
| AssignYourselfLink.java:45-46 | aria | role="button" tabindex="0" |
| AssignYourselfLink.java:47 | text | title="Assign this annotation to yourself" (initial) |
| AnnotationEditViewImpl.ui.xml:55; ChangeAssignedToViewImpl.ui.xml:12 | text | 'Assign Yourself' as the element's text |

**Stays in Java:** AssignYourselfLink.java:75-82 - disabled state and the title chosen by it (already assigned / no permission / assign) are state; AssignYourselfLink.java:48-55 - Enter/Space key handling.

## `TextFieldCell`

cell, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.cell.textfield.client.TextFieldCell`
- **Controller:** `none`
- **Template:** `none` → `none (a grid cell: its markup is the SafeHtmlTemplates Template, TextFieldCell.java:102-111, which stay for grid cells)`
- **Names:** No clash. Keeps its name and package. TextField (added by copy_and_paste) is a different class and name.

**Stays in Java:** TextFieldCell.java:104-110 - SafeHtml template with fixed classes text-field text-field--cell; per-row aria-label comes from data (nameForRow). Nothing to move..

## `PercentBarCell`

cell, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.cell.info.client.PercentBarCell`
- **Controller:** `none`
- **Template:** `none` → `none (grid cells: ColourSwatchCell, PercentBarCell, RedGreenTextCell, SvgStringCell and ToolTipCell render SafeHtml, which stays for cells)`
- **Goes:** `ToolTipCell.java is unused (no references outside itself); delete it when removals are allowed (removals wait for other in-flight work)`
- **Names:** No clash for any of the five cells. ToolTipCell.ToolTipCellValue is an inner class; no clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| PercentBarCell.java:83,108 | `SafeStylesUtils.forWidth(pct, Unit.PCT) into style="{1}"` | dynamic | style holds only --percent-bar-cell__width (SafeStylesUtils.fromTrustedNameAndValue("--percent-bar-cell__width", pct + "%")); PercentBarCell.css .percentBarCell-bar gets width: var(--percent-bar-cell__width) |
| ColourSwatchCell.java:40-42 | `<div class="colourSwatchCell-swatch" style="background-color:<colour>">` | dynamic | style="--colour-swatch-cell__background-color: <colour>" (validated colour, built as SafeStyles); ColourSwatchCell.css .colourSwatchCell-swatch gets background-color: var(--colour-swatch-cell__background-color) |

**Stays in Java:** PercentBarCell.java:81 - title is the percentage (data); PercentBarCell.java:91-98 - threshold class chosen from the value (state); RedGreenTextCell.java:48-54 - text and class chosen by value; ToolTipCell.java:38-42 - never closes its <div> (bug: append "</div>"), if it is kept at all.

## `FindInContentResultCell`

cell, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.explorer.client.presenter.FindInContentResultCell`
- **Controller:** `none`
- **Template:** `none` → `none (cell, SafeHtml)`
- **Names:** No clash. Keeps its name and package.

**Stays in Java:** FindInContentResultCell.java:41-109 - icon, sample, highlight, name, path, uuid and tags are data; 'Tags: ' prefix (:104) stays in the cell's Java.

## `AiChatCell`

cell, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.ai.client.AiChatCell`
- **Controller:** `none`
- **Template:** `none` → `none (cell, SafeHtml)`
- **Names:** No clash. Keeps its name; the DateTimeFormatter constructor argument is fine for a cell (made by its list, not a template).

**Stays in Java:** AiChatCell.java:41-50 - title and relative age are data.

## `Toolbar`

widget, dumb; complexity 2, effort S; used by 79.

- **Component:** `stroom.widget.button.client.Toolbar`
- **Controller:** `none`
- **Template:** `none` → `none for Toolbar and ToolbarRow (FlowPanel subclasses whose children come from their users' templates; only a root block class is fixed). ToolbarSeparator gets stroom-core-client-widget/src/main/java/stroom/widget/button/client/ToolbarSeparator.ui.xml (UiBinder<DivElement, ToolbarSeparator>, CurveTabSelector pattern)`
- **Names:** No clash for Toolbar, ToolbarRow, ToolbarSeparator or ButtonGroupFocus. Note: @UiConstructor Toolbar(String name) stays; a no-arg constructor plus setName could be added so GIN/templates without name work, but every template gives name.

| Into the template | What | Detail |
| --- | --- | --- |
| ToolbarSeparator.java:29-32 | structure | <div class="toolbar__separator" role="separator" aria-orientation="vertical"/> as ToolbarSeparator.ui.xml |

**Stays in Java:** Toolbar.java:57 - root block class toolbar (single-element convention); ToolbarRow.java:27 - root block class toolbar-row (single-element convention); ButtonGroupFocus.java:122-138 - role=toolbar, aria-label, aria-orientation and roving tabindex depend on how many buttons are shown (runtime); Toolbar.java:68-104 - button/toggle/refresh factories build from icon and name data.

## `FormGroup`

widget, dumb; complexity 3, effort S; used by 54.

- **Component:** `stroom.widget.form.client.FormGroup`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FormGroup.ui.xml (new; FormLabel, FormGroupHelpButton, DescriptionHTML, HelpHTML and ControlNames stay templateless)`
- **Names:** No clash for FormGroup, FormGroupHelpButton, FormLabel, DescriptionHTML, HelpHTML or ControlNames. All keep their names.

| Into the template | What | Detail |
| --- | --- | --- |
| FormGroup.java:118,175,181 | structure | Root <g:FlowPanel ui:field="formGroupPanel" styleName="form-group"> |
| FormGroup.java:121,176 | structure | <g:FlowPanel ui:field="labelPanel" styleName="form-group__label-row"> |
| FormGroup.java:119,177 | component-declaration | <f:FormLabel ui:field="formLabel" addStyleNames="form-group__label"/> inside the label row, shown/hidden with setVisible rather than removed (updateLabelPanel, :797-805) |
| FormGroup.java:120; FormGroupHelpButton.java:48 | component-declaration | <f:FormGroupHelpButton ui:field="helpButton" title="Click for help"/> inside the label row (title moves from the Java constructor to the template) |
| FormGroup.java:122,178 | structure | <g:FlowPanel ui:field="descriptionPanel" styleName="form-group__description-row" visible="false"/> |
| FormGroup.java:123,168 | structure | <g:Label ui:field="feedbackLabel" styleName="form-group__feedback" visible="false"/>, with the child inserted before it |
| FormGroup.java:126,172-173 | structure | <g:Label ui:field="helpDescription" styleName="form-group__help-text" visible="false"/> last |
| FormGroup.java:746 | css-class | form-group__description on DescriptionHTML could be its own constructor class (setStyleName in DescriptionHTML) rather than set by FormGroup |

**Stays in Java:** FormGroup.java:170,169 - unique ids for the label and help description (DOM.createUniqueId), referenced by aria-labelledby/aria-describedby; FormGroup.java:229-281,385 - aria-labelledby, role=group, aria-label per control, aria-required and aria-keyshortcuts are wired to whatever control it holds (runtime); FormGroup.java:322-334 - feedback text and its sr-only full form are data; FormGroup.java:522,607-609 - form-group--inline-tick and description-row--disabled are state; FormGroup.java:727 - allow-focus added to whatever child it is given; FormGroup.java:440 - reads style.display to skip hidden controls (a read, not an inline style); FormGroupHelpButton.java:46 and FormLabel.java:33 - root classes (single-element convention); FormGroupHelpButton.java:57-68 icon SVG; FormLabel.java:42-75 for, text and modifiers are data/state; Replacing add/remove (updateFormGroupPanel, :783-795) with fixed parts and setVisible must be checked with a full screenshot and a11y diff (fan-in 54).

## `QuickSettings`

widget, dumb; complexity 1, effort S; used by 2.

- **Component:** `stroom.widget.button.client.QuickSettings`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/button/client/QuickSettingsGroup.ui.xml (new, for the inner Group via @UiTemplate on a Group Binder); QuickSettings itself stays built in Java (root class only, groups are data)`
- **Names:** No clash. Keeps its name; QuickSettings.Group stays an inner class.

| Into the template | What | Detail |
| --- | --- | --- |
| QuickSettings.java:66 | css-class | <g:FlowPanel styleName="quick-settings__group"> as the group template root |
| QuickSettings.java:72 | aria | role="group" on the group root (an HTMLPanel or a div root so the attribute can be written) |
| QuickSettings.java:67-68 | structure | <g:Label ui:field="title" styleName="quick-settings__title"/> (wordWrap=false) |

**Stays in Java:** QuickSettings.java:37 - root class quick-settings (single-element convention); QuickSettings.java:67 - group title text is given by the caller; QuickSettings.java:69-73 - the label id (DOM.createUniqueId) and aria-labelledby are generated at runtime; QuickSettings.java:82-84 - presets are data: new LinkButton(name) with quick-settings__setting; switch addClickHandler to LinkButton.setOnClick once it exists.

## `Spinner`

widget, dumb; complexity 1, effort S; used by 2.

- **Component:** `stroom.main.client.view.TaskSpinner`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/main/client/view/Spinner.ui.xml` → `stroom-core-client/src/main/java/stroom/main/client/view/TaskSpinner.ui.xml`
- **Goes:** `stroom-core-client/src/main/resources/stroom/main/client/view/Spinner.ui.xml (moved and renamed)`; stroom-core-client/src/main/java/stroom/main/client/view/Spinner.java (renamed to TaskSpinner.java); callers MainToolbar.java:37,49, MainToolbar.ui.xml:7, MainPresenter.java:29,205 move in the same commit
- **Names:** Clash: three classes are called Spinner (this one, stroom.cell.valuespinner.client.Spinner and stroom.widget.valuespinner.client.Spinner). Renamed TaskSpinner (no clash in all_simple_names.json): it is the main toolbar's spinner showing the app's running tasks, and clicking it opens the task manager. If the rename is not wanted, it keeps Spinner and its template moves to stroom-core-client/src/main/java/stroom/main/client/view/Spinner.ui.xml.

| Into the template | What | Detail |
| --- | --- | --- |
| Spinner.java:42-45 | component-declaration | <sp:SpinnerLarge ui:field="spinner" soft="true" visible="false"/> in the template, instead of new SpinnerLarge() appended by element (which also left it out of the widget tree) |
| Spinner.ui.xml:3; MainToolbar.ui.xml:7 | css-class | Root class: the template's 'spinner' has no CSS rule and MainToolbar replaces it with styleName="spinner mainViewImpl-spinner"; give the root styleName="task-spinner", rename .mainViewImpl-spinner (components.css:874) to .task-spinner, and drop styleName from MainToolbar.ui.xml:7 |

**Stays in Java:** Spinner.java:47-77 - the delayed start timer and spinning state; Spinner.java:79-85 - click/double-click out: add setOnClick(Runnable) beside addClickHandler (MainPresenter.java:135 uses it).

## `DocumentTypeCell`

cell, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.explorer.client.presenter.DocumentTypeCell`
- **Controller:** `none`
- **Template:** `none` → `none (a list cell rendered with SafeHtml, which stays for cells)`
- **Names:** No clash. Keeps its name and package (it is a cell, not a presenter, so not moved to client.component). DocumentTypeSelectionModel stays with it.

**Stays in Java:** DocumentTypeCell.java:40-60 - commented-out onBrowserEvent can go; DocumentTypeCell.java:65-76 - icon, type name and tick state are data.

## `ColumnValueCell`

cell, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.dashboard.client.table.ColumnValueCell`
- **Controller:** `none`
- **Template:** `none` → `none (cell, SafeHtml via HtmlBuilder)`
- **Names:** No clash (stroom.dashboard.shared.ColumnValue is its value type, a different name). Keeps its name.

**Stays in Java:** ColumnValueCell.java:43-50 - tick state and value are data.

## `FindAnnotationCell`

cell, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.annotation.client.FindAnnotationCell`
- **Controller:** `none`
- **Template:** `none` → `none (cell, SafeHtml via HtmlBuilder)`
- **Names:** No clash. Keeps its name. Lozenge and DurationLabel are SafeHtml helpers it calls, not components to declare.

**Stays in Java:** FindAnnotationCell.java:44-99 - all content is data; fixed words '-', 'opened', 'assigned to' (:76,82,92,94) stay in the cell's Java.

## `SettingsGroup`

widget, dumb; complexity 1, effort S; used by 9.

- **Component:** `stroom.planb.client.view.SettingsGroup`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/planb/client/view/SettingsGroup.ui.xml (new)`
- **Names:** No clash. Keeps its name. Used by planb and floormap templates (TemporalStateSettingsViewImpl.ui.xml, FloorMapEventStoreSettingsViewImpl.ui.xml, ...).

| Into the template | What | Detail |
| --- | --- | --- |
| SettingsGroup.java:31,36 | structure | Root <g:FlowPanel ui:field="settingsGroupPanel" styleName="settings-group"> |
| SettingsGroup.java:32,37 | component-declaration | <f:FormLabel ui:field="formLabel" addStyleNames="settings-group-label"/> (FormLabel is from the FormGroup unit, same wave); the child is added after it, replacing the clear-and-re-add in updateFormGroupPanel (:63-69) |

**Stays in Java:** SettingsGroup.java:43 - label text comes from its users' templates (label=...); SettingsGroup.java:53 - allow-focus on whatever child it is given.

