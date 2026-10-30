# Wave 2: High fan-in input and layout widgets

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

Give the shared value widgets and layout widgets setOnChange callbacks and one read-only setter, alongside their existing HasValue/ValueChangeEvent API, and make them usable in templates (TimeZoneBox loses its injected Binder; DropDownBox keeps one disabled state).

## CSS and template notes for the wave

- Cross-cutting, decide before wave 2: UiBinder can't write role, aria-*, tabindex, type or placeholder on a g: widget element (the only ways in templates today are plain HTML in a g:HTMLPanel, see SpinnerLarge.ui.xml, or a typed setter, as TickBox.setAriaLabel and PasswordBox.setPlaceholder). This wave uses three ways: (1) a template whose root is an element (UiBinder<DivElement/InputElement, X> with setElement) for widgets that extend Widget (ColourBox, RadioGroup, FloorMapIconPicker); (2) plain elements in a g:HTMLPanel for parts that need no widget (ValueSpinner arrows/feedback/status, PasswordBox status, QuickFilter description/error); (3) small shared widgets with setters for the rest. Proposed names, all free in all_simple_names.json: stroom.widget.component.client.AriaPanel (a FlowPanel with setRole, setTabIndex, setAriaLabel, setAriaHasPopup, setAriaHidden, setAriaLive) for DropDownBox's root and SelectionBox's renderBox; stroom.widget.form.client.TextInput (a TextBox with setType, setPlaceholder, setRole, setAriaHasPopup, setAriaExpanded) for the inputs in QuickFilter, ValueSpinner and BaseSelectionBox.
- Add setAriaLabel(String) (template attribute ariaLabel) to ValueSpinner and BaseSelectionBox, as TickBox has, so that AccessibleName.set(...) calls in DurationPicker.java:76-77 and DefaultMonthSelector.java:116,139 become template attributes.
- IconBox (wave 1) needs a template form of setDropDownWidget (e.g. @UiChild or a dropDown="true" attribute) before BaseSelectionBox can declare it.
- Hiding: there is no Stroom utility hidden class in ui/css. UIObject.setVisible (GWT's own inline display:none) isn't counted as an inline style; SplitPane's own getStyle().setDisplay calls become BEM modifiers (split-pane__pane--hidden, split-pane__splitter--hidden) in split-pane.css.
- Reused classes in this wave: sr-only (spinner.css:126), allow-focus, stroom-control (stroom.css:576), max (stroom.css:793), form (form.css:42), days (components.css:1655), pager-* (components.css:294-336). New classes go in the block's own CSS file: selection-box.css, split-pane.css, xsdbrowser.css, editor.css (scrollbar-metrics), stroom-histogram.css, pathways.css, tick-box.css only if word wrap is kept.
- Custom property naming follows the existing --block__thing form (e.g. --split-pane__splitter-size): --split-pane__tracks, --xsd-node-grid__columns, --stroom-histogram__tooltip-left, --trace-histogram__bar-width/-index/-height.
- Record corrections: Spinner.ui.xml (stroom-core-client/src/main/resources/stroom/main/client/view) belongs to stroom.main.client.view.Spinner (MainToolbar), not the ValueSpinner unit; DropDownBox and ValueSpinner don't declare FormGroup (they look up their parent FormGroup); DurationPicker should declare ValueSpinner and SelectionBox (same wave, so convert after them); PasswordBox and QuickFilter should declare Toolbar and ToolbarButton; TraceHistogramChart has one inline style (Attribute.style, TraceHistogramChart.java:134) the record counted as 0; DropDownBox's template is already beside its Java.

## `TickBox`

widget, tidy; complexity 2, effort M; used by 67.

- **Component:** `stroom.widget.tickbox.client.view.TickBox`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it extends ButtonBase, which needs its root element in super(...) before a UiBinder can bind `this`; the three children are fixed and stay as constants)`
- **Goes:** HasWordWrap implementation: getWordWrap/setWordWrap (TickBox.java:99, 302-304, 474-478) have no callers (the wordWrap template uses are on g:Label), so drop them with their inline style; `Keep ValueChangeEvent/HasValue beside a new setOnChange(Consumer<Boolean>) until its ~67 callers move (rule 5)`; `setEnabled stays beside setReadOnly only if disabled is kept as a distinct state`
- **Names:** Keeps its name and package; unique in all_simple_names.json.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| TickBox.java:303 | `getElement().getStyle().getWhiteSpace() in getWordWrap()` | static | Deleted with HasWordWrap (no callers). If wrapping control is wanted later: modifier .tick-box--nowrap { white-space: nowrap; } in tick-box.css |
| TickBox.java:475 | `getElement().getStyle().setWhiteSpace(wrap ? NORMAL : NOWRAP)` | static | Deleted with HasWordWrap (no callers); else .tick-box--nowrap in tick-box.css |

**Stays in Java:** TickBox.java:123-136 label root, input, mark and label spans with tick-box, tick-box__input, __mark, __label: ButtonBase(Element) takes the root in super(), so no UiBinder owner yet; fixed, kept as BLOCK constants; TickBox.java:130 TICK_SVG inner HTML: shared with TickBoxCell (public constant TICK_SVG); TickBox.java:139 unique id: generated; TickBox.java:165 aria-readonly: state; TickBox.java:231 label text and :248 aria-label: set by callers' templates (label=, ariaLabel= attributes already work); TickBox.java:391 value attribute: data.

## `PasswordBox`

widget, tidy; complexity 2, effort S; used by 8.

- **Component:** `stroom.widget.form.client.PasswordBox`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/form/client/PasswordBox.ui.xml (new)`
- **Goes:** `getInput() (PasswordBox.java:107) once callers stop reaching into the PasswordTextBox`; `setFocus(boolean) in favour of focus()`
- **Names:** Keeps its name and package; unique. The record's empty 'declares' is wrong: it builds a Toolbar and ToolbarButton (wave 1) in Java, so declare them.

