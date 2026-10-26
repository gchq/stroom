/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.client.main;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.core.client.UrlParameters;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.flexlayout.FlexLayout;
import stroom.dashboard.client.flexlayout.FlexLayoutChangeHandler;
import stroom.dashboard.client.flexlayout.MutableLayoutConfig;
import stroom.dashboard.client.flexlayout.MutableSize;
import stroom.dashboard.client.flexlayout.MutableSplitLayoutConfig;
import stroom.dashboard.client.flexlayout.MutableTabConfig;
import stroom.dashboard.client.flexlayout.MutableTabLayoutConfig;
import stroom.dashboard.client.input.KeyValueInputPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.DashboardPresenter.DashboardView;
import stroom.dashboard.client.query.CurrentSelectionPresenter;
import stroom.dashboard.client.query.QueryInfo;
import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.client.table.TableChange;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.client.text.TextPresenter;
import stroom.dashboard.client.vis.VisPresenter;
import stroom.dashboard.shared.AbstractQueryComponentSettings;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.HasSelectionFilter;
import stroom.dashboard.shared.HasSelectionQuery;
import stroom.dashboard.shared.KeyValueInputComponentSettings;
import stroom.dashboard.shared.LayoutConstraints;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.HasChangeHandlers;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Format;
import stroom.query.api.ParamUtil;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.Sort.SortDirection;
import stroom.query.client.presenter.QueryToolbarPresenter;
import stroom.query.client.presenter.SearchErrorListener;
import stroom.query.client.presenter.SearchStateListener;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.util.client.JSONUtil;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Version;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.menu.client.presenter.SimpleMenuItem;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DashboardPresenter
        extends DocPresenter<DashboardView, DashboardDoc>
        implements
        FlexLayoutChangeHandler,
        DocumentTabData,
        HasToolbar,
        SearchStateListener,
        SearchErrorListener {

    private static final String VERSION_7_2_0 = Version.of(7, 2, 0).toString();
    private static final String DEFAULT_PARAMS_INPUT = "Params";
    // What starts the line of a Visualisation's render report in the description, and the most of it given.
    private static final String RENDER_REPORT = "Render report: ";
    private static final int MAX_RENDER_REPORT_CHARS = 600;

    private static final Logger logger = Logger.getLogger(DashboardPresenter.class.getName());
    private final FlexLayout layoutPresenter;
    private final Components components;
    // Changes made programmatically that have been applied, by their undo key, so each can be undone while the
    // dashboard is as the change left it.
    private final Map<String, AppliedChanges> appliedChanges = new HashMap<>();
    private final QueryToolbarPresenter queryToolbarPresenter;
    private final QueryInfo queryInfo;
    private final Provider<LayoutConstraintPresenter> layoutConstraintPresenterProvider;
    private final Provider<CurrentSelectionPresenter> currentSelectionPresenterProvider;
    private final Provider<DocSelectionPopup> dashboardSelection;
    private CurrentSelectionPresenter currentSelectionPresenter;
    private String lastLabel;
    private boolean loaded;
    private String customTitle;
    private DocRef docRef;

    private boolean embedded;
    private boolean queryOnOpen;

    private LayoutConstraints layoutConstraints = new LayoutConstraints(true, true);
    private MutableSize preferredSize = new MutableSize();
    private boolean designMode;

    private ResultStoreInfo resultStoreInfo;
    private String externalLinkParameters;

    private final DashboardContextImpl dashboardContext;
    private final InlineSvgToggleButton editModeButton;
    private final InlineSvgButton addComponentButton;
    private final InlineSvgButton setConstraintsButton;
    private final InlineSvgButton selectionInfoButton;
    private final InlineSvgButton maximiseTabsButton;
    private final InlineSvgButton restoreTabsButton;
    private final ButtonPanel editToolbar;

    @Inject
    public DashboardPresenter(final EventBus eventBus,
                              final DashboardView view,
                              final FlexLayout flexLayout,
                              final Components components,
                              final QueryToolbarPresenter queryToolbarPresenter,
                              final Provider<RenameTabPresenter> renameTabPresenterProvider,
                              final QueryInfo queryInfo,
                              final Provider<LayoutConstraintPresenter> layoutConstraintPresenterProvider,
                              final Provider<CurrentSelectionPresenter> currentSelectionPresenterProvider,
                              final UrlParameters urlParameters,
                              final Provider<DocSelectionPopup> dashboardSelection) {
        super(eventBus, view);
        this.queryToolbarPresenter = queryToolbarPresenter;
        this.layoutPresenter = flexLayout;
        this.components = components;
        this.queryInfo = queryInfo;
        this.layoutConstraintPresenterProvider = layoutConstraintPresenterProvider;
        this.currentSelectionPresenterProvider = currentSelectionPresenterProvider;
        this.dashboardSelection = dashboardSelection;

        dashboardContext = new DashboardContextImpl(eventBus, components, queryToolbarPresenter);
        queryToolbarPresenter.setParamValues(dashboardContext);

        final TabManager tabManager = new TabManager(components, renameTabPresenterProvider, this);
        flexLayout.setTabManager(tabManager);

        flexLayout.setChangeHandler(this);
        flexLayout.setComponents(components);
        view.setContent(flexLayout);

        editModeButton = new InlineSvgToggleButton();
        editModeButton.setSvg(SvgImage.EDIT);
        editModeButton.setTitle("Enter Design Mode");

        addComponentButton = new InlineSvgButton();
        addComponentButton.setSvg(SvgImage.ADD);
        addComponentButton.setTitle("Add Component");
        addComponentButton.setVisible(false);

        setConstraintsButton = new InlineSvgButton();
        setConstraintsButton.setSvg(SvgImage.RESIZE);
        setConstraintsButton.setTitle("Set Constraints");
        setConstraintsButton.setVisible(false);

        selectionInfoButton = new InlineSvgButton();
        selectionInfoButton.setSvg(SvgImage.SELECTION);
        selectionInfoButton.setTitle("View Current Selection");
        selectionInfoButton.setVisible(false);

        maximiseTabsButton = new InlineSvgButton();
        maximiseTabsButton.setSvg(SvgImage.MAXIMISE);
        maximiseTabsButton.setTitle("Maximise");

        restoreTabsButton = new InlineSvgButton();
        restoreTabsButton.setSvg(SvgImage.MINIMISE);
        restoreTabsButton.setTitle("Restore");
        restoreTabsButton.setVisible(false);

//                <g:FlowPanel styleName="DashboardViewImpl-top dock-min dock-container-horizontal">
//            <g:FlowPanel styleName="dock-max">
//                <g:Button ui:field="designModeButton" text="Enter Design Mode" width="200px"/>
//            </g:FlowPanel>
//        </g:FlowPanel>
//        <g:FlowPanel
//                styleName="DashboardViewImpl-top DashboardViewImpl-designButtons dock-min dock-container-horizontal">
//            <g:FlowPanel styleName="dock-min DashboardViewImpl-top-buttons">
//                <g:Button ui:field="addPanelButton" text="Add Panel"/>
//                <g:Button ui:field="addInputButton" text="Add Input"/>
//                <g:Button ui:field="constraintsButton" text="Constraints"/>
//            </g:FlowPanel>
//        </g:FlowPanel>


        editToolbar = new ButtonPanel();
        editToolbar.addButton(editModeButton);
        editToolbar.addButton(addComponentButton);
        editToolbar.addButton(setConstraintsButton);
        editToolbar.addButton(selectionInfoButton);
        editToolbar.addButton(maximiseTabsButton);
        editToolbar.add(restoreTabsButton);

        NullSafe.consumeNonBlankString(urlParameters.getTitle(), true, this::setCustomTitle);
//        final String linkParams = ;
        setParamsFromLink(urlParameters.getParams());
        setEmbedded(urlParameters.isEmbedded());
        setQueryOnOpen(urlParameters.isQueryOnOpen());
//        dashboardPresenter.setParamsFromLink(params);
//        dashboardPresenter.setEmbedded(embedded);
//        dashboardPresenter.setQueryOnOpen(queryOnOpen);
    }

    public void setParentContext(final DashboardContext parent) {
        dashboardContext.setParent(parent);
    }

    @Override
    public List<Widget> getToolbars() {
        final List<Widget> list = new ArrayList<>();
        list.add(editToolbar);
        list.add(queryToolbarPresenter.getWidget());
        return list;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(queryToolbarPresenter.addStartQueryHandler(e -> toggleStart()));
        registerHandler(queryToolbarPresenter.addTimeRangeChangeHandler(e -> {
            onChange();
            dashboardContext.fireContextChangeEvent();
            start();
        }));
        registerHandler(editModeButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onDesign();
            }
        }));
        registerHandler(addComponentButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onAdd(e);
            }
        }));
        registerHandler(setConstraintsButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onConstraints();
            }
        }));
        registerHandler(selectionInfoButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onSelectionInfo();
            }
        }));
        registerHandler(maximiseTabsButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                maximiseTabs(null);
            }
        }));
        registerHandler(restoreTabsButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                restoreTabs();
            }
        }));
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();

        // Remove all components. This should have been done already in the
        // onClose() method.
        components.removeAll();
    }

    private void onConstraints() {
        restoreTabs();

        final LayoutConstraintPresenter presenter = layoutConstraintPresenterProvider.get();
        final HandlerRegistration handlerRegistration = presenter.addValueChangeHandler(e -> {
            if (!Objects.equals(e.getValue(), layoutConstraints)) {
                layoutConstraints = e.getValue();
                layoutPresenter.setLayoutConstraints(layoutConstraints);
                onChange();
            }
        });
        presenter.read(layoutConstraints);
        ShowPopupEvent.builder(presenter)
                .popupType(PopupType.CLOSE_DIALOG)
                .caption("Set Layout Constraints")
                .modal(true)
                .onShow(e -> presenter.getView().focus())
                .onHide(e -> handlerRegistration.removeHandler())
                .fire();
    }

    private void onSelectionInfo() {
        if (currentSelectionPresenter == null) {
            currentSelectionPresenter = currentSelectionPresenterProvider.get();
        }

        currentSelectionPresenter.refresh(dashboardContext, false);
        final HandlerRegistration handlerRegistration = dashboardContext.addContextChangeHandler(e ->
                currentSelectionPresenter.refresh(dashboardContext, false));
        ShowPopupEvent.builder(currentSelectionPresenter)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(600, 800))
                .caption("Current Selection")
                .modal(false)
                .onHide(e -> handlerRegistration.removeHandler())
                .fire();
    }

    private void onDesign() {
        setDesignMode(!designMode);
        // Design mode is stored on the document, so switching it is a change to the document like any
        // other. Without this the toggle would leave nothing dirty, and a dashboard saved in design
        // mode could never be taken out of it — there would be nothing for Save to write.
        //
        // Only the user-driven toggle does this. setDesignMode() is also called from onRead(), where
        // marking the document dirty just for opening it would be wrong.
        onChange();
    }

    private void setDesignMode(final boolean designMode) {
        this.designMode = designMode;
        addComponentButton.setVisible(designMode);
        setConstraintsButton.setVisible(designMode);
        selectionInfoButton.setVisible(designMode);
        layoutPresenter.setDesignMode(designMode);
        getView().setDesignMode(designMode);
//        components.forEach(component -> component.setDesignMode(designMode));

        if (designMode) {
            editModeButton.setTitle("Exit Design Mode");
            editModeButton.setState(true);
        } else {
            editModeButton.setTitle("Enter Design Mode");
            editModeButton.setState(false);
        }
    }

