# Wave 3: Containers, tab bars, dialog chrome and dumb view/presenter collapses

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

Move the tab-bar family to setOnSelect/setOnClose/setOnMenu, give the dialog chrome and its button bars templates and no-arg constructors (deleting the four Resizable*Content copies), and convert the presenter/view pairs that hold no logic into components. (PagerView and PagerViewWithHeading, first planned here, moved to wave 5.)

**Status: done 24 October 2026; see the wave's section in `../COMPONENT_MIGRATION_ORDER.md` for what was done and what was left.**

## Audited (25 October 2026)

Four reviews of the wave's commits (each step, and the whole wave), each finding checked in the code. What was fixed is listed in the wave's section of `../COMPONENT_MIGRATION_ORDER.md`. Decided, against this plan:

- **`Icon` has no block of its own.** It writes the shared `svg-image` block that `SvgImageUtil` writes, so an icon declared in a template looks as one set in Java does; a block of its own would hold no rules. Where it sits is styled by its parent's element class (e.g. `dialog__icon`).
- **The `TabSelector` is made in Java**, by `AbstractTabBar`'s constructor, which adopts it (`add(selector, root)`): the bars' templates have elements as their roots (the bar widget takes its root as its element), and such a template can't hold widgets.
- **`draggable="true"` stays with `DraggableTabBar`**, which also listens for the drag: the bar makes its tabs draggable, not the tab's template.
- **No `setOnShowMenu` on the tab bars yet**: nothing would call it. It is added with its first caller, when the tab layouts convert (`CurveTabLayoutPresenter`, wave 15; the screens on `LinkTabsLayoutView` as they convert); `setOnSelect` and `setOnClose` are on `TabBar`, so callers can move without a cast.

Left, recorded in `../ISSUES.md` or here:

- A `PopupType.POPUP` given a size still sets an inline width and height on its content (`PopupUtil`): a class wouldn't beat the content's own width rules as the inline size does, so it waits for the popups' contents to convert (menus and lists).
- `Dialog` and `ResizableDialog` keep `implements TaskMonitorFactory` (not a `setBusy(boolean)`), and `ResizableDialog` takes its size in its constructor (not `setPopupSize`); `Dialog` has no `setAutoHide`. They are made only by `PopupSupportImpl`, so this waits until the popup manager can show a controller again.
- `PopupSupportImpl` casts a dialog to `TaskMonitorFactory` and passes a button bar as both content and buttons; a typed helper can come with the above.
- Blocks named for an older class: `HtmlPane` uses `info-page`, `IFrame` `iframe-panel`/`iframe-frame` and `NameDialog` the shared `form`, as planned (reuse over new classes); `OperatorEditor` adds `operator` beside `termEditor-item`, as `TermEditor` does, until `TermEditor` converts (wave 7). `QueryButtons`, `MainToolbar` and `OperatorEditor` still extend `Composite`, not `Component` (harmless).
- `QueryDataViewImpl` and `SteppingPresenter` borrow the `query-buttons__button` classes for their own run and stop buttons; they move to their own blocks when they convert (both wave 13).
- `LinkTab`'s sizer is an `AriaPanel` filled with `setInnerText`, until `Label` has an `ariaHidden` setter.
- The suite's crawl-based oracles (`capability-specs`, `handler-inventory`, `route-recipes`) still name classes the wave deleted (`ProgressPresenter`, `UserTaskPresenter`, `TextBoxPopup`, the `Resizable*Content` classes): they are rebuilt from a crawl of a running app, not from the source, so they wait for the next crawl.

## Decided before starting (24 October 2026)