| Into the template | What | Detail |
| --- | --- | --- |
| PasswordBox.java:82-87 | structure | root <g:FlowPanel styleName="password-box"> with input, end, status (password-box.css:22) |
| PasswordBox.java:69 | css-class | <g:PasswordTextBox ui:field="input" styleName="password-box__input"/> |
| PasswordBox.java:75-76 | structure | <g:FlowPanel ui:field="end" styleName="password-box__end"> holding the toolbar |
| PasswordBox.java:71-73 | component-declaration | <b:Toolbar name="Password" addStyleNames="password-box__toolbar"><b:ToolbarButton ui:field="revealButton" svg="EYE" title="Show password"/></b:Toolbar> |
| PasswordBox.java:79-80 | aria | <div ui:field="status" class="password-box__status" aria-live="polite"/> (HTMLPanel child) or a Label with an AriaPanel-style setter |

**Stays in Java:** PasswordBox.java:112 placeholder: set by callers (already a setter usable as a template attribute); PasswordBox.java:127-130 badge holder and password-box--with-badge: only when a badge is given; PasswordBox.java:145 status text, :156 type text/password, :158-164 icon and title Show/Hide + secretName: state and caller data.

## `Editor`

widget, tidy; complexity 3, effort S; used by 4.

- **Component:** `stroom.editor.client.view.Editor`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because its whole widget is the third-party AceEditor, configured through Ace's API; a template would only hold <ace:AceEditor styleName="code-editor__ace"/>, which is optional)`
- **Names:** Keeps its name and package; Editor is unique in the repo's classes (GWT's com.google.gwt.editor.client.Editor is a library interface, not in all_simple_names.json). StroomAceThemes and ScrollbarMetrics are helpers and keep their names. Ace sources under edu/ycp/cs/dh/acegwt must not be edited.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ScrollbarMetrics.java:51 | `inner.setSize("200px", "200px")` | static | New .scrollbar-metrics__inner { width: 200px; height: 200px; } in editor.css |
| ScrollbarMetrics.java:53 | `outer.setSize("100px", "100px")` | static | New .scrollbar-metrics { width: 100px; height: 100px; ... } in editor.css |
| ScrollbarMetrics.java:54 | `outer.getElement().getStyle().setPosition(Position.FIXED)` | static | .scrollbar-metrics { position: fixed; } |
| ScrollbarMetrics.java:55-56 | `setLeft(-1000, PX) / setTop(-1000, PX)` | static | .scrollbar-metrics { left: -1000px; top: -1000px; } |
| ScrollbarMetrics.java:57 | `setOverflow(Overflow.SCROLL)` | static | .scrollbar-metrics { overflow: scroll; } |

| Into the template | What | Detail |
| --- | --- | --- |
| Editor.java:169 | css-class | code-editor__ace (editor.css:29); only if Editor gets a one-line template declaring AceEditor (no-arg constructor at AceEditor.java:306) |

**Stays in Java:** Editor.java:176-187 removeGutterTabStop: rewrites Ace's own generated gutter element at runtime; Editor.java:332 aria-readonly on Ace's text input: state; destroy() tied to the owner's disposal.

## `ColourBox`

widget, tidy; complexity 1, effort S; used by 3.