//    public boolean isDesignMode() {
//        return designMode;
//    }

    private void onAdd(final ClickEvent event) {
        restoreTabs();

        final Element target = event.getNativeEvent().getEventTarget().cast();

        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft() - 3,
                target.getAbsoluteTop() + target.getClientHeight() + 1);

        final List<Item> inputs = new ArrayList<>();
        final List<Item> panels = new ArrayList<>();
        for (final ComponentType type : components.getComponentTypes()) {
            final SimpleMenuItem item = new SimpleMenuItem.Builder()
                    .text(type.getName())
                    .command(() -> addNewComponent(type))
                    .build();
            if (ComponentUse.INPUT.equals(type.getUse())) {
                inputs.add(item);
            } else {
                panels.add(item);
            }
        }

        final List<Item> menuItems = new ArrayList<>();
        menuItems.add(new SimpleParentMenuItem(
                1,
                SafeHtmlUtil.from("Input"),
                null,
                inputs));
        menuItems.addAll(panels);

        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(this);
    }

    public void setParamsFromLink(final String params) {
        logger.log(Level.INFO, "Dashboard Presenter setParamsFromLink " + params);
        this.externalLinkParameters = params;
        dashboardContext.setLinkParams(ParamUtil.parse(externalLinkParameters));
    }

    void setEmbedded(final boolean embedded) {
        this.embedded = embedded;
        getView().setEmbedded(embedded);
    }

    public void setResultStoreInfo(final ResultStoreInfo resultStoreInfo) {
        this.resultStoreInfo = resultStoreInfo;
    }

    public void setQueryOnOpen(final boolean queryOnOpen) {
        this.queryOnOpen = queryOnOpen;
    }

    @Override
    protected void onRead(final DocRef docRef, final DashboardDoc dashboard, final boolean readOnly) {
        this.docRef = docRef;
        if (!loaded) {
            loaded = true;

            dashboardContext.setDashboardDocRef(docRef);
            components.clear();
            MutableLayoutConfig layoutConfig = null;

            DashboardConfig dashboardConfig = dashboard.getDashboardConfig();
            if (dashboardConfig != null) {
                queryToolbarPresenter.setTimeRange(dashboardConfig.getTimeRange());

                layoutConfig = MutableConfigUtil.fromLayoutConfig(dashboardConfig.getLayout());
                layoutConstraints = dashboardConfig.getLayoutConstraints();
                if (layoutConstraints == null) {
                    layoutConstraints = new LayoutConstraints(true, true);
                }
                preferredSize = MutableConfigUtil.fromSize(dashboardConfig.getPreferredSize());
                if (preferredSize == null) {
                    preferredSize = new MutableSize();
                }

                List<ComponentConfig> componentConfigList = dashboardConfig.getComponents();

                // ADD A KEY/VALUE PARAMETER INPUT BOX FOR BACKWARD COMPATIBILITY.
                if (dashboardConfig.getModelVersion() == null) {
                    if (componentConfigList == null) {
                        componentConfigList = new ArrayList<>();
                        dashboardConfig = dashboardConfig.copy().components(componentConfigList).build();
                    }

                    final String params = NullSafe.string(dashboardConfig.getParameters());

                    componentConfigList
                            .add(new ComponentConfig(
                                    KeyValueInputPresenter.TYPE.getId(),
                                    DEFAULT_PARAMS_INPUT,
                                    DEFAULT_PARAMS_INPUT,
                                    new KeyValueInputComponentSettings(params)));
                    final MutableTabConfig tabConfig = new MutableTabConfig(DEFAULT_PARAMS_INPUT, true);
                    final MutableTabLayoutConfig tabLayoutConfig =
                            new MutableTabLayoutConfig(new MutableSize(200, 76), null);
                    tabLayoutConfig.add(tabConfig);

                    final MutableSplitLayoutConfig splitLayoutConfig = new MutableSplitLayoutConfig(new MutableSize(200,
                            76), Dimension.Y);
                    splitLayoutConfig.add(tabLayoutConfig);
                    splitLayoutConfig.add(layoutConfig);
                    layoutConfig = splitLayoutConfig;

//                    dashboardConfig = dashboardConfig.copy().layout(layoutConfig).build();
                }

                if (componentConfigList != null) {
                    for (final ComponentConfig componentConfig : componentConfigList) {
                        addComponent(componentConfig.getType(), componentConfig);
                    }
                    for (final ComponentConfig componentConfig : componentConfigList) {
                        final Component component = components.get(componentConfig.getId());
                        if (component != null) {
                            component.link();
                        }
                    }
                }

            } else {
                // /**
                // * ADD TEST DATA
                // */
                // final SplitLayoutData down = new
                // SplitLayoutData(Dimension.Y);
                // for (int i = 0; i < 3; i++) {
                // final SplitLayoutData across = new
                // SplitLayoutData(Dimension.X);
                // down.add(across);
                //
                // for (int l = 0; l < 2; l++) {
                // final SplitLayoutData down2 = new
                // SplitLayoutData(Dimension.Y);
                // across.add(down2);
                //
                // for (int j = 0; j < 3; j++) {
                // final TabLayoutData tablayout = new TabLayoutData();
                // down2.add(tablayout);
                //
                // for (int k = 0; k < 2; k++) {
                // final String type = TablePresenter.TYPE;
                // final String id = type + "_" +
                // String.valueOf(System.currentTimeMillis());
                //
                // final ComponentData componentData = new ComponentData();
                // componentData.setId(id);
                //
                // final ComponentPresenter component =
                // componentRegistry.getComponent(type);
                // component.read(componentData);
                //
                // components.add(component);
                // componentViews.put(id, component.getView());
                //
                // final TabData tabData = new TabData();
                // tabData.setId(id);
                // tabData.setName(component.getType() + " " + i + ":" + j + ":"
                // + k);
                //
                // tablayout.add(tabData);
                // }
                // }
                // }
                // }
                // dashboardData.setLayout(down);
                // /**
                // * DONE - ADD TEST DATA
                // */
            }

            // if (dashboardData.getTabVisibility() != null) {
            // tabVisibility.setSelectedItem(dashboardData.getTabVisibility());
            // }

            layoutPresenter.configure(layoutConfig, layoutConstraints, preferredSize);

            // Tell all queryable components whether we want them to query on open.
            for (final Component component : components) {
                if (component instanceof Queryable) {
                    if (resultStoreInfo != null) {
                        final SearchRequestSource searchRequestSource = resultStoreInfo.getSearchRequestSource();
                        if (searchRequestSource != null &&
                            component.getId().equals(searchRequestSource.getComponentId())) {
                            ((Queryable) component).setResultStoreInfo(resultStoreInfo);
                        }
                    }

                    ((Queryable) component).setQueryOnOpen(queryOnOpen);
                }
            }
            resultStoreInfo = null;

            // If we have been given some external link parameters then set those in the "Params" input component if we
            // can find one.
            if (externalLinkParameters != null) {
                // Try to find a Key/Value component to put the params in called "Params".
                for (final Component component : components.getComponents()) {
                    if (component instanceof final KeyValueInputPresenter keyValueInputPresenter) {
                        if (keyValueInputPresenter.getLabel().equals(DEFAULT_PARAMS_INPUT)) {
                            keyValueInputPresenter.setValue(externalLinkParameters);
                            // If we found one then we don't need to treat external parameters as a special case.
                            this.externalLinkParameters = null;
                            break;
                        }
                    }
                }
            }

        }

        // Design mode is a stored toggle, entered only when the flag is explicitly true.
        //
        // A NEW dashboard already arrives with it set: DashboardStoreImpl.getTemplate() applies
        // designMode(true) to the template every new document is created from. So a null flag does
        // NOT mean "new" — it means a dashboard saved before the flag existed, and those must open
        // normally rather than being dragged into edit layout.
        //
        // The same rule is applied on a re-read after a save or save as, rather than forcing design
        // mode on: onWrite now persists the mode the user was working in, so re-reading restores it.
        // Forcing it on there also fired for an ordinary second read, which made this check
        // unreachable in practice and put every dashboard into design mode.
        final DashboardConfig config = dashboard.getDashboardConfig();
        final boolean enterDesignMode = config != null && Boolean.TRUE.equals(config.getDesignMode());
        setDesignMode(enterDesignMode);

        addComponentButton.setEnabled(!readOnly && !embedded);
        setConstraintsButton.setEnabled(!readOnly && !embedded);
        selectionInfoButton.setEnabled(!readOnly && !embedded);
    }

    private Component addComponent(final String type, final ComponentConfig componentConfig) {
        final Component component = components.add(type, componentConfig.getId());
        if (component != null) {
            component.setDashboardContext(dashboardContext);
//            component.setDesignMode(designMode);

            if (component instanceof final HasChangeHandlers hasChangeHandlers) {
                hasChangeHandlers.addChangeHandler(this::onChange);
            }

            // Set params on the component if it needs them.
            if (component instanceof final Queryable queryable) {
                queryable.addSearchStateListener(this);
                queryable.addSearchErrorListener(this);
                queryable.setTaskMonitorFactory(this);
                queryable.setQueryInfo(queryInfo);
            } else if (component instanceof HasTaskMonitorFactory) {
                ((HasTaskMonitorFactory) component).setTaskMonitorFactory(this);
            }

            component.read(componentConfig);
            dashboardContext.fireContextChangeEvent();
        }

        enableQueryButtons();

        return component;
    }

    private void enableQueryButtons() {
        queryToolbarPresenter.setEnabled(!getQueryableComponents().isEmpty());
        queryToolbarPresenter.onSearching(getCombinedSearchState());
    }

    private boolean getCombinedSearchState() {
        final List<Queryable> queryableComponents = getQueryableComponents();
        boolean combinedMode = false;
        for (final Queryable queryable : queryableComponents) {
            if (queryable.getSearchState()) {
                combinedMode = true;
            }
        }
        return combinedMode;
    }

    private List<ErrorMessage> getCombinedErrors() {
        final List<ErrorMessage> errors = new ArrayList<>();
        final List<Queryable> queryableComponents = getQueryableComponents();
        for (final Queryable queryable : queryableComponents) {
            if (queryable.getCurrentErrors() != null) {
                errors.addAll(queryable.getCurrentErrors());
            }
        }
        return errors;
    }

    @Override
    protected DashboardDoc onWrite(final DashboardDoc dashboard) {
        final List<ComponentConfig> componentDataList = new ArrayList<>(components.size());
        for (final Component component : components) {
            final ComponentConfig componentConfig = component.write();
            componentDataList.add(componentConfig);
        }

        final DashboardConfig dashboardConfig = DashboardConfig
                .builder()
                .timeRange(queryToolbarPresenter.getTimeRange())
                .components(componentDataList)
                .layout(MutableConfigUtil.toLayoutConfig(layoutPresenter.getLayoutConfig()))
                .layoutConstraints(layoutConstraints)
                .preferredSize(MutableConfigUtil.toSize(preferredSize))
                // Persist the mode the user is working in, so saving in design mode is remembered.
                .designMode(designMode)
                .modelVersion(VERSION_7_2_0)
                .build();
        return dashboard.copy().dashboardConfig(dashboardConfig).build();
    }

    @Override
    public void onClose() {
        // Remove all components.
        components.onClose();
        super.onClose();
    }

    @Override
    public String getType() {
        return DashboardDoc.TYPE;
    }

    public void duplicateTabTo(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig) {
        final DocSelectionPopup chooser = dashboardSelection.get();
        chooser.setCaption("Choose Dashboard");
        chooser.setIncludedTypes(DashboardDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.EDIT);

        chooser.show(dashDocRef -> {
            if (dashDocRef != null) {
                OpenDocumentEvent.builder(this, dashDocRef)
                        .forceOpen(true)
                        .callbackOnOpen(presenter -> {
                            if (presenter instanceof final DashboardSuperPresenter dashboardSuperPresenter) {
                                dashboardSuperPresenter.getDashboardPresenter()
                                        .duplicateTab(tabLayoutConfig, tabConfig, components);
                            }
                        }).fire();
            }
        });
    }

    public void duplicateTab(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tab) {
        duplicateTabs(tabLayoutConfig, Collections.singletonList(tab), components);
    }

    public void duplicateTab(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig,
                             final Components components) {
        duplicateTabs(tabLayoutConfig, Collections.singletonList(tabConfig), components);
    }

    public void duplicateTabPanel(final MutableTabLayoutConfig tabLayoutConfig) {
        duplicateTabs(tabLayoutConfig, new ArrayList<>(tabLayoutConfig.getTabs()), components);
    }

    public void duplicateTabs(final MutableTabLayoutConfig tabLayoutConfig, final List<MutableTabConfig> tabs,
                              final Components orginalComponents) {
        // Get sets of unique component ids and names.
        final ComponentNameSet componentNameSet = new ComponentNameSet(this.components);
        final Map<String, String> idMapping = new HashMap<>();
        final List<ComponentConfig> newComponents = new ArrayList<>();
        final Map<String, MutableTabConfig> newTabConfigMap = new HashMap<>();
        if (tabs != null) {
            for (final MutableTabConfig tabConfig : tabs) {
                // Duplicate the referenced component.
                final Component originalComponent = orginalComponents.get(tabConfig.getId());
                originalComponent.write();
                final ComponentType type = originalComponent.getComponentType();

                final ComponentId componentId = componentNameSet.createUnique(type, originalComponent.getLabel());
                final ComponentConfig componentConfig = originalComponent.getComponentConfig()
                        .copy()
                        .id(componentId.id)
                        .name(componentId.name)
                        .build();

                idMapping.put(tabConfig.getId(), componentId.id);
                newComponents.add(componentConfig);

                final MutableTabConfig newTabConfig = new MutableTabConfig(componentId.id, tabConfig.isVisible());
                newTabConfigMap.put(componentId.id, newTabConfig);
            }
        }

        // Now try and repoint the id references so that all new copied items reference each other rather than their
        // originals.
        final List<ComponentConfig> modifiedComponents = new ArrayList<>();
        for (final ComponentConfig componentConfig : newComponents) {
            ComponentSettings settings = componentConfig.getSettings();
            if (settings instanceof final TableComponentSettings tableComponentSettings) {
                if (tableComponentSettings.getQueryId() != null
                    && idMapping.containsKey(tableComponentSettings.getQueryId())) {
                    settings = tableComponentSettings.copy()
                            .queryId(idMapping.get(tableComponentSettings.getQueryId()))
                            .build();
                }
            } else if (settings instanceof final VisComponentSettings visComponentSettings) {
                if (visComponentSettings.getTableId() != null
                    && idMapping.containsKey(visComponentSettings.getTableId())) {
                    settings = visComponentSettings.copy()
                            .tableId(idMapping.get(visComponentSettings.getTableId()))
                            .build();
                }
            } else if (settings instanceof final TextComponentSettings textComponentSettings) {
                if (textComponentSettings.getTableId() != null
                    && idMapping.containsKey(textComponentSettings.getTableId())) {
                    settings = textComponentSettings.copy()
                            .tableId(idMapping.get(textComponentSettings.getTableId()))
                            .build();
                }
            }
            modifiedComponents.add(componentConfig.copy()
                    .settings(settings)
                    .build());
        }

        // Now try and add all the duplicated components.
        final List<Component> duplicatedComponents = new ArrayList<>();
        for (final ComponentConfig componentConfig : modifiedComponents) {
            final Component component = addComponent(componentConfig.getType(), componentConfig);
            if (component != null) {
                final MutableTabConfig newTabConfig = newTabConfigMap.get(component.getId());
                component.setTabConfig(newTabConfig);
                duplicatedComponents.add(component);
            }
        }

        // Now link all the components.
        for (final Component component : duplicatedComponents) {
            component.link();
        }

        if (!duplicatedComponents.isEmpty()) {
            final MutableTabConfig firstTabConfig = getFirstTabConfig(tabLayoutConfig);
            final Element selectedComponent = getFirstComponentElement(firstTabConfig);
            final Rect rect = ElementUtil.getClientRect(selectedComponent);
            layoutPresenter.enterNewComponentDestinationMode(
                    null,
                    duplicatedComponents,
                    rect.getLeft() + (rect.getWidth() / 2),
                    rect.getTop() + (rect.getHeight() / 2));
        }
    }

    @Override
    public void removeTab(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tab) {
        removeTabs(tabLayoutConfig, Collections.singletonList(tab));
    }

    @Override
    public void removeTabPanel(final MutableTabLayoutConfig tabLayoutConfig) {
        removeTabs(tabLayoutConfig, new ArrayList<>(tabLayoutConfig.getTabs()));
    }

    private void removeTabs(final MutableTabLayoutConfig tabLayoutConfig, final List<MutableTabConfig> tabs) {
        // Figure out what tabs would remain after removal.
        int hiddenCount = 0;
        int totalCount = 0;
        for (final MutableTabConfig tab : tabLayoutConfig.getTabs()) {
            if (!tabs.contains(tab)) {
                if (!tab.isVisible()) {
                    hiddenCount++;
                }
                totalCount++;
            }
        }

        // If all remaining tabs are hidden then we can't allow removal.
        if (totalCount > 0 && totalCount == hiddenCount) {
            AlertEvent.fireError(this, "You cannot remove or hide all tabs", null);
        } else {
            String message = "Are you sure you want to remove this tab?";
            if (tabs.size() > 1) {
                message = "Are you sure you want to remove these tabs?";
            }

            ConfirmEvent.fire(this, message, ok -> {
                if (ok) {
                    for (final MutableTabConfig tab : tabs) {
                        layoutPresenter.closeTab(tab);
                        final Component component = components.get(tab.getId());
                        if (component != null) {
                            if (component instanceof final Queryable queryable) {
                                queryable.removeSearchStateListener(this);
                                queryable.removeSearchErrorListener(this);
                            }
                            components.remove(tab.getId());
                            dashboardContext.fireContextChangeEvent();
                        }
                    }
                    enableQueryButtons();
                }
            });
        }
    }

    public void maximiseTabs(final MutableTabConfig tabConfig) {
        maximiseTabsButton.setVisible(false);
        restoreTabsButton.setVisible(true);
        layoutPresenter.maximiseTabs(tabConfig);
    }

    public void restoreTabs() {
        maximiseTabsButton.setVisible(true);
        restoreTabsButton.setVisible(false);
        layoutPresenter.restoreTabs();
    }

    public boolean isMaximised() {
        return layoutPresenter.isMaximised();
    }

    void toggleStart() {
        final boolean searching = getCombinedSearchState();
        if (searching) {
            stop();
        } else {
            start();
        }
    }

    void start() {
        // If we have some queryable components then make sure we get query info for them.
        final List<Queryable> queryableComponents = getQueryableComponents();
        if (!queryableComponents.isEmpty()) {
            queryInfo.prompt(() -> {
                for (final Queryable queryable : queryableComponents) {
                    queryable.setDashboardContext(dashboardContext);
                    queryable.start();
                }
            }, this);
        }
    }

    void stop() {
        for (final Queryable queryable : getQueryableComponents()) {
            queryable.stop();
        }
    }

    private List<Queryable> getQueryableComponents() {
        // Get a sub list of components that can be queried.
        final List<Queryable> queryableComponents = new ArrayList<>();
        for (final Component component : components) {
            if (component instanceof Queryable) {
                queryableComponents.add((Queryable) component);
            }
        }
        return queryableComponents;
    }

    @Override
    public String getLabel() {
        String label = getTitle();
        if (isDirty()) {
            label = "* " + label;
        }
        return label;
    }

    public String getTitle() {
        String title = "";
        if (docRef != null) {
            title = docRef.getName();
        }
        if (NullSafe.isNonEmptyString(customTitle)) {
            title = customTitle.replaceAll("\\$\\{name\\}", title);
        }
        return title;
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DOCUMENT_DASHBOARD;
    }

    @Override
    protected void onDirty() {
        // Only fire tab refresh if the tab has changed.
        if (lastLabel == null || !lastLabel.equals(getLabel())) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
    }

    public void setCustomTitle(final String customTitle) {
        this.customTitle = customTitle;
    }

    private void addNewComponent(final ComponentType type) {
        if (type != null) {

            // Get sets of unique component ids and names.
            // Get sets of unique component ids and names.
            final ComponentId componentId = new ComponentNameSet(components)
                    .createUnique(type, type.getName());
            final ComponentConfig componentConfig = ComponentConfig
                    .builder()
                    .type(type.getId())
                    .id(componentId.id)
                    .name(componentId.name)
                    .build();

            final Component componentPresenter = addComponent(componentConfig.getType(), componentConfig);
            if (componentPresenter != null) {
                componentPresenter.link();
                final MutableTabConfig tabConfig = new MutableTabConfig(componentId.id, true);
                componentPresenter.setTabConfig(tabConfig);

                final MutableTabConfig firstTabConfig = getFirstTabConfig(layoutPresenter.getLayoutConfig());
                if (firstTabConfig == null) {
                    // Add the panel directly.
                    // Note that as we don't have any panels then size it to fit the visible area.
                    final MutableSize visibleSize = layoutPresenter.getVisibleSize();
                    preferredSize = visibleSize.copy();

                    final MutableTabLayoutConfig tabLayoutConfig =
                            new MutableTabLayoutConfig(visibleSize, 0);
                    tabLayoutConfig.add(tabConfig);

                    layoutPresenter.configure(tabLayoutConfig, layoutConstraints, preferredSize);
                    onChange();

                    // Show the component settings.
                    componentPresenter.showSettings();

                } else {
                    final Element element = getFirstComponentElement(firstTabConfig);
                    final Rect rect = ElementUtil.getClientRect(element);
                    layoutPresenter.enterNewComponentDestinationMode(
                            componentPresenter,
                            Collections.singletonList(componentPresenter),
                            rect.getLeft() + (rect.getWidth() / 2),
                            rect.getTop() + (rect.getHeight() / 2));
                }
            }
        }
    }

    private Element getFirstComponentElement(final MutableTabConfig firstTabConfig) {
        if (firstTabConfig != null) {
            final Component component = components.get(firstTabConfig.getId());
            if (component != null) {
                final MyPresenterWidget<?> myPresenterWidget = (MyPresenterWidget<?>) component;
                return myPresenterWidget.getWidget().getElement();
            }
        }
        return getWidget().getElement();
    }

    private MutableTabConfig getFirstTabConfig(final MutableLayoutConfig layoutConfig) {
        if (layoutConfig != null) {
            if (layoutConfig instanceof final MutableSplitLayoutConfig splitLayoutConfig) {
                final List<MutableLayoutConfig> list = splitLayoutConfig.getChildren();
                if (list != null) {
                    for (final MutableLayoutConfig child : list) {
                        final MutableTabConfig tabConfig = getFirstTabConfig(child);
                        if (tabConfig != null) {
                            return tabConfig;
                        }
                    }
                }

            } else if (layoutConfig instanceof final MutableTabLayoutConfig tabLayoutConfig) {
                if (!tabLayoutConfig.getTabs().isEmpty()) {
                    if (tabLayoutConfig.getSelected() >= 0 &&
                        tabLayoutConfig.getSelected() < tabLayoutConfig.getTabs().size()) {
                        return tabLayoutConfig.get(tabLayoutConfig.getSelected());
                    } else {
                        return tabLayoutConfig.get(0);
                    }
                }
            }
        }
        return null;
    }

    /// @return A description of the dashboard as edited, in plain text, for tools and people reading it: each
    /// query's data source and expression, each table's columns, formatting rules and settings, each
    /// Visualisation's document, linked table, settings and latest render report, each other component's
    /// settings, the parameters, the time range, and the components shown.
    public String getDescription() {
        return describe(true);
    }

    /// @return The dashboard's description ([#getDescription()]) without the Visualisations' render reports,
    /// which change as they render, for telling whether the dashboard itself has changed.
    public String getComparableDescription() {
        return describe(false);
    }

    // The dashboard's description, with the Visualisations' render reports or without them.
    private String describe(final boolean withRenderReports) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Time range: ").append(queryToolbarPresenter.getTimeRange()).append('\n');
        sb.append("Parameters: ").append(NullSafe.list(dashboardContext.getParams())).append('\n');
        final List<String> shown = new ArrayList<>();
        addShownComponents(layoutPresenter.getLayoutConfig(), shown);
        sb.append("Shown components: ").append(String.join(", ", shown)).append('\n');
        for (final Component component : components) {
            final String name = NullSafe.get(component.getComponentConfig(), ComponentConfig::getName);
            sb.append("\n## ").append(component.getComponentType().getName()).append(" '").append(name)
                    .append("' (id ").append(component.getId()).append(")\n");
            if (component instanceof final QueryPresenter queryPresenter) {
                final DocRef dataSource = queryPresenter.getDataSource();
                sb.append("Data source: ").append(dataSource == null
                        ? "none"
                        : dataSource.getType() + " '" + dataSource.getName() + "' (uuid " + dataSource.getUuid()
                          + ")").append('\n');
                sb.append("Expression:\n").append(NullSafe.get(queryPresenter.getExpression(),
                        ExpressionOperator::toMultiLineString)).append('\n');
            } else if (component instanceof final TablePresenter tablePresenter) {
                final TableComponentSettings settings = tablePresenter.getTableComponentSettings();
                sb.append("Query: ").append(NullSafe.get(settings, TableComponentSettings::getQueryId))
                        .append('\n');
                sb.append("Columns:\n");
                for (final Column column : NullSafe.list(NullSafe.get(settings,
                        TableComponentSettings::getColumns))) {
                    if (!column.isSpecial()) {
                        sb.append("- ").append(column.getName()).append(": ")
                                .append(NullSafe.string(column.getExpression()))
                                .append(describeColumnState(column))
                                .append('\n');
                    }
                }
                final List<ConditionalFormattingRule> rules = NullSafe.list(NullSafe.get(settings,
                        TableComponentSettings::getConditionalFormattingRules));
                if (!rules.isEmpty()) {
                    sb.append("Conditional formatting (the first rule a row matches applies):\n");
                    for (int i = 0; i < rules.size(); i++) {
                        sb.append(i + 1).append(". ").append(describeRule(rules.get(i))).append('\n');
                    }
                }
            } else if (component instanceof final VisPresenter visPresenter) {
                describeVis(sb, visPresenter, withRenderReports);
            } else if (component instanceof final EmbeddedQueryPresenter embeddedQuery) {
                describeEmbeddedQuery(sb, embeddedQuery);
            }
            describeSelectionFilters(sb, NullSafe.get(component.getComponentConfig(), ComponentConfig::getSettings));
            final String settings = describeSettings(NullSafe.get(component.getComponentConfig(),
                    ComponentConfig::getSettings));
            if (settings != null) {
                sb.append("Settings: ").append(settings).append('\n');
            }
        }
        return sb.toString();
    }

    // The filters a component takes from other components' selections (e.g. a chart's): a query's, applied to its
    // search, and a table's, applied to its rows. A term's value names a selection, e.g.
    // ${component.vis-1.selection.category}.
    private static void describeSelectionFilters(final StringBuilder sb, final ComponentSettings settings) {
        if (settings instanceof final HasSelectionQuery query) {
            describeHandlers(sb, "Query selection filters", query.getSelectionQuery());
        }
        if (settings instanceof final HasSelectionFilter filter) {
            describeHandlers(sb, "Table selection filters", filter.getSelectionFilter());
        }
    }

    private static void describeHandlers(final StringBuilder sb,
                                         final String heading,
                                         final List<ComponentSelectionHandler> handlers) {
        final List<String> described = NullSafe.list(handlers).stream()
                .filter(handler -> handler.getExpression() != null)
                .map(handler -> "[id " + NullSafe.nonBlankStringElse(handler.getId(), "none") + "] "
                                + handler.getExpression().toString() + (handler.isEnabled()
                        ? ""
                        : " (disabled)"))
                .toList();
        if (!described.isEmpty()) {
            sb.append(heading).append(": ").append(String.join("; ", described)).append('\n');
        }
    }

    // A component's main settings (those its Settings dialog has), by short keys, or null if it has none.
    private String describeSettings(final ComponentSettings settings) {
        final List<String> parts = new ArrayList<>();
        if (settings instanceof final TableComponentSettings table) {
            parts.add("pageSize " + NullSafe.getOrElse(table, TableComponentSettings::getPageSize, 100));
            parts.add("maxResults " + NullSafe.list(table.getMaxResults()));
            parts.add("showDetail " + table.showDetail());
            parts.add("extractValues " + table.extractValues());
            parts.add("extractionPipeline " + describeDoc(table.getExtractionPipeline()));
            parts.add("showValueFilters " + table.showValueFilters());
            parts.add("query " + describeComponent(table.getQueryId()));
            if (table.overrideMaxStringFieldLength()) {
                parts.add("maxStringFieldLength " + table.getMaxStringFieldLength());
            }
        } else if (settings instanceof final AbstractQueryComponentSettings query) {
            final Automate automate = query.getAutomate();
            parts.add("queryOnOpen " + (automate != null && automate.isOpen()));
            parts.add("autoRefresh " + (automate != null && automate.isRefresh()));
            parts.add("refreshInterval " + NullSafe.get(automate, Automate::getRefreshInterval));
        } else if (settings instanceof final TextComponentSettings text) {
            parts.add("table " + (text.getTableId() == null
                    ? "none (any)"
                    : describeComponent(text.getTableId())));
            parts.add("pipeline " + describeDoc(text.getPipeline()));
            parts.add("showAsHtml " + text.isShowAsHtml());
            parts.add("showStepping " + text.isShowStepping());
        } else {
            return null;
        }
        return String.join("; ", parts);
    }

    // An embedded query: the Query it shows by reference, if it does, and its StroomQL, between lines of three
    // backticks.
    private static void describeEmbeddedQuery(final StringBuilder sb, final EmbeddedQueryPresenter embeddedQuery) {
        final DocRef referenced = embeddedQuery.getReferencedQuery();
        sb.append("Query: ").append(referenced == null
                ? "embedded in the dashboard"
                : "Query " + describeDoc(referenced) + " by reference").append('\n');
        sb.append("StroomQL:\n```\n").append(NullSafe.string(embeddedQuery.getQueryText()).strip())
                .append("\n```\n");
    }

    // A Visualisation component: the Visualisation it shows, the table it is linked to, its settings as a JSON
    // object of control ids and values, and (if wanted) its latest render report, on one line and cut short, as the
    // visualisation's script wrote it.
    private void describeVis(final StringBuilder sb, final VisPresenter vis, final boolean withRenderReport) {
        final VisComponentSettings settings = vis.getSettings() instanceof final VisComponentSettings visSettings
                ? visSettings
                : null;
        final DocRef visualisation = NullSafe.get(settings, VisComponentSettings::getVisualisation);
        sb.append("Visualisation: ").append(visualisation == null
                ? "none"
                : "'" + NullSafe.string(visualisation.getName()) + "' (uuid " + visualisation.getUuid() + ")")
                .append('\n');
        sb.append("Linked table: ").append(describeComponent(NullSafe.get(settings, VisComponentSettings::getTableId)))
                .append('\n');
        final String json = NullSafe.get(settings, VisComponentSettings::getJson);
        final JSONObject object = parseObject(json);
        sb.append("Vis settings: ").append(object != null
                ? object.toString()
                : NullSafe.string(json).replaceAll("\\s+", " ")).append('\n');
        final String report = vis.getLastRenderReport();
        if (withRenderReport && NullSafe.isNonBlankString(report)) {
            final String line = report.replaceAll("[\\r\\n]+", " ").trim();
            sb.append(RENDER_REPORT).append(line.length() > MAX_RENDER_REPORT_CHARS
                    ? line.substring(0, MAX_RENDER_REPORT_CHARS) + "..."
                    : line).append('\n');
        }
    }

    // A JSON object, or null if the text is empty or isn't one.
    private static JSONObject parseObject(final String json) {
        if (NullSafe.isBlankString(json)) {
            return null;
        }
        try {
            return JSONUtil.getObject(JSONUtil.parse(json));
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static String describeDoc(final DocRef doc) {
        return doc == null
                ? "none"
                : "'" + doc.getName() + "' (uuid " + doc.getUuid() + ")";
    }

    private String describeComponent(final String componentId) {
        final Component component = componentId == null
                ? null
                : components.get(componentId);
        return component == null
                ? "none"
                : "'" + NullSafe.get(component.getComponentConfig(), ComponentConfig::getName) + "'";
    }

    // A conditional formatting rule: which rows it matches, and what it does to them.
    private static String describeRule(final ConditionalFormattingRule rule) {
        final List<String> effects = new ArrayList<>();
        if (rule.isHide()) {
            effects.add("hidden");
        }
        if (rule.getFormattingStyle() != null && rule.getFormattingStyle() != ConditionalFormattingStyle.NONE) {
            effects.add(rule.getFormattingStyle().getDisplayValue() + " "
                        + NullSafe.get(rule.getFormattingType(), ConditionalFormattingType::getDisplayValue));
        } else if (rule.getCustomStyle() != null) {
            effects.add("custom colours");
        }
        if (rule.getTextAttributes() != null && rule.getTextAttributes().isBold()) {
            effects.add("bold");
        }
        if (rule.getTextAttributes() != null && rule.getTextAttributes().isItalic()) {
            effects.add("italic");
        }
        return "rows where " + rule.getExpression() + ": " + (effects.isEmpty()
                ? "no effect"
                : String.join(", ", effects)) + (rule.isEnabled()
                ? ""
                : " (disabled)");
    }

    // How a column is shown: hidden, grouped, sorted, formatted or filtered.
    private static String describeColumnState(final Column column) {
        final List<String> state = new ArrayList<>();
        if (!column.isVisible()) {
            state.add("hidden");
        }
        if (column.getGroup() != null) {
            state.add("grouped, level " + (column.getGroup() + 1));
        }
        if (column.getSort() != null) {
            state.add("sorted " + NullSafe.get(column.getSort().getDirection(), SortDirection::getDisplayValue)
                      + ", order " + (column.getSort().getOrder() + 1));
        }
        final Format.Type formatType = NullSafe.get(column.getFormat(), Format::getType);
        if (formatType != null && formatType != Format.Type.GENERAL) {
            state.add("format " + formatType.getDisplayValue());
        }
        final String filter = NullSafe.get(column.getColumnFilter(), ColumnFilter::getFilter);
        if (NullSafe.isNonBlankString(filter)) {
            state.add("filter '" + filter + "'");
        }
        return state.isEmpty()
                ? ""
                : " (" + String.join("; ", state) + ")";
    }

    /// Applies changes made programmatically to one of the dashboard's tables, as its column menus would, unsaved:
    /// changes to the table, to the expression of the classic query that feeds it, and tables added beside it as
    /// tabs (fed by the same query), which later changes name as their table. They are applied all or none: if one
    /// can't be, the dashboard is put back as it was. They are applied on top of whatever else has changed since
    /// they were proposed: each finds what it changes again, and a query expression they replace must be as it was
    /// (see [DashboardDescriptions#sameExpression(String, ExpressionOperator)]). Once applied, they can be undone
    /// with [#undoTableChanges(String)] while the components they changed or added are as they left them.
    ///
    /// @param componentId The table's component id.
    /// @param name        The table's name, used if no component has the id.
    /// @param changes     The changes, in order.
    /// @param undoKey     What the changes are known by, to undo them, or null if they can't be undone.
    /// @return Why they couldn't all be applied, or null if they were.
    public String applyTableChanges(final String componentId,
                                      final String name,
                                      final List<TableChange> changes,
                                      final String undoKey) {
        Component component = componentId == null
                ? null
                : components.get(componentId);
        if (component == null) {
            for (final Component each : components) {
                if (Objects.equals(NullSafe.get(each.getComponentConfig(), ComponentConfig::getName), name)) {
                    component = each;
                }
            }
        }
        if (component == null) {
            return "The dashboard has no table '" + name + "'.";
        }
        ExpressionOperator expression = null;
        // The hash of the expression it replaces, as it was when proposed.
        String replacedExpression = null;
        for (final TableChange change : changes) {
            if (TableChange.SET_EXPRESSION.equals(change.getOp())) {
                expression = change.getQuery();
                replacedExpression = change.getRef();
                if (expression == null) {
                    return "The query's new expression can't be read.";
                }
            }
        }
        final QueryPresenter query;
        if (expression != null) {
            final String queryId = TableChange.queryId(component);
            query = queryId != null && components.get(queryId) instanceof final QueryPresenter queryPresenter
                    ? queryPresenter
                    : null;
            if (query == null) {
                return "Table '" + name + "' isn't fed by a classic query on this dashboard, so its expression "
                       + "can't be set.";
            }
            // Everything else changed since it was proposed is kept, but not an expression changed since: the
            // new one would replace it.
            if (replacedExpression != null && !DashboardDescriptions.sameExpression(replacedExpression,
                    query.getExpression())) {
                return "The query's expression has changed since this was proposed, so it isn't replaced. Ask "
                       + "again for an up-to-date change.";
            }
        } else {
            query = null;
        }

        final TableComponentSettings before = TableChange.snapshot(component);
        final ExpressionOperator expressionBefore = query != null
                ? query.getExpression()
                : null;
        // The tables added, by the name the changes give them (lower case), and the name and settings of each other
        // component renamed or changed, as they were, by id.
        final Map<String, Component> added = new LinkedHashMap<>();
        // Of several charts added together, the last added and the first of the row it is in, which later ones are
        // placed beside, under or with (see TableChange.RIGHT, UNDER and WITH).
        Component lastVis = null;
        Component rowStart = null;
        final Map<String, ComponentConfig> configsBefore = new LinkedHashMap<>();
        for (int i = 0; i < changes.size(); i++) {
            final TableChange change = changes.get(i);
            final String prefix = "Change " + (i + 1) + " (" + change.getOp() + "): ";
            String failed = null;
            if (TableChange.ADD_TABLE.equals(change.getOp())) {
                final Component table = addTableBeside(component, change.getName(), change.isCopy());
                if (table == null) {
                    failed = prefix + "the table couldn't be added.";
                } else {
                    added.put(NullSafe.string(change.getName()).toLowerCase(), table);
                }
            } else if (TableChange.ADD_TEXT.equals(change.getOp())) {
                final Component followed = change.getTable() == null || change.getTable().equalsIgnoreCase(name)
                        ? component
                        : added.get(change.getTable().toLowerCase());
                final Component text = followed == null
                        ? null
                        : addTextBelow(followed, change.getName());
                if (text == null) {
                    failed = prefix + "the Text widget couldn't be added.";
                } else {
                    added.put(NullSafe.string(change.getName()).toLowerCase(), text);
                }
            } else if (TableChange.ADD_VIS.equals(change.getOp())) {
                final Component linked = change.getTable() == null || change.getTable().equalsIgnoreCase(name)
                        ? component
                        : added.containsKey(change.getTable().toLowerCase())
                                ? added.get(change.getTable().toLowerCase())
                                : tableNamed(change.getTable());
                final Component vis = linked == null
                        ? null
                        : addVis(linked, change, lastVis, rowStart);
                if (vis != null) {
                    if (!TableChange.RIGHT.equals(change.getPlacement())
                        && !TableChange.WITH.equals(change.getPlacement())) {
                        rowStart = vis;
                    }
                    lastVis = vis;
                }
                if (vis == null) {
                    failed = prefix + "the visualisation couldn't be added.";
                } else {
                    added.put(NullSafe.string(change.getName()).toLowerCase(), vis);
                }
            } else if (TableChange.REMOVE_SELECTION_FILTER.equals(change.getOp())) {
                final Component target = change.getTargetId() == null
                        ? null
                        : components.get(change.getTargetId());
                if (target == null) {
                    failed = prefix + "there is no component '" + NullSafe.string(change.getTarget()) + "'.";
                } else {
                    if (!added.containsValue(target) && !configsBefore.containsKey(target.getId())) {
                        configsBefore.put(target.getId(), ComponentSettingChanges.snapshot(target));
                    }
                    final String problem = ComponentSettingChanges.removeSelectionFilter(target, change.getFilter());
                    failed = problem == null
                            ? null
                            : prefix + problem;
                }
            } else if (TableChange.ADD_SELECTION_FILTER.equals(change.getOp())) {
                final Component target = change.getTargetId() == null
                        ? null
                        : components.get(change.getTargetId());
                if (target == null) {
                    failed = prefix + "there is no component '" + NullSafe.string(change.getTarget()) + "'.";
                } else {
                    if (!added.containsValue(target) && !configsBefore.containsKey(target.getId())) {
                        configsBefore.put(target.getId(), ComponentSettingChanges.snapshot(target));
                    }
                    final String problem = ComponentSettingChanges.addSelectionFilter(target, change.getValue(),
                            change.getQuery());
                    failed = problem == null
                            ? null
                            : prefix + problem;
                }
            } else if (TableChange.SET_QUERY.equals(change.getOp())) {
                final Component target = change.getTargetId() == null
                        ? null
                        : components.get(change.getTargetId());
                if (!(target instanceof final EmbeddedQueryPresenter embeddedQuery)) {
                    failed = prefix + "there is no embedded query '" + NullSafe.string(change.getTargetId()) + "'.";
                } else if (!Objects.equals(NullSafe.string(embeddedQuery.getQueryText()).strip(),
                        NullSafe.string(change.getRef()).strip())) {
                    failed = prefix + "the embedded query's StroomQL has changed since this was proposed.";
                } else {
                    if (!configsBefore.containsKey(target.getId())) {
                        configsBefore.put(target.getId(), ComponentSettingChanges.snapshot(target));
                    }
                    final String problem = embeddedQuery.setQueryText(change.getValue());
                    failed = problem == null
                            ? null
                            : prefix + problem;
                }
            } else if (TableChange.RENAME.equals(change.getOp()) || TableChange.SET_SETTING.equals(change.getOp())
                       || TableChange.SET_VIS_SETTINGS.equals(change.getOp())) {
                final Component target = change.getTargetId() != null
                        ? components.get(change.getTargetId())
                        : change.getTarget() == null
                                ? component
                                : added.get(change.getTarget().toLowerCase());
                if (target == null) {
                    failed = prefix + "there is no component '" + NullSafe.nonBlankStringElse(change.getTarget(), name)
                             + "'.";
                } else {
                    if (!added.containsValue(target) && !configsBefore.containsKey(target.getId())) {
                        configsBefore.put(target.getId(), ComponentSettingChanges.snapshot(target));
                    }
                    if (TableChange.RENAME.equals(change.getOp())) {
                        target.setComponentName(change.getName());
                    } else if (TableChange.SET_VIS_SETTINGS.equals(change.getOp())) {
                        failed = applyVisSettings(target, change);
                        failed = failed == null
                                ? null
                                : prefix + failed;
                    } else {
                        failed = ComponentSettingChanges.apply(target, change.getSetting(), change.getValue(),
                                change.getRef(), change.getDoc());
                        failed = failed == null
                                ? null
                                : prefix + failed;
                    }
                }
            } else if (!TableChange.SET_EXPRESSION.equals(change.getOp())) {
                final Component target = change.getTable() == null || change.getTable().equalsIgnoreCase(name)
                        ? component
                        : added.get(change.getTable().toLowerCase());
                failed = target == null
                        ? prefix + "there is no table '" + change.getTable() + "'."
                        : TableChange.apply(target, List.of(change));
            }
            if (failed != null) {
                added.values().forEach(this::removeAddedComponent);
                configsBefore.forEach((id, config) -> ComponentSettingChanges.restore(components.get(id), config));
                refreshVisData(configsBefore.keySet());
                TableChange.restore(component, before);
                syncSettingsDialogs(component, configsBefore.keySet());
                return failed;
            }
        }
        if (query != null) {
            query.setExpression(expression);
            query.onChange();
        }
        syncSettingsDialogs(component, configsBefore.keySet());
        // A change to an embedded query, rather than a table, is undone by putting its settings back.
        if (undoKey != null && (before != null || !configsBefore.isEmpty())) {
            appliedChanges.put(undoKey, new AppliedChanges(component.getComponentConfig().getId(), before,
                    NullSafe.get(query, Component::getId), expressionBefore,
                    added.values().stream().map(Component::getId).toList(), configsBefore,
                    getComparableDescription()));
        }
        return null;
    }

    // Adds a table beside another as a tab, fed by the same query, with its columns or none, with the name asked for
    // (made unique if the dashboard has the name already).
    private Component addTableBeside(final Component source, final String name, final boolean copyColumns) {
        final TableComponentSettings settings = TableChange.snapshot(source);
        final MutableTabConfig sourceTab = source.getTabConfig();
        if (settings == null || sourceTab == null || sourceTab.getParent() == null) {
            return null;
        }
        final ComponentType type = source.getComponentType();
        final ComponentId componentId = new ComponentNameSet(components).createUnique(type, name);
        final ComponentConfig componentConfig = ComponentConfig.builder()
                .type(type.getId())
                .id(componentId.id)
                .name(componentId.name)
                .settings(settings.copy()
                        .columns(copyColumns
                                ? new ArrayList<>(NullSafe.list(settings.getColumns()))
                                : new ArrayList<>())
                        .build())
                .build();
        final Component table = addComponent(componentConfig.getType(), componentConfig);
        if (table == null) {
            return null;
        }
        table.link();
        final MutableTabConfig tabConfig = new MutableTabConfig(componentId.id, true);
        table.setTabConfig(tabConfig);
        sourceTab.getParent().add(tabConfig);
        layoutPresenter.clear();
        layoutPresenter.refresh();
        onChange();
        return table;
    }

    // Adds a Text widget in a new pane below a table, following it, with the name asked for (made unique if the
    // dashboard has the name already).
    private Component addTextBelow(final Component table, final String name) {
        final MutableTabConfig tableTab = table.getTabConfig();
        final ComponentType type = components.getComponentTypes().stream()
                .filter(componentType -> TextPresenter.TYPE.getId().equals(componentType.getId()))
                .findFirst()
                .orElse(null);
        if (type == null || tableTab == null || tableTab.getParent() == null) {
            return null;
        }
        final ComponentId componentId = new ComponentNameSet(components).createUnique(type, name);
        final ComponentConfig componentConfig = ComponentConfig.builder()
                .type(type.getId())
                .id(componentId.id)
                .name(componentId.name)
                .settings(TextComponentSettings.builder().tableId(table.getId()).build())
                .build();
        final Component text = addComponent(componentConfig.getType(), componentConfig);
        if (text == null) {
            return null;
        }
        text.link();
        final MutableTabConfig tabConfig = new MutableTabConfig(componentId.id, true);
        text.setTabConfig(tabConfig);
        if (!layoutPresenter.addTabBelow(tableTab.getParent(), tabConfig)) {
            // Beside the table, if it can't go below it.
            tableTab.getParent().add(tabConfig);
            layoutPresenter.clear();
            layoutPresenter.refresh();
        }
        onChange();
        return text;
    }

    // Adds a Visualisation component linked to a table, showing the Visualisation the change gives with its settings,
    // in a new pane below the table or as a tab beside it, with the name asked for (made unique if the dashboard has
    // the name already).
    // A table on the dashboard, by its name (ignoring case), e.g. for one of several charts added together that is
    // linked to another table than the change's; or null.
    private Component tableNamed(final String name) {
        for (final Component component : components) {
            if (component instanceof TablePresenter
                && name.equalsIgnoreCase(NullSafe.get(component.getComponentConfig(), ComponentConfig::getName))) {
                return component;
            }
        }
        return null;
    }

    private Component addVis(final Component table,
                             final TableChange change,
                             final Component lastVis,
                             final Component rowStart) {
        final MutableTabConfig tableTab = table.getTabConfig();
        final ComponentType type = components.getComponentTypes().stream()
                .filter(componentType -> VisPresenter.TYPE.getId().equals(componentType.getId()))
                .findFirst()
                .orElse(null);
        final String json = mergeVisSettings(null, change.getValue());
        if (type == null || tableTab == null || tableTab.getParent() == null || change.getDoc() == null
            || json == null) {
            return null;
        }
        final ComponentId componentId = new ComponentNameSet(components).createUnique(type, change.getName());
        final ComponentConfig componentConfig = ComponentConfig.builder()
                .type(type.getId())
                .id(componentId.id)
                .name(componentId.name)
                .settings(VisComponentSettings.builder()
                        .tableId(table.getId())
                        .visualisation(change.getDoc())
                        .json(json)
                        .build())
                .build();
        final Component vis = addComponent(componentConfig.getType(), componentConfig);
        if (vis == null) {
            return null;
        }
        // Linking it to the table fetches its data, if the table's query has run.
        vis.link();
        final MutableTabConfig tabConfig = new MutableTabConfig(componentId.id, true);
        vis.setTabConfig(tabConfig);
        // Of several charts added together: beside, under or with one added before it.
        final MutableTabConfig lastTab = NullSafe.get(lastVis, Component::getTabConfig);
        final MutableTabConfig rowStartTab = NullSafe.get(rowStart, Component::getTabConfig);
        if (TableChange.WITH.equals(change.getPlacement()) && lastTab != null && lastTab.getParent() != null) {
            lastTab.getParent().add(tabConfig);
            layoutPresenter.clear();
            layoutPresenter.refresh();
            onChange();
            return vis;
        }
        if (TableChange.RIGHT.equals(change.getPlacement()) && lastTab != null
            && layoutPresenter.addTabRight(lastTab.getParent(), tabConfig)) {
            onChange();
            return vis;
        }
        if (TableChange.UNDER.equals(change.getPlacement()) && rowStartTab != null
            && layoutPresenter.addTabBelow(rowStartTab.getParent(), tabConfig)) {
            onChange();
            return vis;
        }
        if (TableChange.TAB.equals(change.getPlacement())
            || !layoutPresenter.addTabBelow(tableTab.getParent(), tabConfig)) {
            // Beside the table: asked for, or if it can't go below it.
            tableTab.getParent().add(tabConfig);
            layoutPresenter.clear();
            layoutPresenter.refresh();
        }
        onChange();
        return vis;
    }

    // Changes a Visualisation component's settings: merges the change's settings into them, switches it to the
    // change's Visualisation if it gives one, and fetches its data again, as its data depends on them.
    private String applyVisSettings(final Component target, final TableChange change) {
        if (!(target instanceof final VisPresenter vis)
            || !(vis.getSettings() instanceof final VisComponentSettings current)) {
            return "the component isn't a visualisation.";
        }
        final String json = mergeVisSettings(current.getJson(), change.getValue());
        if (json == null) {
            return "the visualisation's settings can't be read.";
        }
        final VisComponentSettings.Builder builder = current.copy().json(json);
        if (change.getDoc() != null) {
            builder.visualisation(change.getDoc());
        }
        vis.applySettings(builder.build());
        refreshVisData(vis);
        return null;
    }

    // A Visualisation's settings (a JSON object of control ids and values) with changes merged in: each key the
    // changes give set to their value, or removed if their value is empty, so the control's default applies.
    // Null if either can't be read.
    private static String mergeVisSettings(final String currentJson, final String changesJson) {
        final JSONObject current = NullSafe.isBlankString(currentJson)
                ? new JSONObject()
                : parseObject(currentJson);
        final JSONObject changes = NullSafe.isBlankString(changesJson)
                ? new JSONObject()
                : parseObject(changesJson);
        if (current == null || changes == null) {
            return null;
        }
        final JSONObject merged = new JSONObject();
        for (final String key : current.keySet()) {
            if (!changes.containsKey(key)) {
                merged.put(key, current.get(key));
            }
        }
        for (final String key : changes.keySet()) {
            final JSONValue value = changes.get(key);
            final String text = value == null || value.isNull() != null
                    ? ""
                    : value.isString() != null
                            ? value.isString().stringValue()
                            : value.toString();
            if (!text.isEmpty()) {
                merged.put(key, new JSONString(text));
            }
        }
        return merged.toString();
    }

    // Fetches the data of each Visualisation component among those named again, e.g. after its settings change.
    private void refreshVisData(final Collection<String> componentIds) {
        for (final String id : componentIds) {
            if (components.get(id) instanceof final VisPresenter vis) {
                refreshVisData(vis);
            }
        }
    }

    // Fetches a Visualisation's data again, with its settings as they are now, from the query that feeds the table
    // it is linked to, if that query has run.
    private void refreshVisData(final VisPresenter vis) {
        final String tableId = vis.getSettings() instanceof final VisComponentSettings settings
                ? settings.getTableId()
                : null;
        final String queryId = tableId == null
                ? null
                : TableChange.queryId(components.get(tableId));
        if (queryId != null && components.get(queryId) instanceof final QueryPresenter query
            && query.getSearchModel() != null) {
            query.getSearchModel().refresh(vis.getId(), result -> {
                if (result != null) {
                    vis.setData(result);
                }
            });
        }
    }

    // Removes a component added by applied changes, with its tab.
    private void removeAddedComponent(final Component component) {
        if (component.getTabConfig() != null && component.getTabConfig().getParent() != null) {
            layoutPresenter.closeTab(component.getTabConfig());
        }
        if (component instanceof final Queryable queryable) {
            queryable.removeSearchStateListener(this);
            queryable.removeSearchErrorListener(this);
        }
        components.remove(component.getId());
        dashboardContext.fireContextChangeEvent();
        enableQueryButtons();
    }

    /// @param undoKey What applied changes are known by.
    /// @return True if they can be undone: they were applied here, and the components they changed or added are as
    /// they left them (others may have changed since, as undoing doesn't touch them).
    public boolean canUndoTableChanges(final String undoKey) {
        final AppliedChanges undo = undoKey == null
                ? null
                : appliedChanges.get(undoKey);
        return undo != null
               && components.get(undo.componentId) != null
               && DashboardDescriptions.sameComponents(undo.descriptionAfter, getComparableDescription(),
                undo.touchedIds());
    }

    /// Undoes applied changes, putting the table back as it was before them, as an unsaved edit, if the
    /// components they changed or added are as they left them.
    ///
    /// @param undoKey What the changes are known by.
    /// @return True if they were undone.
    public boolean undoTableChanges(final String undoKey) {
        if (!canUndoTableChanges(undoKey)) {
            return false;
        }
        final AppliedChanges undo = appliedChanges.remove(undoKey);
        for (final String addedId : undo.addedIds) {
            final Component addedComponent = components.get(addedId);
            if (addedComponent != null) {
                removeAddedComponent(addedComponent);
            }
        }
        undo.configsBefore.forEach((id, config) -> ComponentSettingChanges.restore(components.get(id), config));
        refreshVisData(undo.configsBefore.keySet());
        if (undo.queryId != null && components.get(undo.queryId) instanceof final QueryPresenter query) {
            query.setExpression(undo.expressionBefore);
            query.onChange();
        }
        final boolean restored = undo.before == null
                                 || TableChange.restore(components.get(undo.componentId), undo.before);
        syncSettingsDialogs(components.get(undo.componentId), undo.configsBefore.keySet());
        return restored;
    }

    // Brings the Settings dialogs of the table and the other components a change touched up to date with their
    // settings, so saving doesn't write what the dialogs last showed back over the change.
    private void syncSettingsDialogs(final Component table, final Collection<String> componentIds) {
        final List<Component> touched = new ArrayList<>();
        if (table != null) {
            touched.add(table);
        }
        for (final String id : componentIds) {
            NullSafe.consume(components.get(id), touched::add);
        }
        ComponentSettingChanges.syncSettingsDialogs(touched);
    }

    /// @return The dashboard's components, e.g. to find its tables and the queries that feed them.
    public Components getComponents() {
        return components;
    }

    // Adds the names of the components shown: the selected tab of each tab layout.
    private void addShownComponents(final MutableLayoutConfig layoutConfig, final List<String> shown) {
        if (layoutConfig instanceof final MutableSplitLayoutConfig splitLayoutConfig) {
            for (final MutableLayoutConfig child : NullSafe.list(splitLayoutConfig.getChildren())) {
                addShownComponents(child, shown);
            }
        } else if (layoutConfig instanceof final MutableTabLayoutConfig tabLayoutConfig
                   && !tabLayoutConfig.getTabs().isEmpty()) {
            final int selected = tabLayoutConfig.getSelected() >= 0
                                 && tabLayoutConfig.getSelected() < tabLayoutConfig.getTabs().size()
                    ? tabLayoutConfig.getSelected()
                    : 0;
            final Component component = components.get(tabLayoutConfig.get(selected).getId());
            if (component != null) {
                shown.add(NullSafe.get(component.getComponentConfig(), ComponentConfig::getName));
            }
        }
    }

    @Override
    public void onSearching(final boolean searching) {
        queryToolbarPresenter.onSearching(getCombinedSearchState());
    }

    @Override
    public void onError(final List<ErrorMessage> errors) {
        queryToolbarPresenter.onError(getCombinedErrors());
    }

    @Override
    public DocRef getDocRef() {
        return docRef;
    }

    public void onContentTabVisible(final boolean visible) {
        components.getComponents().stream()
                .filter(component -> component instanceof Refreshable)
                .map(component -> (Refreshable) component)
                .forEach(refreshable -> {
                    refreshable.setAllowRefresh(visible);
                    if (visible && refreshable.isRefreshScheduled()) {
                        refreshable.cancelRefresh();
                        if (!refreshable.isSearching()) {
                            refreshable.run(false, false);
                        }
                    }
                });

        components.getComponents().forEach(component -> component.onContentTabVisible(visible));
    }


    // --------------------------------------------------------------------------------


    private static class ComponentNameSet {

        private final Set<String> currentIdSet = new HashSet<>();
        private final Set<String> currentNameSet = new HashSet<>();

        public ComponentNameSet(final Components components) {
            for (final Component component : components.getComponents()) {
                currentIdSet.add(component.getId());
                currentNameSet.add(component.getLabel());
            }
        }

        public ComponentId createUnique(final ComponentType type, final String currentName) {
            final String id = UniqueUtil.createUniqueComponentId(type, currentIdSet);
            final String name = UniqueUtil.makeUniqueName(currentName, currentNameSet);
            currentIdSet.add(id);
            currentNameSet.add(name);
            return new ComponentId(id, name);
        }
    }


    // --------------------------------------------------------------------------------


    // Changes that have been applied: the table they changed and its settings before, the query whose expression
    // they set (if they did) and that expression before, the components they added, the name and settings of each
    // other component they renamed or changed, and the dashboard's description just after, whose parts for those
    // components it must still have for them to be undone.
    private static class AppliedChanges {

        private final String componentId;
        private final TableComponentSettings before;
        private final String queryId;
        private final ExpressionOperator expressionBefore;
        private final List<String> addedIds;
        private final Map<String, ComponentConfig> configsBefore;
        private final String descriptionAfter;

        private AppliedChanges(final String componentId,
                       final TableComponentSettings before,
                       final String queryId,
                       final ExpressionOperator expressionBefore,
                       final List<String> addedIds,
                       final Map<String, ComponentConfig> configsBefore,
                       final String descriptionAfter) {
            this.componentId = componentId;
            this.before = before;
            this.queryId = queryId;
            this.expressionBefore = expressionBefore;
            this.addedIds = addedIds;
            this.configsBefore = configsBefore;
            this.descriptionAfter = descriptionAfter;
        }

        // The ids of the components the changes changed or added.
        private Set<String> touchedIds() {
            final Set<String> ids = new HashSet<>();
            ids.add(componentId);
            NullSafe.consume(queryId, ids::add);
            ids.addAll(addedIds);
            ids.addAll(configsBefore.keySet());
            return ids;
        }
    }


    // --------------------------------------------------------------------------------


    private static class ComponentId {

        private final String id;
        private final String name;

        private ComponentId(final String id, final String name) {
            this.id = id;
            this.name = name;
        }
    }


    // --------------------------------------------------------------------------------


    public interface DashboardView extends View {

        void setContent(Widget view);

        void setEmbedded(boolean embedded);

        void setDesignMode(boolean designMode);
    }
}