- **No merging.** `Dialog` and `ResizableDialog` stay two units, each converted on its own: they differ in about 290 lines (resizing, sizes, their own templates) and already share what they have in common (`DialogWindow`, `DialogFocusTrap`, `AbstractPopupPanel`); anything more found in common goes into a helper, not a flag. The dialogs' button bars stay four small components (`OkCancelContent`, `CloseContent`, `AcceptRejectContent`, `CreateOkCancelContent`), not one bar with an action set; what goes is the four `Resizable*Content` classes, which are copies of the first three (and of the create one's only user), differing only in their names.
- **The tab bars keep their classes.** `DraggableTabBar` stays a class of its own between `AbstractTabBar` and `CurveTabBar` (its dead separator code, DraggableTabBar.java:113-118, goes; `DraggedTab` stays). The tab overflow button becomes one widget, `TabSelector` (`stroom.widget.tab.client.view`), with its own template, block (`tab-selector`) and stylesheet, declared in both bars' templates, its arrows an `Icon`: `AbstractTabSelector` goes, and `CurveTabSelector` and `BasicTabSelector`, copies of each other (one with a template, one without), become it. Rows below marked "Superseded" are read in that light.
- **"Collapsing" a presenter** means converting one that holds no logic: a presenter with nothing to do becomes a dumb component with no controller (`SourceProgressBar`, `HtmlPane`, `UserTask`), one with some becomes a component and controller pair (`InfoTooltip`, `NameDialog`, `IFrame`). Nothing is merged.
- **An `Icon` widget first** (`stroom.svg.client.Icon`, named for what it is): a dumb widget with an `image` attribute (an `SvgImage`), `aria-hidden`, and its own block (`icon`, `icon.css`), so that fixed icons (the tab selectors' arrows, a tab's close cross, the dialog's resize handle) are declared in templates in this wave and later ones, rather than set in Java with `SvgImageUtil`.
- **`WrapperView` is left out of this wave**: it only holds a child, and goes when its last presenter user becomes a controller (waves 10 and 11), which calls `initWidget(child)`.
- **Three steps**, each committed and checked with every story, the leak check and a screenshot diff: (1) `Icon`, then the dialog chrome and button bars (`AbstractPopupPanel`, `Dialog`, `ResizableDialog`, the content components, `HelpPopup`); (2) the tab bars (`AbstractTabBar`, `LinkTabBar`, `CurveTabBar`, `LayerContainerImpl`); (3) the dumb presenters and small tidies (`SourceProgressBar`, `HtmlPane`, `IFrame`, `UserTask`, `InfoTooltip`, `NameDialog`, `MainToolbar`, `QueryButtons`, `VisToolbarRow`, `GroupLevelButtons`, `OperatorEditor`).
- **Today's rules apply over this plan where they differ**: every block in its own stylesheet with BEM names (`layerContainer-*` becomes `layer-container__*`, the tab bar's property `--tab-bar__left`, not `--tab-bar-left`), names for what a thing is (rule 12), and no reuse of `pager` (the class of `PagerView`'s container, not of a pager) for `VisToolbarRow` without checking what it brings.
- **Already done** since this plan was written: `BaseSelectionBox.setAriaLabel` (wave 2); `AGENTS.md` names `ControllerDialog.showOnce`; `MainToolbar` declares `TaskSpinner`.

## CSS and template notes for the wave

- tab-bar.css (units 37-39) needs three new rules. (1) Positions as a custom property: .tab-bar__tab, .tab-bar__separator, .tab-bar__selector { left: var(--tab-bar-left, 0); }, so that AbstractTabBar sets only --tab-bar-left on each element. (2) Hidden-state modifiers .tab-bar__tab--hidden and .tab-bar__selector--hidden { visibility: hidden; left: -1000px; }, replacing makeInvisible/makeVisible (AbstractTabBar.java:647-655). (3) .tab-bar__tab--fixed for a tab that can't be closed (CurveTab.java:86-87).
- No template element shows an SvgImage today. IconBox is a field wrapper, and every other caller uses SvgImageUtil.setSvgAsInnerHtml or ToolbarButton's svg attribute. Decided: wave 3 starts with a small dumb `stroom.svg.client.Icon` widget (an image attribute, aria-hidden), so the fixed icons in this wave (DOUBLE_ARROW in both tab selectors, TAB_CLOSE in CurveTab, the resize handle) are declared in templates, as are later units' icons.
- Classes named after deleted ViewImpls should get BEM names in place, in the same file, alongside their unit, since each is used only by that unit. Moving them must keep the screenshots identical: progressViewImpl-* becomes source-progress-bar, source-progress-bar__track and source-progress-bar__window (components.css:889-913); userTaskView-* could become user-task__* (components.css:102-150). layerContainer-* (components.css:35-58, 1280) and info-tooltip (info-tooltip.css) already read as block names and stay. The comment in info-tooltip.css that names TooltipViewImpl should name InfoTooltip.
- The SourceProgressBar window colours become state modifiers using the existing palette variables in material_design_colors.css: --blue-600 (:138), --grey-700 (:343) and --amber-400 (:272). No variable exists for the complete colour, #15a32c, so it stays as a literal in the rule.
- TermEditor.css (unit 170): .termEditor-item.operator { width: 55px; }, following that file's existing modifiers (wide, narrow, condition, docRef). TermEditor's own inline widths, if any, should use the same pattern when it converts.
- Reused unchanged: info-page and max (stroom.css:647) for HtmlPane; iframe-panel and iframe-frame (stroom.css:897-908) for IFrame; feedback (ChangePassword.css:13) in NameDialog. form-group__feedback (form.css:84) differs (no min-height or margin), so switching would change the look. pager, pager-toolbar, dock-* and visToolbarRow are reused for VisToolbarRow, and QueryButtons-button with its play and stop modifiers (stroom-dashboard.css:34-50) for QueryButtons.
- Prerequisite from wave 2: BaseSelectionBox needs a setAriaLabel(String) (UiBinder attribute ariaLabel, as LinkButton has) so that OperatorEditor, and later TermEditor, can name their boxes in the template instead of calling AccessibleName.set.
- Doc drift found: AGENTS.md says ComponentDialog.showOnce, but the class is now stroom.widget.component.client.ControllerDialog.showOnce, and the unit records' "declares" for MainToolbar says ValueSpinner where the template uses stroom.main.client.view.Spinner.

## `WrapperView`

Taken out of wave 3 (decided 24 October 2026): it only holds a child, and is deleted when the last of its presenter users (IncludeExcludeFilterDictionaryPresenter, ScriptDependencyListPresenter, ProcessorProfilePresenter, IndexVolumeGroupPresenter, FsVolumeGroupPresenter, MarkerListPresenter, AnnotationTagPresenter, NodeGroupPresenter, and MonitoringModule's binding) becomes a controller, which calls `initWidget(child)` (waves 10 and 11). Its GIN bindings (DashboardAppModule.java:191, MonitoringModule.java:106, the workbench's ScreenViewsModule.java:111) and the story's `new WrapperViewImpl()` (SourceViewerStories.java:261) go with it.

## `TooltipPresenter`

dialog, presenter; complexity 1, effort S; used by 9.

- **Component:** `stroom.widget.tooltip.client.component.InfoTooltip`
- **Controller:** `stroom.widget.tooltip.client.component.InfoTooltipController`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/tooltip/client/component/InfoTooltip.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/tooltip/client/presenter/TooltipPresenter.java (and its nested TooltipView interface)`; `stroom-core-client-widget/src/main/java/stroom/widget/tooltip/client/view/TooltipViewImpl.java`; stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:134 bindPresenterWidget(TooltipPresenter, TooltipView, TooltipViewImpl) and its imports :87-89; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:99 bindPresenterWidget(TooltipPresenter, ...) and imports :73-75
- **Names:** No class is called Tooltip, but the repo already has FocusTooltip, VisTooltip and QuickFilterTooltipUtil, and this is the grid information column's "Information" popup, styled by info-tooltip.css. So the name is InfoTooltip, not the suggested Tooltip, to match its CSS block and InfoColumn. InfoTooltipController is also free. Rule 1 puts it in client.component, not the presenter package the Becomes column gave. Callers: ProcessorListPresenter, IndexShardPresenter, ProcessorTaskListPresenter, ProcessorTaskSummaryPresenter, NodeStatusListPresenter, TaskManagerListPresenter, ImportConfigConfirmPresenter, and the javadoc at InfoColumn.java:40. These keep one owned TooltipPresenter today (e.g. ProcessorListPresenter.java:115 own(...), :279 show, :282 OpenLinkUtil on its widget). They switch to making an InfoTooltipController for each show (ControllerDialog.showOnce disposes it on hide), so OpenLinkUtil.addClickHandler has to go on each new one.

| Into the template | What | Detail |
| --- | --- | --- |
| TooltipViewImpl.java:116-117 | structure | <g:HTML ui:field="content" styleName="info-tooltip"/> as the template root, replacing new HTML() + setStyleName("info-tooltip") |
| TooltipViewImpl.java:113-141 | component-declaration | InfoTooltip extends Component with a Binder, and has setText(String), setHtml(SafeHtml) and focus(); no view interface |

**Stays in Java:** TooltipViewImpl.java:119 content.getElement().setTabIndex(0): the HTML widget has no tabIndex setter that UiBinder can use, so it stays in the component's constructor (or the root becomes an HTMLPanel holding a <div ui:field tabindex="0">, which adds a wrapper element and must keep the screenshots the same); TooltipPresenter.java:39,55 the text or HTML shown (data); TooltipPresenter.java:64-72 popup type POPUP, caption "Information", position, focus on show and the anchor as auto-hide partner: dialog chrome, so it stays in the controller's ControllerDialog.showOnce(...) builder.

## `ProgressPresenter`

widget, presenter; complexity 2, effort S; used by 7.

- **Component:** `stroom.widget.progress.client.component.SourceProgressBar`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/progress/client/view/ProgressViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/progress/client/component/SourceProgressBar.ui.xml`
- **Goes:** stroom-core-client-widget/src/main/java/stroom/widget/progress/client/presenter/ProgressPresenter.java (and the nested ProgressView interface); `stroom-core-client-widget/src/main/java/stroom/widget/progress/client/view/ProgressViewImpl.java`; `stroom-core-client-widget/src/main/resources/stroom/widget/progress/client/view/ProgressViewImpl.ui.xml (moved and renamed)`; stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:161-164 bindPresenterWidget(ProgressPresenter, ProgressView, ProgressViewImpl); `stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:153-156 bindPresenterWidget(ProgressPresenter, ...)`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/processing/StreamStoreScreenModule.java:110-112`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/StreamStoreScreenModule.java:110-112`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/StreamStoreScreenModule.java:111-113`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/StreamStoreScreenModule.java:110-112`
- **Names:** The suggested name Progress clashes with the unit's own model class stroom.widget.progress.client.presenter.Progress, which the callers also use. So the component is SourceProgressBar, the name its stories already use (SourceProgressBarStories). SourceProgressBar is free. It is a reusable dumb widget, so it has no controller, and the presenter's bound and uncertainty maths moves into the component. Progress (the model) moves out of the emptied presenter package to stroom.widget.progress.client.Progress. setClickHandler(Consumer<Double>) becomes setOnClick(Consumer<Double>). Callers change: DataPresenter (:165,:217,:299,:1023; DataView.setProgressView(ProgressView) at :1316 and DataViewImpl.java:132), SourcePresenter (:100,:174; SourceView :890), CharacterNavigatorPresenter :41, and the stories SourcePresenterStories :182-186, SourceViewerStories :260 and SourceProgressBarStories :66-85.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ProgressViewImpl.java:88-90 | `progressBarInner.getStyle().setLeft(rangeFrom, Unit.PCT)` | dynamic | --source-progress-bar-from (e.g. "12.5%") on the window element; .source-progress-bar__window { left: var(--source-progress-bar-from, 0); } in components.css (replacing .progressViewImpl-progressBarInner at :907) |
| ProgressViewImpl.java:95-97 | `progressBarInner.getStyle().setWidth(progress, Unit.PCT)` | dynamic | --source-progress-bar-width; .source-progress-bar__window { width: var(--source-progress-bar-width, 0); } |
| ProgressViewImpl.java:102-104 (colours from ProgressPresenter.java:37-40, chosen at :161-179) | `progressBarInner.getStyle().setBackgroundColor(colour)` | static | State modifiers on the window, new in components.css: the default .source-progress-bar__window is var(--blue-600) (#1e88e5, as :907 has now), and the modifiers are --disabled var(--grey-700), --complete #15a32c and --unbounded var(--amber-400) (the variables are in material_design_colors.css:138/343/272). This also resolves the TODO at ProgressPresenter.java:163. |
| ProgressViewImpl.java:122-127 | `style.setCursor(Cursor.DEFAULT / Cursor.POINTER)` | static | State modifier source-progress-bar__track--clickable { cursor: pointer; } in components.css, set while an onClick is given |

| Into the template | What | Detail |
| --- | --- | --- |
| ProgressViewImpl.ui.xml:4-13 | css-class | Rename to BEM and move with the template: progressViewImpl-progressBarContainer becomes source-progress-bar, progressViewImpl-progressBarOuter becomes source-progress-bar__track, progressViewImpl-progressBarInner becomes source-progress-bar__window (components.css:889-913, renamed in place) |
| ProgressViewImpl.java:49-52 | component-declaration | SourceProgressBar extends Component with a static Binder and a no-arg constructor (no GIN Binder injection), and @UiHandler on the track's click |

**Stays in Java:** ProgressPresenter.java:158-180 the window position and width, the state modifier and the title: worked out from the Progress data; ProgressPresenter.java:31-33,166-177 and ProgressViewImpl.java:109-112 the title text ("Section of the source data visible in the editor (x%)..."): built from data, set on the track and the window; ProgressViewImpl.java:117 setVisible: state.

## `IFramePresenter`

widget, tidy; complexity 2, effort S; used by 6.

- **Component:** `stroom.iframe.client.component.IFrame`
- **Controller:** `stroom.iframe.client.component.IFrameController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/iframe/client/component/IFrame.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/iframe/client/presenter/IFramePresenter.java (the nested IFrameView goes; the SandboxOption enum moves to IFrame); `stroom-core-client/src/main/java/stroom/iframe/client/view/IFrameViewImpl.java`; `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:136 bindPresenterWidget(IFramePresenter, IFrameView, IFrameViewImpl)`; `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:141-144 bindPresenterWidget(IFramePresenter, ...)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:100 bindPresenterWidget(IFramePresenter, ...)
- **Names:** The Becomes column keeps IFramePresenter, but it is a presenter unit, so rule 1 moves it to stroom.iframe.client.component. IFrame and IFrameController are both free. GWT has Frame and IFrameElement, but not IFrame. It is a pair, not a lone widget, for two reasons. It polls the page title every second (IFrameViewImpl.java:56-71), which must stop on dispose (IFramePresenter.java:39), and a dumb component has no lifecycle. And HyperlinkEventHandlerImpl.java:280-292 shows it as a dialog, which needs a controller (ControllerDialog.showOnce). The controller runs the poll, holds the custom title and calls setOnTitleChange and getLabel; IFrame only sets url, srcdoc, sandbox, id and accessible name, and reads the document title. IFrameLoadUiHandlers stays: IFrameContentPresenter and IFrameContentViewImpl, in a later wave, still use it (rule 5). Callers: MarkdownEditPresenter (:79), MarkdownPreviewPresenter (:61), HyperlinkEventHandlerImpl (:73,:280), and MarkdownEditorStories :166,:175.

| Into the template | What | Detail |
| --- | --- | --- |
| IFrameViewImpl.java:43-44,51-54 | structure | <g:SimplePanel styleName="iframe-panel"><g:Frame ui:field="frame" addStyleNames="iframe-frame"/></g:SimplePanel> (stroom.css:897-908). Use addStyleNames, not styleName, on the Frame to keep gwt-Frame as today. |

**Stays in Java:** IFrameViewImpl.java:81 id, :87 url, :92 srcdoc, :97-109 sandbox attribute: values from the caller; IFrameViewImpl.java:118 frame title (accessible name): given by the caller (e.g. "Preview"), so it stays a setter; IFrameViewImpl.java:39,46,123-127 the "Loading..." default and the last title read: state, in the controller; IFrameViewImpl.java:56-71 the title poll (Scheduler.scheduleFixedDelay): moves to IFrameController and stops in its onDispose.

## `AbstractPopupPanel`

widget, tidy; complexity 2, effort S; used by 5.

- **Component:** `stroom.widget.popup.client.view.AbstractPopupPanel`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it is the PopupPanel base: its root element is GWT's popup, not a template's widget)`
- **Goes:** the DialogActionUiHandlers constructor argument of AbstractPopupPanel and SimplePopup (AbstractPopupPanel.java:44, SimplePopup.java:42-57): setOnAction(Consumer<DialogAction>) instead, so SimplePopup has a no-arg constructor
- **Names:** No clash. AbstractPopupPanel is the base of Dialog, SimplePopup and HelpPopup, so it is kept rather than merged (three subclasses). SimplePopup and SimplePopupLayout keep their names. SimplePopupLayout is a SimplePanel with class popup and can stay as it is.

**Stays in Java:** setStyleName("popup popup--floating") (SimplePopup.java:61) and SimplePopupLayout's setStyleName("popup") (SimplePopupLayout.java:29): on popup or panel roots that templates can't make, and fixed. Acceptable as each class's own block name; role="dialog" and aria-label set from the caption (SimplePopup.java:73-82): from data; the drag glass classes popupPanel-dragGlass and popupPanel-dragGlassVisible (AbstractPopupPanel.java:21-23): Glass makes its own body element; position: GWT PopupPanel.

## `HelpPopup`

dialog, tidy; complexity 2, effort S; used by 4.

- **Component:** `stroom.widget.help.client.HelpPopup`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/help/client/HelpPopup.ui.xml`
- **Goes:** static factories markdown(...)/html(...) (HelpPopup.java:79, :88), replaced by a no-arg constructor with setContent(SafeHtml), setMarkdown(boolean), setAccessibleName(String) and setOnClose(Runnable); `private CloseActionHandler implements DialogActionUiHandlers (HelpPopup.java:165-177): AbstractPopupPanel is given a close callback instead`; `no GIN binding`
- **Names:** HelpPopup has no clash. It keeps its name and package (stroom.widget.help.client).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/help/client/HelpPopup.java:60-62 | structure | Template for the popup's widget: <g:HTML ui:field="content" styleName="help-popup__content"/>, filled with setHTML(SafeHtml) instead of new HTMLPanel(content) |
| stroom-core-client-widget/src/main/java/stroom/widget/help/client/HelpPopup.java:61 | css-class | help-popup__content |

**Stays in Java:** setStyleName("help-popup") and role="dialog" on the popup itself (HelpPopup.java:63, :70): the root is the PopupPanel's own element, which no template makes; aria-label (HelpPopup.java:71): the name comes from the caller; help-popup--markdown and markdown (HelpPopup.java:66-67): a variant set by setMarkdown(boolean); aria-haspopup and aria-expanded on the opener (HelpPopup.java:102-103, :141): another widget's element, changing state. Better still, the opener's own template should say aria-haspopup="dialog"; PopupUtil.showPopup places the popup (left and top inline). That is GWT PopupPanel's own positioning and outside this unit.

## `LinkTabBar`

widget, tidy; complexity 1, effort S; used by 4.

- **Component:** `stroom.widget.tab.client.view.LinkTabBar`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/LinkTabBar.ui.xml (and LinkTab.ui.xml beside it)`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/BasicTabSelector.java (the selector is in LinkTabBar.ui.xml)` **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**
- **Names:** Keeps its name and package. Its users declare it in templates (LinkTabsLayoutViewImpl, DataRetentionViewImpl, QueryResultTabsViewImpl, UserPreferencesViewImpl, DataViewImpl, LinkTabPanelViewImpl, FloorMapDockViewImpl, AskStroomAiConfigViewImpl) or make it with new LinkTabBar() (TabLayout.java:86). Its no-arg constructor stays.

| Into the template | What | Detail |
| --- | --- | --- |
| LinkTabBar.java:27 | css-class | Template root <div class="tab-bar tab-bar--link"> |
| LinkTabBar.java:29-34 | structure | <div class="tab-bar__strut" aria-hidden="true">A</div> (the invisible text that sets the bar's height) |
| AbstractTabBar.java:87-90 (for this bar) | aria | <div ui:field="tabList" class="tab-bar__tabs" role="tablist"/> |
| LinkTabBar.java:56-59 + BasicTabSelector.java:32-47 | structure | Selector elements in the template: tab-bar__selector (role="button", aria-haspopup="menu", aria-expanded="false", aria-label="Show all tabs"), tab-bar__selector-arrows, tab-bar__selector-count | **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**
| LinkTab.java:32-50 | structure | LinkTab.ui.xml (UiBinder<DivElement>): <div class="tab-bar__tab" role="tab" tabindex="-1" aria-selected="false"><div ui:field="sizer" class="tab-bar__sizer" aria-hidden="true"/><div ui:field="label" class="tab-bar__label"/><div ui:field="hotspot" class="tab-bar__hotspot"/></div> |

**Stays in Java:** LinkTabBar.java:62-66 createSeparator (class tab-bar__separator, text "\|"): one for each gap between visible tabs, so built at runtime; BasicTabSelector.java:34 SvgImageUtil DOUBLE_ARROW on the arrows element: no SvgImage template element yet; LinkTab.java:34 the tab's title (tooltip), :38 and :45 its text (both the sizer and the label): data; LinkTab.java:59-65 the tab-bar__hotspot--visible state class (dashboard drag highlight); LinkTabBar.java:40,45 GlobalResizeObserver: behaviour.

## `HtmlPresenter`

widget, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.editor.client.component.HtmlPane`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/editor/client/component/HtmlPane.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/editor/client/presenter/HtmlPresenter.java (and the nested HtmlView interface)`; `stroom-core-client-widget/src/main/java/stroom/editor/client/view/HtmlViewImpl.java`; `No GIN binding to remove: HtmlPresenter makes its own new HtmlViewImpl() (HtmlPresenter.java:30)`
- **Names:** The suggested name Html is free in the repo, but it reads as GWT's HTML widget, and HtmlPanel as GWT's HTMLPanel, which this class uses. So the name is HtmlPane: no clash, and it says what it is (a scrolling pane of HTML). HtmlPane is a dumb widget with no controller. Callers: dashboard TextPresenter (:92,:212), query TextPresenter (:58,:175), DataPresenter (:160,:986), SourceViewerStories :253, and HtmlPresenterStories (renamed HtmlPaneStories, story path Widgets/Display/HtmlPane).

| Into the template | What | Detail |
| --- | --- | --- |
| HtmlViewImpl.java:31-33 | structure | <g:ScrollPanel styleName="max info-page"><g:HTML ui:field="content"/></g:ScrollPanel>, replacing new ScrollPanel() + addClassName("max") + addClassName("info-page") (stroom.css:647) |

**Stays in Java:** HtmlViewImpl.java:43-44 the HTML shown (data): content.setHTML(SafeHtmlUtils.fromTrustedString(html)), which trusts the string as new HTMLPanel(html) does, instead of making a new HTMLPanel for each set.

## `LayerContainerImpl`

widget, dumb; complexity 2, effort S; used by 10.

- **Component:** `stroom.widget.tab.client.view.LayerContainerImpl`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/LayerContainerImpl.ui.xml`
- **Names:** The widget keeps its name: it implements the LayerContainer interface, which ComponentLayer/ControllerLayer and the presenter-era Layer implementations use. There is no clash and nothing to merge.

| Into the template | What | Detail |
| --- | --- | --- |
| LayerContainerImpl.java:42-44 | structure | <g:FlowPanel ui:field="panel" styleName="layerContainer-container"/> as the template root, replacing new FlowPanel() + addStyleName |

**Stays in Java:** LayerContainerImpl.java:117 widget.addStyleName("layerContainer-layer") on each layer added at runtime: a fixed class, but on children added at runtime; LayerContainerImpl.java:78 layer.setLayerVisible(...): state (layerContainer-layer--visible is set by the layers); The commented-out fade/TransitionTimer code (:38, :58-62, :70-75, :87-94, :103, :121-178) is dead, but it is left until in-flight work completes (removals-wait-for-other-work).

## `UserTaskPresenter`

composite, tidy; complexity 1, effort S; used by 3.

**GWT button.** It still uses GWT's own `Button` (`com.google.gwt.user.client.ui.Button`), which nothing may use (agreed 24 October 2026): `UserTaskViewImpl.ui.xml`'s Terminate (`<g:Button ui:field="terminate">`). Replace it in this wave, with a Stroom icon button in a toolbar as its plan says (the red 12px look moves to a class on it), and check the look against the screenshots.

- **Component:** `stroom.task.client.component.UserTask`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/task/client/view/UserTaskViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/task/client/component/UserTask.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/task/client/presenter/UserTaskPresenter.java (and the nested UserTaskView interface)`; stroom-core-client/src/main/java/stroom/task/client/presenter/UserTaskUiHandlers.java (UserTaskManagerPresenter.java:54 stops implementing it); `stroom-core-client/src/main/java/stroom/task/client/view/UserTaskViewImpl.java`; `stroom-core-client/src/main/resources/stroom/task/client/view/UserTaskViewImpl.ui.xml (moved and renamed)`; stroom-core-client/src/main/java/stroom/task/client/gin/TaskModule.java:32 bindPresenterWidget(UserTaskPresenter, UserTaskView, UserTaskViewImpl); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/TaskScreenModule.java:39-41 bindPresenterWidget(UserTaskPresenter, ...)
- **Names:** The Becomes column keeps UserTaskPresenter in the presenter package, but it is a presenter unit, so rule 1 moves it to stroom.task.client.component. UserTask is free; avoid TaskProgress, which is the shared DTO it shows. It is a dumb row with no controller: UserTaskManagerPresenter (:58,:61,:77,:164-198) makes one per task (Provider<UserTask> or new UserTask()) and sets setOnTerminate(Runnable), since the owner knows the row's TaskProgress.

| Into the template | What | Detail |
| --- | --- | --- |
| UserTaskViewImpl.java:54 | structure | The STOP icon: with a Stroom button it becomes svg="STOP" in the template instead of SvgImageUtil.setSvgAsInnerHtml |
| UserTaskViewImpl.ui.xml:9-11 | component-declaration | Replace the lone g:Button with <b:Toolbar name="Task actions"><b:ToolbarButton ui:field="terminate" svg="STOP" title="Terminate" visible="false"/></b:Toolbar> (wave 1, per the rule that buttons are always in a toolbar). The red 12px look (.gwt-Button.userTaskView-terminate, components.css:125-147) has to move to a class on the ToolbarButton, and the screenshots will differ, so this needs agreeing. The alternative is to keep the g:Button and move only the class. |
| UserTaskViewImpl.ui.xml:3-8 | css-class | Moved unchanged (userTaskView-layout/-details/-taskName/-terminateCell, components.css:102-150). They could be renamed to BEM user-task__* in place, as they belong to this row alone. |

**Stays in Java:** UserTaskViewImpl.java:64 and UserTaskPresenter.java:39-40 task name, age (ModelStringUtil.formatDurationString) and status text: data; UserTaskViewImpl.java:80 terminate visible: state.

## `AbstractTabBar`

widget, tidy; complexity 5, effort L; used by 2.

- **Component:** `stroom.widget.tab.client.view.AbstractTabBar`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it is the shared base of CurveTabBar and LinkTabBar, and the fixed DOM it needs (root, tablist, overflow selector) moves into each subclass's element template; it gets the elements through a protected constructor)`
- **Goes:** stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/AbstractTabSelector.java (with CurveTabSelector and BasicTabSelector, units 38/39: the selector becomes plain elements in each bar's template); `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/DraggableTabBar.java (merged into CurveTabBar, its only subclass)`; stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/DraggedTab.java (becomes a private record at the bottom of CurveTabBar) **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**
- **Names:** The name is kept and the class is not merged: its 1,025 lines of keyboard, overflow and resize logic are shared by two subclasses that differ only in their tabs. DraggableTabBar (126 lines), which only CurveTabBar extends, is merged into CurveTabBar instead. Its separator code (DraggableTabBar.java:113-118) does nothing there, because CurveTabBar has no separators (createSeparator returns null), so it is dropped. AbstractTab stays the base of CurveTab and LinkTab, since the bar needs one type for both. The TabBar interface (stroom.widget.tab.client.presenter.TabBar) stays where it is under rule 5: eight view interfaces return it (CurveTabLayoutView, LinkTabsLayoutView, QueryResultTabsView, UserPreferencesView, DataView, LinkTabPanelView, FloorMapDock...). It can move to the view package when they go. The base changes from FlowPanel to ComplexPanel so that it can setElement(root) from the subclass's UiBinder<DivElement> template. Tabs stay widgets, inserted into the tablist element. **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| AbstractTabBar.java:353 | `separator.getStyle().setLeft(x, Unit.PX)` | dynamic | style.setProperty("--tab-bar-left", x + "px"); in tab-bar.css, .tab-bar__separator, .tab-bar__tab, .tab-bar__selector { left: var(--tab-bar-left, 0); } |
| AbstractTabBar.java:648-649 | `element.getStyle().setVisibility(HIDDEN); setLeft(-1000, PX) (makeInvisible)` | static | State class tab-bar__tab--hidden (and tab-bar__selector--hidden for the selector), new in tab-bar.css: { visibility: hidden; left: -1000px; }, added and removed by makeInvisible/makeVisible |
| AbstractTabBar.java:653-654 | `element.getStyle().setLeft(x, PX); setVisibility(VISIBLE) (makeVisible)` | dynamic | Remove the --hidden modifier and set --tab-bar-left on the element (rule above in tab-bar.css) |
| DraggableTabBar.java:116 | `separator.getStyle().setLeft(x, Unit.PX) while measuring` | dynamic | None: the code is dropped when DraggableTabBar is merged into CurveTabBar, which has no separators. It also appended a new separator on every measurement. | **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**

| Into the template | What | Detail |
| --- | --- | --- |
| AbstractTabBar.java:87-90 | structure | <div ui:field="tabList" class="tab-bar__tabs" role="tablist"/> moves into CurveTabBar.ui.xml and LinkTabBar.ui.xml, passed to the protected constructor AbstractTabBar(Element root, Element tabList, Element selector, Element selectorCount) |
| AbstractTabBar.java:1008-1014 (getTabSelector: createTabSelector, makeInvisible, appendChild) | structure | Each bar's template declares the selector: <div ui:field="selector" class="tab-bar__selector tab-bar__selector--hidden" role="button" aria-haspopup="menu" aria-expanded="false" aria-label="Show all tabs"><div ui:field="selectorArrows" class="tab-bar__selector-arrows"/><div ui:field="selectorCount" class="tab-bar__selector-count"/></div>; createTabSelector() goes | **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**
| AbstractTabBar.java:685-688 (initTabElement) | aria | role="tab", tabindex="-1" and aria-selected="false" go into the CurveTab and LinkTab templates; the unique id and aria-controls stay in Java |
| AbstractTabBar.java:953-966 (addSelectionHandler/addRequestCloseTabHandler/addShowMenuHandler) | component-declaration | Add setOnSelect(Consumer<TabData>), setOnClose(Consumer<TabData>) and setOnShowMenu(...) callbacks beside the GwtEvent channels, which stay until every TabBar user has moved (rule 5) |

**Stays in Java:** AbstractTabBar.java:224-226 tab-bar--multiple: a state class (number of tabs); AbstractTabBar.java:295 tab.setText(tabData.getLabel()): data; AbstractTabBar.java:613-622 separators made per gap (one for each pair of visible tabs, data-driven), with aria-hidden on each; AbstractTabBar.java:639-640 the overflow count and aria-label "Show all tabs (N hidden)": built from data; AbstractTabBar.java:660-665 setAccessibleName: stays as a setter, so a template can give accessibleName="..."; AbstractTabBar.java:670-675, 692-697, 700-716 tabpanel role, aria-controls, aria-selected and aria-labelledby on the panel given and on the selected tab: they change with state; AbstractTabBar.java:905-937 the All Tabs menu items (IconMenuItem, bold for visible tabs): built from data; AbstractTab.java:46-59 the TaskMonitorFactory (spinner per tab): behaviour, kept.

## `VisToolbarRow`

widget, tidy; complexity 2, effort S; used by 2.

- **Component:** `stroom.dashboard.client.vis.VisToolbarRow`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisToolbarRow.ui.xml`
- **Names:** The widget keeps its name and package. It changes from extending FlowPanel to extending Component. setVisButtons(buttons, onPress) becomes setVisButtons(buttons) plus setOnPress(BiConsumer<String, Boolean>). getRefreshButton stays for VisViewImpl :95 and QueryResultVisViewImpl :98 (and their presenters) until they are split; it is made with new VisToolbarRow() at VisViewImpl.java:53 and QueryResultVisViewImpl.java:56, which can then declare it in their templates. VisToolbarButton stays as its data.

| Into the template | What | Detail |
| --- | --- | --- |
| VisToolbarRow.java:42 | css-class | Root <g:FlowPanel styleName="pager dock-container-horizontal dock-min align-center visToolbarRow"> (dashboard.css:414, components.css:294) |
| VisToolbarRow.java:44-50 | structure | <g:FlowPanel styleName="pager-toolbar"><b:Toolbar ui:field="visToolbar" name="Visualisation actions" visible="false"/></g:FlowPanel> |
| VisToolbarRow.java:52-55 | component-declaration | <b:Toolbar name="Visualisation updates"><r:RefreshButton ui:field="refreshButton" allowPause="true"/></b:Toolbar> (Toolbar wave 1, RefreshButton wave 1, already declared this way in Pager.ui.xml:22) |

**Stays in Java:** VisToolbarRow.java:69-96 the visualisation's buttons and toggles (icons, titles, state, enabled), made through Toolbar.button/toggle: data from vis.js; VisToolbarRow.java:96 visToolbar visible while it has buttons: state.

## `QueryButtons`

widget, tidy; complexity 1, effort S; used by 5.

- **Component:** `stroom.query.client.view.QueryButtons`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/QueryButtons.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/view/QueryButtons.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/query/client/presenter/QueryUiHandlers.java (after QueryPresenter.java:171 and QueryToolbarPresenter.java:45,65 use setOnStart(Runnable))
- **Names:** The widget keeps its name and package. HasUiHandlers<QueryUiHandlers> becomes setOnStart(Runnable). onSearching (SearchStateListener) gets a plain setSearching(boolean); the listener interface stays while QueryViewImpl.java:78 and the search model still call it (rule 5). QueryDataViewImpl.java:45-51 reuses its QueryButtons-button classes; that is not part of this unit.

| Into the template | What | Detail |
| --- | --- | --- |
| QueryButtons.java:47 | structure | svg="PLAY" on <b:ToolbarButton ui:field="start"> |
| QueryButtons.java:49 | structure | shortcut="OK" (ToolbarButton.setShortcut takes KeyBinding.Action, an enum, which UiBinder can parse; check that it compiles) |
| QueryButtons.java:67,74 | css-class | QueryButtons-button is fixed (already in the template at :6), so Java only toggles play and stop |
| QueryButtons.ui.xml:4-7 | component-declaration | Moved beside the Java; Toolbar and ToolbarButton (wave 1) are already declared |

**Stays in Java:** QueryButtons.java:66-79 the searching state: the play/stop state classes (stroom-dashboard.css:39-50), the icon PLAY/STOP, the shortcut OK/CLOSE and the title "Execute Query"/"Stop Query"; QueryButtons.java:82-84 setEnabled: state.

## `TextBoxPopup`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.widget.popup.client.component.NameDialog`
- **Controller:** `stroom.widget.popup.client.component.NameDialogController`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/popup/client/view/TextBoxViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/component/NameDialog.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/presenter/TextBoxPopup.java (and its nested TextBoxView interface)`; `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/TextBoxViewImpl.java`; `stroom-core-client-widget/src/main/resources/stroom/widget/popup/client/view/TextBoxViewImpl.ui.xml (moved and renamed)`; stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:176 bindPresenterWidget(TextBoxPopup, TextBoxView, TextBoxViewImpl) and imports :81-83; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:96 bindPresenterWidget(TextBoxPopup, ...) and imports :65-67
- **Names:** TextBoxPopup is already a class name: the presenter, which is deleted. The component is dialog content (a required Name field), not a popup, so it is named NameDialog / NameDialogController (both free). The Becomes column's TextBoxPopupController would also have worked. Rule 1 puts it in stroom.widget.popup.client.component. TestTextBoxPopup (NO_NAME_MESSAGE) becomes TestNameDialogController, TextBoxPopupDialogStories (:61 builds TextBoxViewImpl) moves to the new pair, and TabSessionManager calls controller.show(caption, consumer) through ControllerDialog.showOnce. Although the unit's notes say no controller is needed, it gets one: the design pairs every dialog with a controller, which shows it and handles OK.

| Into the template | What | Detail |
| --- | --- | --- |
| TextBoxViewImpl.ui.xml:5-15 | component-declaration | Moved unchanged: <form:FormGroup identity="textBoxFieldName" label="Name" helpText=... required="true"> (wave 1, already declared), with g:TextBox ui:field="textBox" and g:Label ui:field="feedback" styleName="feedback" (ChangePassword.css:13); root styleName="max form" |

**Stays in Java:** TextBoxPopup.java:32 NO_NAME_MESSAGE "You must provide a name" and :52-66 OK validation (setInvalid/setValid): set at runtime by the controller's hide-request handler; TextBoxPopup.java:46-51 popup type OK_CANCEL_DIALOG, PopupSize.resizableX(), caption (from the caller) and focus on show: dialog chrome, in the controller; TextBoxViewImpl.java:153,159 FieldValidity.setInvalid/setValid: state.

## `GroupLevelButtons`

widget, tidy; complexity 1, effort S; used by 3.

- **Component:** `stroom.dashboard.client.table.GroupLevelButtons`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because, as the design's 'a set of actions' rule says, it is given another part's toolbar (the table's pager toolbar, or TraceOverviewWidget's expandControls) and adds its two buttons to it with Toolbar.button(icon, name, onClick))`
- **Names:** The name stays and it is unchanged. It is not a widget: it already follows the design's rule for buttons that go on another part's toolbar. Users: TablePresenter :249, QueryResultTablePresenter :231, TraceOverviewWidget :173.

**Stays in Java:** GroupLevelButtons.java:50-55 the Expand/Collapse buttons, with their icons and names, are added to the given toolbar through its API, disabled at first; GroupLevelButtons.java:67-77 enabled state and titles ("Expand Groups to Level N"): built from data.

## `MainToolbar`

composite, tidy; complexity 1, effort S; used by 2.

- **Component:** `stroom.main.client.view.MainToolbar`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/main/client/view/MainToolbar.ui.xml` → `stroom-core-client/src/main/java/stroom/main/client/view/MainToolbar.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/main/client/presenter/MainUiHandlers.java (MainPresenter.java:65 stops implementing it and :90 sets the two callbacks instead; AskStroomAiDialogStories.java:295 likewise)
- **Names:** The widget keeps its name and package. setMainUiHandlers becomes setOnShowMenu(BiConsumer<NativeEvent, Element>) and setOnShowAi(BiConsumer<NativeEvent, Element>), or Consumer<Element> if MainPresenter only needs the anchor. The unit's "declares" list names ValueSpinner, but the template declares stroom.main.client.view.Spinner (wave 1, unit 486), not the value spinner.

| Into the template | What | Detail |
| --- | --- | --- |
| MainToolbar.ui.xml:6-12 | component-declaration | Moved beside the Java unchanged. It already declares s:Spinner, b:Toolbar name="Application", b:ToolbarToggleButton svg="AI" title="Ask Stroom AI" and b:ToolbarButton svg="ELLIPSES_VERTICAL" title="Show Main Menu" opensMenu="true". |

**Stays in Java:** MainToolbar.java:49-55 getSpinner/getShowAi getters: MainPresenter.java:203 drives the spinner and the AI toggle's state; they can stay until MainPresenter is split; MainToolbar.java:71 passes menu.getElement() as showAi's target. It probably should be showAi.getElement(); check this when the callback replaces it..

## `CurveTabBar`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.widget.tab.client.view.CurveTabBar`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/tab/client/view/CurveTabSelector.ui.xml (its selector markup is folded into CurveTabBar.ui.xml)` → `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/CurveTabBar.ui.xml (and CurveTab.ui.xml beside it)` **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/view/CurveTabSelector.java`; `stroom-core-client-widget/src/main/resources/stroom/widget/tab/client/view/CurveTabSelector.ui.xml`; `DraggableTabBar.java (merged in, see unit 37)` **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**
- **Names:** The widget keeps its name and package. DraggableTabBar's drag logic merges into it (its only subclass), and DraggedTab becomes a private record at the bottom of the class. CurveTabSelector goes because the selector is now three elements in the bar's template; BasicTabSelector (unit 39) was an identical copy without a template. **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| CurveTab.java:86 | `close.getStyle().setDisplay(Display.NONE) when not closable` | static | Modifier tab-bar__tab--fixed on the tab, new in tab-bar.css: .tab-bar__tab--fixed .tab-bar__close { display: none; } |
| CurveTab.java:87 | `label.getStyle().setPaddingRight(20, Unit.PX) when not closable` | static | Same modifier: .tab-bar--curved .tab-bar__tab--fixed .tab-bar__label { padding-right: 20px; } in tab-bar.css |

| Into the template | What | Detail |
| --- | --- | --- |
| CurveTabBar.java:29-30 | css-class | Template root <div class="tab-bar tab-bar--curved"> with the tablist <div ui:field="tabList" class="tab-bar__tabs" role="tablist" aria-label="Open documents"/> (setAccessibleName("Open documents") moves too) |
| CurveTabBar.java:55-57 + CurveTabSelector.java:38-45 + CurveTabSelector.ui.xml:3-6 | structure | Selector in the bar's template: class tab-bar__selector, role="button", aria-haspopup="menu", aria-expanded="false", aria-label="Show all tabs", with children tab-bar__selector-arrows and tab-bar__selector-count | **(Superseded, see "Decided before starting": a `TabSelector` widget; `DraggableTabBar` kept.)**
| CurveTab.java:49-98 | structure | CurveTab.ui.xml (g:HTMLPanel root, CurveTab becomes a Composite): <div class="tab-bar__tab" role="tab" tabindex="-1" aria-selected="false" draggable="true"> containing an icon <div ui:field="icon" class="tab-bar__icon" aria-hidden="true"/>, <div ui:field="label" class="tab-bar__label"/> and <div class="tab-bar__end"> holding the spinner and <div ui:field="close" class="tab-bar__close" aria-hidden="true"/> |
| CurveTab.java:90-91 | component-declaration | <sp:SpinnerSmall ui:field="spinner" addStyleNames="tab-bar__spinner"/> (wave 1): declared, so attached as a widget. Today only its element is appended, at :95. |
| DraggableTabBar.java:45 | structure | draggable="true" goes onto the tab in CurveTab.ui.xml |

**Stays in Java:** CurveTabSelector.java:39 and CurveTab.java:78 SvgImageUtil.setSvgAsInnerHtml(DOUBLE_ARROW / TAB_CLOSE): no template element shows an SvgImage yet (see cssNotes); CurveTab.java:54-62 the icon, its colour class (iconColour.getClassName()) and its title: per-tab data. The icon element is hidden when there is no icon.; CurveTab.java:67-73 label text (cut to 50 characters) and title: data; CurveTab.java:85-88 tab-bar__tab--fixed is added when !allowClose (data); CurveTabBar.java:36,41 GlobalResizeObserver on attach and detach: behaviour; CurveTabBar.java:63-65 getTabGap 6: layout maths used for positions.

## `Dialog`

widget, tidy; complexity 3, effort M; used by 1.

- **Component:** `stroom.widget.popup.client.view.Dialog`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/popup/client/view/Dialog.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/Dialog.ui.xml`
- **Goes:** the Dialog(DialogActionUiHandlers[, autoHide[, modal]]) constructors (Dialog.java:86-127): a no-arg Dialog with setAutoHide, setModal and setOnAction; `implements TaskMonitorFactory (Dialog.java:50, :328): setBusy(boolean)`
- **Names:** Dialog keeps its name and package. ResizableDialog stays a unit of its own (not merged; decided 24 October 2026). DialogWindow and DialogFocusTrap are package-private helpers that keep their names. DialogWindow's window menu already goes through MenuRequest, which is allowed, so it needs no controller.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/DialogWindow.java:350 | `widget.getElement().getStyle().setPropertyPx("width", bounds.width)` | dynamic | --dialog__width on dialog__container (the same property as ResizableDialog), in dialog.css |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/DialogWindow.java:353 | `widget.getElement().getStyle().setPropertyPx("height", bounds.height)` | dynamic | --dialog__height on dialog__container, in dialog.css |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/Dialog.java:88 | component-declaration | <spinner:SpinnerLarge ui:field="spinner" visible="false"/>. SpinnerLarge, Toolbar, ToolbarButton and the window button (title="Window Options") are already declared |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/DialogWindow.java:92 | aria | role="status" on <div ui:field="hint" class="dialog__hint"/> (the template root becomes an HTMLPanel) |

**Stays in Java:** setStyleName("dialog") and PopupUtil.setDialogRole (Dialog.java:119-121): on the PopupPanel root; the caption and icon (Dialog.java:131-143): from the caller; dialog--moving and dialog--resizing (DialogWindow.java:202): state; DialogFocusTrap's data attribute (DialogFocusTrap.java:40): a marker set at run time; the popup's left and top: GWT PopupPanel.

## `OkCancelContent`

widget, tidy; complexity 2, effort M; used by 1.

- **Components:** `OkCancelContent`, `CloseContent`, `AcceptRejectContent` and `CreateOkCancelContent` in `stroom.widget.popup.client.view`: four small components, each its template (the screen's content, `dialog__content`, then its buttons, `dialog__buttons`), used by both dialog kinds (decided 24 October 2026: not one bar with an action set).
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/popup/client/view/{OkCancel,Close,AcceptReject}Content.ui.xml` → beside their Java in `src/main/java`; `ResizableCreateOkCancelContent.ui.xml` → `CreateOkCancelContent.ui.xml` beside its Java.
- **Goes:** `ResizableOkCancelContent`, `ResizableCloseContent` and `ResizableAcceptRejectContent` (Java and templates): copies of the first three that differ only in their names (and the accept/reject template's unused `resizableAcceptRejectContent-*` classes); `ResizableCreateOkCancelContent` is renamed `CreateOkCancelContent`, as it has no fixed-size twin. PopupSupportImpl.java:200-299, the only maker, uses the four for both dialogs. The `HideRequestUiHandlers` constructor argument: a no-arg constructor and `setOnHideRequest(Consumer<HideRequest>)`, so a template could declare them.
- **Names:** "Content" is what they are: the dialog's content and its buttons (`dialog.css` calls it the content template inside `dialog__body`). `DialogButtons` is the interface they implement.

| Into the template | What | Detail |
| --- | --- | --- |
| OkCancelContent.java:50-51; CloseContent.java:44; AcceptRejectContent.java:55-56; ResizableCreateOkCancelContent.java:55-57 | component-declaration | The buttons' icons as template attributes (`Button.setIcon(SvgImage)`): ok and accept `icon="OK"`, cancel, close and reject `icon="CANCEL"`, create `icon="ADD"` |
| AcceptRejectContent.ui.xml | css-class | acceptRejectContent-button, -accept and -reject (components.css:610-646) become BEM in the content's own stylesheet, or are dropped if nothing styles them differently from the dialog's other buttons |

**Stays in Java:** enabled, loading and focus of the buttons around a hide request (OkCancelContent.java:56-110): state.

## `ResizableDialog`

widget, tidy; complexity 3, effort M; used by 1.

- **Component:** `stroom.widget.popup.client.view.ResizableDialog` (not merged into `Dialog`: decided 24 October 2026; they share `DialogWindow`, `DialogFocusTrap` and `AbstractPopupPanel`, and anything more in common goes into a helper)
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/popup/client/view/ResizableDialog.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/ResizableDialog.ui.xml`
- **Goes:** `the DialogActionUiHandlers and DialogButtons constructor arguments: a no-arg ResizableDialog with setPopupSize(PopupSize), setOnAction(Consumer<DialogAction>) and setContent`; implements TaskMonitorFactory (ResizableDialog.java:56, :564): setBusy(boolean) on the spinner, with a small adapter from Contexts.TASK_MONITOR
- **Names:** Keeps its name: it is the dialog that can be resized.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/ResizableDialog.java:389 | `elem.getStyle().setPropertyPx("width", constrainedWidth)` | dynamic | --dialog__width on the dialog__container element, used by .dialog--resizable .dialog__container { width: var(--dialog__width, auto) } (dialog.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/ResizableDialog.java:413 | `elem.getStyle().setPropertyPx("height", constrainedHeight)` | dynamic | --dialog__height on dialog__container, used by .dialog--resizable .dialog__container { height: var(--dialog__height, auto) } (dialog.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/ResizableDialog.java:550-560 | `resizeN/E/S/W/NE/NW/SE/SW.setVisible(...) (inline display)` | static | New modifiers dialog--resize-width and dialog--resize-height on the dialog. dialog.css hides .dialog__resize by default and shows --e and --w under --resize-width, --n and --s under --resize-height, and the corners under both |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/ResizableDialog.java:119 | component-declaration | <spinner:SpinnerLarge ui:field="spinner" visible="false"/>, already declared, with the initial visible="false" moved into the template |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/ResizableDialog.java:177 | component-declaration | The resize handle's RESIZE_HANDLE icon: `<svg:Icon image="RESIZE_HANDLE"/>` in the south-east handle (the new `Icon` widget) |
| stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/DialogWindow.java:92 | aria | role="status" on the hint. The template's root becomes a g:HTMLPanel class="dialog__container" so that the hint is <div ui:field="hint" class="dialog__hint" role="status"/> |

**Stays in Java:** setStyleName("dialog"), addStyleName("dialog--resizable") and PopupUtil.setDialogRole (role="dialog", aria-labelledby) (ResizableDialog.java:153-156): on the PopupPanel's own element, which no template makes; the title text and icon (ResizableDialog.java:181-193): from the caller; dialog--moving and dialog--resizing state classes (DialogWindow.java:202); position (setPopupPosition), set by GWT's PopupPanel; PopupUtil.java:165,168 also sets the popup's width and height inline (outside this unit; PopupUtil should use the same --dialog__width and --dialog__height).

## `OperatorEditor`

widget, tidy; complexity 1, effort S; used by 2.

- **Component:** `stroom.query.client.OperatorEditor`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/query/client/OperatorEditor.ui.xml`
- **Goes:** The EventBinder field (OperatorEditor.java:41-49) and onLoad/onUnload binding (:70-80): a @UiHandler on the SelectionBox's ValueChangeEvent replaces them
- **Names:** The widget keeps its name and package (rule 1). ExpressionUiHandlers (setUiHandlers at :129) is replaced by setOnChange(Runnable), with TermEditor, since ExpressionItemRenderer and ExpressionTreePanel (:74 new OperatorEditor()) drive both. ExpressionUiHandlers itself stays while TermEditor and others still use it.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| OperatorEditor.java:135 (called at :56 with 55) | `widget.getElement().getStyle().setWidth(55, Unit.PX)` | static | New modifier .termEditor-item.operator { width: 55px; } in TermEditor.css, following that file's modifiers (wide, narrow, condition, docRef) |

| Into the template | What | Detail |
| --- | --- | --- |
| OperatorEditor.java:52-67 | structure | <g:FlowPanel ui:field="layout" styleName="termEditor-outer" visible="false"><g:FlowPanel ui:field="inner" styleName="termEditor-inner"> ... </g:FlowPanel></g:FlowPanel> |
| OperatorEditor.java:52,56,134 | component-declaration | <i:SelectionBox ui:field="listBox" addStyleNames="termEditor-item operator"/> (wave 2) |
| OperatorEditor.java:53 | aria | ariaLabel="Operator" on the SelectionBox. This needs a setAriaLabel(String) on BaseSelectionBox (in wave 2's tidy, as LinkButton has ariaLabel); until then AccessibleName.set stays in Java |

**Stays in Java:** OperatorEditor.java:54 listBox.addItems(Op.values()): the item list; OperatorEditor.java:89,91,108 the value and the shown or hidden state during an edit (startEdit/endEdit); OperatorEditor.java:116 Operator mutation: per the unit's notes it moves to the tree's owner, with the component reporting through setOnChange.