- **Component:** `stroom.widget.colour.client.ColourBox`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/colour/client/ColourBox.ui.xml (new; element root: UiBinder<InputElement, ColourBox>, then setElement(BINDER.createAndBindUi(this)), as it extends Widget)`
- **Goes:** `Keep HasValue/ValueChangeEvent beside setOnChange(Consumer<String>) until callers move`
- **Names:** Keeps its name and package; unique.

| Into the template | What | Detail |
| --- | --- | --- |
| ColourBox.java:44-46 | structure | <input type="color" class="colour-box" value="#000000"/> (colour-box.css:25) |

**Stays in Java:** ColourBox.java:47 the value: data (the default can move into the template as value=); disabled/readOnly: state.

## `XSDDisplay`

widget, dumb; complexity 3, effort S; used by 1.

- **Component:** `stroom.widget.xsdbrowser.client.view.XSDDisplay`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/view/XSDDisplayBox.ui.xml (new, for the fixed box chrome, merging XSDDisplayBoxTitle); XSDDisplay itself none (built in Java because the diagram is drawn from the parsed schema)`
- **Goes:** `XSDDisplayBoxTitle.java merged into XSDDisplayBox (and its template)`; XSDDisplayBox width/height constructor parameters and outerBox.setWidth/setHeight (XSDDisplayBox.java:31-32, 55-60): every caller passes null (XSDDisplay.java:107-108,124-125,138-139,156-157,220-221, ...); `XSDNodeGrid.setCellStyle/setRowStyle (XSDNodeGrid.java:92-105), replaced by class setters`
- **Names:** Keeps its name and package; XSDDisplay, XSDDisplayBox, XSDNodeGrid, XSDNodeLabel, ClickPanel are unique. XSDDisplayBoxTitle is merged into XSDDisplayBox rather than kept.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| XSDDisplay.java:476 | `fp.getElement().getStyle().setPosition(Position.RELATIVE)` | static | New class .xsdBrowser .xsdBrowser-occurrence { position: relative; } in xsdbrowser.css (positions the absolute .occuranceLabel) |
| XSDDisplayBox.java:56 | `outerBox.setWidth(width)` | static | Removed: every caller passes null |
| XSDDisplayBox.java:59 | `outerBox.setHeight(height)` | static | Removed: every caller passes null |
| XSDDisplayBoxTitle.java:42 | `lblTitle.getElement().getStyle().setColor("white")` | static | color: white on .xsdBrowser .boxTitle-row (xsdbrowser.css:70) or a new .xsdBrowser .boxTitle__text |
| XSDDisplayBoxTitle.java:69-75 | `setCursor(POINTER / DEFAULT) on layout, image and label (6 calls)` | static | Modifier .xsdBrowser .boxTitle--selectable { cursor: pointer; } (children inherit) in xsdbrowser.css, added when node != null; default needs no rule |
| XSDNodeGrid.java:46 | `setProperty("gridTemplateColumns", "repeat(" + columnCount + ", auto)")` | dynamic | --xsd-node-grid__columns: <n> on the grid; .xsdBrowser .xsdBrowser-nodeGrid { grid-template-columns: repeat(var(--xsd-node-grid__columns), auto); } (xsdbrowser.css:136) |
| XSDNodeGrid.java:93 via XSDDisplay.java:300 | `setCellStyle(row, 0, "paddingLeft", "5px")` | static | Cells of column 0 get .xsdBrowser-nodeGrid__cell--start { padding-left: 5px; } (set in ensureRows, which knows the column) |
| XSDNodeGrid.java:93 via XSDDisplay.java:301 | `setCellStyle(row, last, "paddingRight", "5px")` | static | .xsdBrowser-nodeGrid__cell--end { padding-right: 5px; } |
| XSDNodeGrid.java:93 via XSDDisplay.java:305-306 | `setRowStyle(0, "paddingTop", "3px") / (last, "paddingBottom", "3px")` | static | addRowStyleName(row, "xsdBrowser-nodeGrid__cell--top" / "--bottom") with padding-top/bottom: 3px in xsdbrowser.css |
| XSDNodeGrid.java:93 via XSDDisplay.java:607 | `setRowStyle(last, "borderBottom", "1px solid black")` | static | .xsdBrowser-nodeGrid__cell--separator { border-bottom: 1px solid black; } in xsdbrowser.css (keep black so screenshots match) |

| Into the template | What | Detail |
| --- | --- | --- |
| XSDDisplayBox.java:39-52 | structure | XSDDisplayBox.ui.xml: <g:FlowPanel styleName="outerBox"><g:FlowPanel styleName="box"> title, then <g:ScrollPanel ui:field="content" styleName="boxContent"/> |
| XSDDisplayBoxTitle.java:38-50 | structure | title row in the same template: <v:ClickPanel ui:field="title" styleName="boxTitle"><g:FlowPanel styleName="boxTitle-row"><g:SimplePanel ui:field="image" styleName="marginRight"/><g:Label ui:field="titleText" wordWrap="false"/> |
| XSDDisplayBox.java:34 | css-class | boxTitle primary style name |
| XSDDisplayBoxTitle.java:41, XSDNodeLabel.java:36 | aria | unselectable="on" is redundant with .xsdBrowser * { user-select: none } (xsdbrowser.css:17-27): drop rather than move |

**Stays in Java:** XSDDisplay.java:84-196, 251-600 the diagram: boxes, columns (xsdBrowser-column, --centred), grids, labels, brackets, super arrows and images are made per schema node; XSDDisplayBoxTitle.java:39 image class and :40 title: per node; XSDDisplayBox.java:45-47 content, boxTitle-noContent, and :79-81 boxTitle-selected: data and selection state; XSDConstraintDisplay.java:62-77 restriction type and values: from the schema; XSDNodeLabel.java:34-35 label text and nodeLabel class: per node; XSDDisplay.java:661 image titles: per type.

## `LineColInput`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.widget.linecolinput.client.LineColInput`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/linecolinput/client/LineColInput.ui.xml (new)`
- **Goes:** `getTextBox() (LineColInput.java:82) once callers use a new setOnChange; keep until then`
- **Names:** Keeps its name and package; unique.

| Into the template | What | Detail |
| --- | --- | --- |
| LineColInput.java:51-53 | structure | <g:FlowPanel styleName="line-col-input"> (linecolinput.css:23) |
| LineColInput.java:49 | css-class | <g:TextBox ui:field="valueBox" styleName="line-col-input__field allow-focus"/> |

**Stays in Java:** LineColInput.java:46-47 key handlers and validation: behaviour; Value text: data.

## `RadioGroup`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.widget.form.client.RadioGroup`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/form/client/RadioGroup.ui.xml (new; element root UiBinder<DivElement, RadioGroup>, setElement as it extends Widget)`
- **Goes:** `Keep HasValue<T>/ValueChangeEvent beside setOnChange until its one caller moves, then drop`
- **Names:** Keeps its name and package; unique.

| Into the template | What | Detail |
| --- | --- | --- |
| RadioGroup.java:61-63 | structure | <div class="radio-group" role="radiogroup"/> (radio-button.css:64) |

**Stays in Java:** RadioGroup.java:84-100 each option's label, radio input, span, classes, title and text: added per item by addItem, from data; RadioGroup.java:117 aria-label from setAccessibleName: caller's (already a template attribute accessibleName=).

## `Histogram`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.widget.histogram.client.Histogram`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/widget/histogram/client/Histogram.ui.xml (new)`
- **Goes:** `setClickHandler(Consumer<Double>) renamed setOnClick`
- **Names:** Keeps its name and package; Histogram, HistogramLayout, HistogramDataModel are unique.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| Histogram.java:395 | `tooltip.getElement().getStyle().setLeft(clampedX, Unit.PX)` | dynamic | --stroom-histogram__tooltip-left: <n>px on the tooltip; .stroom-histogram-tooltip { left: var(--stroom-histogram__tooltip-left, 0); } in stroom-histogram.css:69 (replacing left: 0) |

| Into the template | What | Detail |
| --- | --- | --- |
| Histogram.java:94-95 | structure | root <g:FlowPanel styleName="stroom-histogram-container"> |
| Histogram.java:112-116 | structure | <g:FlowPanel ui:field="wrapper" styleName="stroom-histogram-wrapper">; the canvas is inserted first in Java |
| Histogram.java:109-110 | css-class | <g:Label ui:field="tooltip" styleName="stroom-histogram-tooltip"/> |

**Stays in Java:** Histogram.java:97-106 the Canvas (Canvas.createIfSupported, no public constructor, may be null), its stroom-histogram-canvas class and role=img: made in Java; Histogram.java:239 aria-label: described from the data; Histogram.java:390, 396 tooltip text and --visible: data and state.

## `FloorMapIconPicker`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.floormap.client.view.FloorMapIconPicker`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapIconPicker.ui.xml (new; element root, so it extends Widget and calls setElement as RadioGroup does, rather than Composite over a FlowPanel)`
- **Goes:** `setEnabled (FloorMapIconPicker.java:214) in favour of setReadOnly`; `setChangeHandler (FloorMapIconPicker.java:221) renamed setOnChange`
- **Names:** Keeps its name and package; unique.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapIconPicker.java:73-74 | structure | <div class="floormap-icon-picker" role="radiogroup"/> (floormap-icon-picker.css:30) |

**Stays in Java:** FloorMapIconPicker.java:76, 92-111 one cell per icon: cell, radio input, glyph (aria-hidden), label, their classes, title and text come from the icon list.

## `TraceHistogramChart`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.pathways.client.presenter.TraceHistogramChart`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because all of its HTML is drawn from the histogram data; the one fixed class, trace-histogram, can be set by a one-line template if wanted)`
- **Goes:** Constructor argument DateTimeFormatter (TraceHistogramChart.java:50): public no-arg constructor plus setDateTimeFormatter(...) (TracesListPresenter.java:111 calls it); or have the controller pass formatted labels; `setZoomHandler(BiConsumer) (TraceHistogramChart.java:80) renamed setOnZoom`
- **Names:** Keeps its name and package (rule: a widget keeps both), though it sits in a presenter package; unique in all_simple_names.json.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| TraceHistogramChart.java:134-135 | `Attribute.style("left: " + leftPct + "%; width: " + barWidthPct + "%; height: " +…` | dynamic | Width is the same for every bar: --trace-histogram__bar-width on .histogram-plot; each bar gets --trace-histogram__bar-index and --trace-histogram__bar-height only; .histogram-bar { left: calc(var(--trace-histogram__bar-index) * var(--trace-histogram__bar-width)); width: var(--trace-histogram__bar-width); height: var(--trace-histogram__bar-height); } in pathways.css:661 |
| TraceHistogramChart.java:129 | `" min-height: 3px;" when count > 0` | static | Modifier .histogram-bar--filled { min-height: 3px; } in pathways.css, added when count > 0 |

| Into the template | What | Detail |
| --- | --- | --- |
| TraceHistogramChart.java:52 | css-class | trace-histogram (pathways.css:614) on the HTML root, if given a template |

**Stays in Java:** TraceHistogramChart.java:96-145 the axes, labels, bars, titles and data-bucket-index: from the data; hint() text: from the data's state.

## `SplitPane`

widget, dumb; complexity 3, effort S; used by 7.

- **Component:** `stroom.widget.layout.client.SplitPane`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it is a ComplexPanel container: its panes and splitters are made per child declared in the caller's template, so there is no fixed markup of its own)`
- **Names:** Keeps its name and package; unique in all_simple_names.json. After the merge its menu still uses stroom.widget.menu.client.presenter.MenuRequest/IconMenuItem/Separator (SplitPane.java:20-23), which survived the MenuManager/Menu rename, so nothing to change.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| SplitPane.java:339-345 | `getElement().getStyle().setProperty(vertical ? "gridTemplateRows" :…` | dynamic | Set --split-pane__tracks on the root (element.getStyle().setProperty("--split-pane__tracks", tracks)); in split-pane.css change .split-pane to grid-template-columns: var(--split-pane__tracks, 100%) and .split-pane--vertical (split-pane.css:41-44) to grid-template-rows: var(--split-pane__tracks, 100%) with grid-template-columns: 100%; the clearProperty call goes |
| SplitPane.java:900-906 | `element.getStyle().clearDisplay() / setDisplay(NONE) on a pane or splitter (setShown)` | static | State modifiers in split-pane.css: .split-pane__pane--hidden, .split-pane__splitter--hidden { display: none; } toggled with setClassName/addClassName; no existing utility hidden class in ui/css |

**Stays in Java:** SplitPane.java:126-148 root block/modifier classes (split-pane, --vertical, --thin): it builds its own element and the modifiers are state set by template attributes; SplitPane.java:202-203 pane div and split-pane__pane: one per child, data-driven; SplitPane.java:737-750 splitter div, split-pane__splitter, role=separator, tabindex, aria-orientation: one per pair of panes, made at runtime; SplitPane.java:767-783 aria-controls, aria-label, aria-value*: from pane names and sizes at runtime; SplitPane.java:537 split-pane--resizing: state while dragging.

## `DaysOfWeek`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.processor.client.view.DaysOfWeek`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/client/view/DaysOfWeek.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/client/view/DaysOfWeek.ui.xml (moved beside the Java)`
- **Goes:** `setEnabled (DaysOfWeek.java:81-85) once its caller uses setReadOnly`
- **Names:** Keeps its name and package; unique.

| Into the template | What | Detail |
| --- | --- | --- |
| DaysOfWeek.ui.xml:6-12 | component-declaration | seven TickBox (this wave) already declared with label text; days class is components.css:1655 |

**Stays in Java:** The ticks: data; the @UiHandler ValueChangeEvent handlers (DaysOfWeek.java:103-137) become the widget's setOnChange output.

## `ValueSpinner`

widget, tidy; complexity 3, effort S; used by 34.

- **Component:** `stroom.widget.valuespinner.client.ValueSpinner`
- **Controller:** `none`
- **Template:** `none (the record's stroom-core-client/src/main/resources/stroom/main/client/view/Spinner.ui.xml belongs to a different class, stroom.main.client.view.Spinner, the main toolbar's busy spinner used in MainToolbar.ui.xml, not to stroom.widget.valuespinner.client.Spinner, which is a non-UI stepping model)` → `stroom-core-client-widget/src/main/java/stroom/widget/valuespinner/client/ValueSpinner.ui.xml (new; a g:HTMLPanel holding the TextBox widget and plain elements for the arrows, feedback and status)`
- **Goes:** `Keep HasValue<Long>/ValueChangeEvent beside a new setOnChange(Consumer<Long>) until its ~34 callers move (rule 5)`
- **Names:** Keeps its name and package. Its helper Spinner clashes three ways in all_simple_names.json (stroom.cell.valuespinner.client.Spinner, stroom.widget.valuespinner.client.Spinner, stroom.main.client.view.Spinner); not renamed in this wave (widget rule), but worth noting for imports. The record's 'declares FormGroup' is wrong: it looks up its enclosing FormGroup (findFormGroup) to show a rejection, it doesn't contain one.

| Into the template | What | Detail |
| --- | --- | --- |
| ValueSpinner.java:80-81 | css-class | <g:TextBox ui:field="valueBox" styleName="value-spinner__input allow-focus"/> |
| ValueSpinner.java:83 | aria | role="spinbutton" on the text box (needs TextInput role setter, see cssNotes) |
| ValueSpinner.java:105-108 | structure | <div class="value-spinner__steps"> with two arrows |
| ValueSpinner.java:137-142 (stepArrow) | aria | <div ui:field="decrease" class="value-spinner__step" title="Decrease value" aria-hidden="true"/> and <div ui:field="increase" ... title="Increase value" aria-hidden="true"/> as plain elements (their handlers are already native pointer listeners) |
| ValueSpinner.java:112 | css-class | <div ui:field="feedback" class="value-spinner__feedback"/> |
| ValueSpinner.java:115-116 | aria | <div ui:field="status" class="value-spinner__status" aria-live="polite"/> |
| ValueSpinner.java:119-130 | structure | root value-spinner > value-spinner__field (input + steps), feedback, status |

**Stays in Java:** ValueSpinner.java:141-143 arrow SVG (ARROW_DOWN/ARROW_UP) inner HTML and the svg's value-spinner__icon class: no template icon widget; set into the ui:field elements in Java; Spinner.java:55-69 aria-valuenow/valuetext/valuemin/valuemax and the text: values; ValueSpinner.java:109-129 (setFeedback/clear) feedback/status text and FormGroup feedback: rejection messages from data; ValueSpinner.java:144-145 mousedown preventDefault and listenForPresses: behaviour.

## `QuickFilter`

widget, tidy; complexity 3, effort S; used by 21.

- **Component:** `stroom.widget.dropdowntree.client.view.QuickFilter`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/QuickFilter.ui.xml (new; QuickFilter changes from extends FlowPanel to a Composite/Component that owns this template)`
- **Goes:** `QuickFilterContextHandler.java once replaced by setOnShowRecent/setOnRecordUse callbacks (keep until its implementers move, rule 5)`; `addValueChangeHandler/HandlerManager (QuickFilter.java:80) kept beside a new setOnChange(Consumer<String>) until callers move`; `setText(text, fireEvents) overload (QuickFilter.java:414) once callers move`
- **Names:** Keeps its name and package. all_simple_names.json has a second QuickFilter: stroom-query/stroom-query-language/.../stroom/query/language/filter/QuickFilter.java (server-side filter parser). Different module and package, never imported together in client code, so no rename; the rule keeps widget names. QuickFilterTooltipUtil, QuickFilterHistoryTracker and QuickFilterContextHandler are helpers, not UI, and keep their names.

| Into the template | What | Detail |
| --- | --- | --- |
| QuickFilter.java:104 | css-class | root styleName="quick-filter" |
| QuickFilter.java:105-106 | css-class | <g:TextBox ui:field="textBox" styleName="quick-filter__input allow-focus"/> |
| QuickFilter.java:109 | aria | type="search" on the text box (needs a TextBox with template setters, see cssNotes TextInput: type="search") |
| QuickFilter.java:110 | text | placeholder="Quick Filter" (TextInput placeholder setter) |
| QuickFilter.java:113-114 | component-declaration | <b:Toolbar ui:field="toolbar" name="Quick filter" addStyleNames="quick-filter__toolbar"> |
| QuickFilter.java:117 | component-declaration | <b:ToolbarButton ui:field="clearButton" svg="TAB_CLOSE" title="Clear Filter"/> (onClick wired in Java with setOnClick) |
| QuickFilter.java:120-123 | component-declaration | <b:ToolbarButton ui:field="dropDownButton" svg="ARROW_DOWN" title="Show Recent Filters" addStyleNames="quick-filter__recent" opensMenu="true" visible="false"/> |
| QuickFilter.java:124 | component-declaration | <b:ToolbarButton ui:field="helpButton" svg="HELP_OUTLINE" title="Quick Filter Syntax Help"/> |
| QuickFilter.java:127 | structure | description: plain <div ui:field="description" hidden=""/> in an HTMLPanel (or Label visible="false"); its id stays in Java |
| QuickFilter.java:130,132 | aria | error message: <div ui:field="errorMessage" class="sr-only" aria-live="polite"/> (sr-only is in spinner.css:126) |
| QuickFilter.java:134-138 | structure | child order text box, toolbar, description, error message: all in the template |

**Stays in Java:** QuickFilter.java:61-66 DEFAULT_POPUP_TEXT and QuickFilterTooltipUtil SafeHtml: rich help content handed to HelpPopup, not page markup; QuickFilter.java:125 HelpPopup.markOpener(helpButton.getElement()) and the HelpPopup it shows: behaviour; QuickFilter.java:128,131 DOM.createUniqueId() ids for aria-describedby: generated; QuickFilter.java:136 new FieldEnd(textBox, toolbar): behaviour (keeps text clear of buttons that come and go); QuickFilter.java:211-223 aria-invalid, describedby, error text and title: from the server's verdict; QuickFilter.java:255 quick-filter--recent modifier: state (whether a context handler is set); QuickFilter.java:378-380 aria-label from setAccessibleName and :393-395 description text: set by the caller (already template attributes accessibleName/description on the declaring template).

## `DropDownBox`

widget, dumb; complexity 2, effort S; used by 4.

- **Component:** `stroom.widget.dropdowntree.client.DropDownBox`
- **Controller:** `none (DocSelectionController and the other selection controllers attach to it)`
- **Template:** `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.ui.xml (already beside the Java; the record's src/main/resources path does not exist)`
- **Goes:** `setEnabled(boolean) (DropDownBox.java:142) once callers use setReadOnly / a disabled state through Component; keep it until then (rule 5)`; `No view interface, UiHandlers or GIN binding exist`
- **Names:** Keeps its name and package; DropDownBox is unique in all_simple_names.json. The record's 'declares FormGroup' is wrong: DropDownBox does not contain a FormGroup, it looks up the FormGroup it sits in (findFormGroup, DropDownBox.java:202-208) to show its problem there, so there is nothing to declare.

| Into the template | What | Detail |
| --- | --- | --- |
| DropDownBox.java:83 | aria | tabindex="0" on the root; needs the root to be a widget with template setters (see cssNotes AriaPanel), e.g. <c:AriaPanel styleName="drop-down stroom-control allow-focus" tabIndex="0" role="button" ariaHasPopup="dialog"> |
| DropDownBox.java:86 | aria | role="button" on the root (same AriaPanel root) |
| DropDownBox.java:87 | aria | aria-haspopup="dialog" on the root (same AriaPanel root) |

**Stays in Java:** DropDownBox.java:89 SvgImageUtil.setSvgAsInnerHtml(ellipsesBtnPanel, ELLIPSES_HORIZONTAL): no template-usable icon widget exists (only ToolbarButton has setSvg); stays unless an icon widget is added; DropDownBox.java:112-114 label text/title: the chosen value, from data; DropDownBox.java:163-183 the problem feedback line (drop-down__feedback, sr-only span, aria-hidden, title, aria-invalid/describedby): added only while there is a problem, text from data; DropDownBox.java:210-216 disabled/readonly state class and aria-disabled: runtime state.

## `Pager`

widget, dumb; complexity 2, effort S; used by 3.

- **Component:** `stroom.data.pager.client.Pager`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/data/pager/client/Pager.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.ui.xml (moved beside Pager.java)`
- **Goes:** `Lazy static binder (Pager.java:45, 83): make it private static final Binder BINDER = GWT.create(Binder.class)`
- **Names:** Keeps its name and package; unique in all_simple_names.json.

| Into the template | What | Detail |
| --- | --- | --- |
| Pager.ui.xml:17-23 | component-declaration | Toolbar, four ToolbarButtons and RefreshButton are already declared; nothing more to move |

**Stays in Java:** Pager.java:182-183 txtFrom/txtTo text, :308-310 '?' placeholders when the count is unknown, :335-344 from/to/of numbers: data from the display's range; Pager.java:370-371 title: caller's.

## `SelectionBox`

widget, tidy; complexity 3, effort M; used by 108.

- **Component:** `stroom.item.client.SelectionBox (and stroom.item.client.BaseSelectionBox, which keeps its name: TermEditor.java:74,600 and SelectionBoxStories use it directly with their own model, so it is not merged)`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/item/client/BaseSelectionBox.ui.xml (new; SelectionBox adds no markup)`
- **Goes:** `SelectionBoxView.java (vestigial one-method view interface; only BaseSelectionBox implements it)`; BaseSelectionBox's EventBinder use (BaseSelectionBox.java:73-82, onLoad/onUnload): handlers on its own children go with them, so add them once in the constructor; `EventBinder.java itself only after its other ~14 users move (DurationPicker etc.)`; `Keep HasValueChangeHandlers beside a new setOnChange/Listeners until ~108 callers move (rule 5)`
- **Names:** SelectionBox, BaseSelectionBox, SelectionBoxView, EventBinder are unique in all_simple_names.json. BaseSelectionBox keeps its name (it is a used generic, not an abstract base).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| BaseSelectionBox.java:114-116 | `textBox.getElement().getStyle().setOpacity(allowTextEntry ? 1 : 0)` | static | Follows the existing selection-box--select-only modifier on the root: add .selection-box--select-only .selection-box__input { opacity: 0; } to selection-box.css (beside :62) |
| BaseSelectionBox.java:117-119 | `renderBox.getElement().getStyle().setOpacity(allowTextEntry ? 0 : 1)` | static | .selection-box:not(.selection-box--select-only) .selection-box__value { opacity: 0; } in selection-box.css (beside :38) |

| Into the template | What | Detail |
| --- | --- | --- |
| BaseSelectionBox.java:85-86 | css-class | <g:TextBox ui:field="textBox" styleName="selection-box__input stroom-control allow-focus"/> |
| BaseSelectionBox.java:88-89 | css-class | <g:SimplePanel ui:field="renderBox" styleName="selection-box__value stroom-control allow-focus"/> |
| BaseSelectionBox.java:92 | aria | aria-hidden="true" on renderBox (AriaPanel/plain element) |
| BaseSelectionBox.java:94-96 | aria | role="combobox" aria-haspopup="listbox" aria-expanded="false" on the text box (TextInput setters) |
| BaseSelectionBox.java:98-101 | structure | <g:FlowPanel styleName="selection-box__control"> holding renderBox then textBox |
| BaseSelectionBox.java:103-107 | component-declaration | <svg:IconBox ui:field="svgIconBox" styleName="selection-box" dropDown="true"> with the control as its child: IconBox needs a template form of setDropDownWidget (e.g. @UiChild or a dropDown attribute); it is a wave 1 widget |

**Stays in Java:** BaseSelectionBox.java:122 selection-box--select-only, :116 disabled, :136 readonly modifiers: state; aria-expanded/aria-controls when the popup opens and closes, aria-readonly: state; The value's rendered picture and text: data.

## `DurationPicker`

widget, tidy; complexity 2, effort S; used by 7.

- **Component:** `stroom.widget.customdatebox.client.DurationPicker`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/customdatebox/client/DurationPicker.ui.xml (new)`
- **Goes:** The EventBinder (DurationPicker.java:43-51) and its onLoad/onUnload bind/unbind: handlers on its own children go with them, so add them once in the constructor (or use the children's new setOnChange); `Keep HasValue<SimpleDuration>/ValueChangeEvent beside setOnChange until callers move`
- **Names:** Keeps its name and package; unique. The record's empty 'declares' is wrong: it embeds ValueSpinner and SelectionBox, both in this wave, so convert it after them and declare both.

| Into the template | What | Detail |
| --- | --- | --- |
| DurationPicker.java:54-56 | component-declaration | <v:ValueSpinner ui:field="time" addStyleNames="duration-picker__amount" min="1" max="1000000" ariaLabel="Amount"/> (ariaLabel needs a new ValueSpinner.setAriaLabel, see cssNotes) |
| DurationPicker.java:57 | component-declaration | <i:SelectionBox ui:field="timeUnit" addStyleNames="duration-picker__unit" ariaLabel="Unit"/> (needs BaseSelectionBox.setAriaLabel) |
| DurationPicker.java:67-68 | css-class | duration-picker__amount / duration-picker__unit (duration-picker.css) |
| DurationPicker.java:70-74 | structure | root <g:FlowPanel styleName="duration-picker"> holding the two |
| DurationPicker.java:76-77 | aria | AccessibleName.set(time,"Amount") / (timeUnit,"Unit") become ariaLabel="Amount"/"Unit" attributes |

**Stays in Java:** DurationPicker.java:59-65 and smallTimeMode() addItem(TimeUnit...): enum items; SelectionBox has no template item syntax; Values set in setValue: data.

## `Calendar`

widget, tidy; complexity 2, effort S; used by 3.

- **Component:** `stroom.widget.datepicker.client.Calendar`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/Calendar.ui.xml (new; holds the header from DefaultMonthSelector and declares CalendarGrid)`
- **Goes:** DefaultMonthSelector.java merged into Calendar (its header goes into the template, its handlers into Calendar), removing the child-takes-parent coupling; `CalendarGrid(Calendar) constructor argument: give it a no-arg constructor and a setter or callbacks so the template can declare it`; `Keep HasValue<UTCDate>/ValueChangeEvent beside setOnChange until its 3 callers move`
- **Names:** Calendar, CalendarGrid, DefaultMonthSelector are unique. DefaultMonthSelector is merged rather than renamed.

| Into the template | What | Detail |
| --- | --- | --- |
| Calendar.java:61-65 | structure | root <g:FlowPanel styleName="calendar"> holding header then <dp:CalendarGrid ui:field="grid"/> |
| DefaultMonthSelector.java:91-95 | structure | <g:FlowPanel styleName="calendar__header"> with two calendar__row rows |
| DefaultMonthSelector.java:81-84 | component-declaration | year row: <i:SelectionBox ui:field="yearSelect" ariaLabel="Year"/> then <b:Toolbar name="Year"><b:ToolbarButton ui:field="previousYear" svg="ARROW_LEFT" title="Previous Year"/><b:ToolbarButton ui:field="nextYear" svg="ARROW_RIGHT" title="Next Year"/></b:Toolbar> |
| DefaultMonthSelector.java:74-76, 86-89 | component-declaration | month row: <i:SelectionBox ui:field="monthSelect" ariaLabel="Month"/> then <b:Toolbar name="Month"> with previousMonth/nextMonth ToolbarButtons (ARROW_LEFT 'Previous Month', ARROW_RIGHT 'Next Month') |
| DefaultMonthSelector.java:116, 139 | aria | AccessibleName.set(...,"Month"/"Year") become ariaLabel attributes (needs BaseSelectionBox.setAriaLabel) |
| CalendarGrid.java:73-74 | aria | the table's calendar__grid class and role="grid" could be the CalendarGrid's own element template; optional, see stays |

**Stays in Java:** DefaultMonthSelector.java:118-120 the 12 month items and :106-113 the 21 year items: locale-formatted data; CalendarGrid.java:76-105 weekday headings (locale names, abbr) and the 6x7 day cells: built in a loop from the model, text from data; CalendarGrid.java:133-137, 151, 165 day text, aria-label, aria-selected, aria-current, grid label: data and state.

## `TimeZoneBox`

widget, tidy; complexity 2, effort S; used by 3.

- **Component:** `stroom.query.client.view.TimeZoneBox`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/TimeZoneBox.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/view/TimeZoneBox.ui.xml (moved beside the Java)`
- **Goes:** @Inject constructor taking Binder (TimeZoneBox.java:53-54): becomes a public no-arg constructor with a static GWT.create Binder; ViewStateStories.java:507 then uses new TimeZoneBox(); ChangeUiHandlers field and setUiHandlers (TimeZoneBox.java:51, 195) replaced by setOnChange(Runnable); ProcessorProfileEditViewImpl, HistogramKeySchemaSettingsWidget and MetricKeySchemaSettingsWidget move in the same commit; `onReadOnly (TimeZoneBox.java:148) duplicate of setReadOnly`
- **Names:** Keeps its name and package; unique.

| Into the template | What | Detail |
| --- | --- | --- |
| TimeZoneBox.ui.xml:13,20,27 | component-declaration | SelectionBox x2 and ValueSpinner already declared inside FormGroups; nothing more |
| TimeZoneBox.ui.xml:8 | css-class | root 'max form': .max (stroom.css:793) and .form (form.css:42) are Stroom classes, keep |

**Stays in Java:** TimeZoneBox.java:56-59 Use items and :63 zone ids from MomentJs: enum/data; TimeZoneBox.java:144-145 FormGroup setVisible for Id/Offset: state (GWT setVisible).

