/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.dashboard.client.flexlayout.FlexLayout;
import stroom.dashboard.client.flexlayout.FlexLayoutChangeHandler;
import stroom.dashboard.client.input.KeyValueInputPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.DashboardPresenter.DashboardView;
import stroom.dashboard.client.query.CurrentSelectionPresenter;
import stroom.dashboard.client.query.QueryInfo;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.KeyValueInputComponentSettings;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.LayoutConstraints;
import stroom.dashboard.shared.Size;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.query.api.ParamUtil;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequestSource;
import stroom.query.client.presenter.QueryToolbarPresenter;
import stroom.query.client.presenter.SearchErrorListener;
import stroom.query.client.presenter.SearchStateListener;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.task.client.HasTaskMonitorFactory;
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
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DashboardPresenter
        extends DocumentEditPresenter<DashboardView, DashboardDoc>
        implements
        FlexLayoutChangeHandler,
        DocumentTabData,
        HasToolbar,
        SearchStateListener,
        SearchErrorListener {

    private static final String VERSION_7_2_0 = Version.of(7, 2, 0).toString();
    private static final String DEFAULT_PARAMS_INPUT = "Params";

    private static final Logger logger = Logger.getLogger(DashboardPresenter.class.getName());
    private final FlexLayout layoutPresenter;
    private final Components components;
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
    private Size preferredSize = new Size();
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
            setDirty(true);
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
                setDirty(true);
                layoutConstraints = e.getValue();
                layoutPresenter.setLayoutConstraints(layoutConstraints);
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
        final HandlerRegistration handlerRegistration = dashboardContext
                .addContextChangeHandler(e -> currentSelectionPresenter.refresh(dashboardContext, false));
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
            LayoutConfig layoutConfig = null;

            final DashboardConfig dashboardConfig = dashboard.getDashboardConfig();
            if (dashboardConfig != null) {
                queryToolbarPresenter.setTimeRange(dashboardConfig.getTimeRange());

                layoutConfig = dashboardConfig.getLayout();
                layoutConstraints = dashboardConfig.getLayoutConstraints();
                if (layoutConstraints == null) {
                    layoutConstraints = new LayoutConstraints(true, true);
                }
                preferredSize = dashboardConfig.getPreferredSize();
                if (preferredSize == null) {
                    preferredSize = new Size();
                }

                List<ComponentConfig> componentConfigList = dashboardConfig.getComponents();

                // ADD A KEY/VALUE PARAMETER INPUT BOX FOR BACKWARD COMPATIBILITY.
                if (dashboardConfig.getModelVersion() == null) {
                    if (componentConfigList == null) {
                        componentConfigList = new ArrayList<>();
                        dashboardConfig.setComponents(componentConfigList);
                    }

                    final String params = NullSafe.string(dashboardConfig.getParameters());

                    componentConfigList
                            .add(new ComponentConfig(
                                    KeyValueInputPresenter.TYPE.getId(),
                                    DEFAULT_PARAMS_INPUT,
                                    DEFAULT_PARAMS_INPUT,
                                    new KeyValueInputComponentSettings(params)));
                    final TabConfig tabConfig = new TabConfig(DEFAULT_PARAMS_INPUT, true);
                    final List<TabConfig> tabs = new ArrayList<>();
                    tabs.add(tabConfig);
                    final TabLayoutConfig tabLayoutConfig =
                            new TabLayoutConfig(new Size(200, 76), tabs, null);
                    final List<LayoutConfig> children = new ArrayList<>();
                    children.add(tabLayoutConfig);
                    children.add(layoutConfig);
                    layoutConfig = new SplitLayoutConfig(new Size(200, 76), Dimension.Y, children);
                    dashboardConfig.setLayout(layoutConfig);
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

            // Turn on design mode if this is a new dashboard.
            if (dashboardConfig != null &&
                dashboardConfig.getDesignMode() != null &&
                dashboardConfig.getDesignMode()) {
                editModeButton.setState(true);
                setDesignMode(true);
            }
        } else {
            // Turn on design mode if this is a read after a save or save as.
            setDesignMode(true);
        }

        addComponentButton.setEnabled(!readOnly && !embedded);
        setConstraintsButton.setEnabled(!readOnly && !embedded);
        selectionInfoButton.setEnabled(!readOnly && !embedded);
    }

    private Component addComponent(final String type, final ComponentConfig componentConfig) {
        final Component component = components.add(type, componentConfig.getId());
        if (component != null) {
            component.setDashboardContext(dashboardContext);
//            component.setDesignMode(designMode);

            if (component instanceof HasDirtyHandlers) {
                ((HasDirtyHandlers) component).addDirtyHandler(event -> setDirty(true));
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

        final DashboardConfig dashboardConfig = new DashboardConfig();
        dashboardConfig.setTimeRange(queryToolbarPresenter.getTimeRange());
        dashboardConfig.setComponents(componentDataList);
        dashboardConfig.setLayout(layoutPresenter.getLayoutConfig());
        dashboardConfig.setLayoutConstraints(layoutConstraints);
        dashboardConfig.setPreferredSize(preferredSize);
        dashboardConfig.setDesignMode(false);
        dashboardConfig.setModelVersion(VERSION_7_2_0);
        dashboard.setDashboardConfig(dashboardConfig);
        return dashboard;
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

    @Override
    public void onDirty() {
//        if (designMode) {
        setDirty(true);
//        }
    }

    public void duplicateTabTo(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
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

    public void duplicateTab(final TabLayoutConfig tabLayoutConfig, final TabConfig tab) {
        duplicateTabs(tabLayoutConfig, Collections.singletonList(tab), components);
    }

    public void duplicateTab(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig,
                             final Components components) {
        duplicateTabs(tabLayoutConfig, Collections.singletonList(tabConfig), components);
    }

    public void duplicateTabPanel(final TabLayoutConfig tabLayoutConfig) {
        duplicateTabs(tabLayoutConfig, new ArrayList<>(tabLayoutConfig.getTabs()), components);
    }

    public void duplicateTabs(final TabLayoutConfig tabLayoutConfig, final List<TabConfig> tabs,
                              final Components orginalComponents) {
        // Get sets of unique component ids and names.
        final ComponentNameSet componentNameSet = new ComponentNameSet(this.components);
        final Map<String, String> idMapping = new HashMap<>();
        final List<ComponentConfig> newComponents = new ArrayList<>();
        final Map<String, TabConfig> newTabConfigMap = new HashMap<>();
        if (tabs != null) {
            for (final TabConfig tabConfig : tabs) {
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

                final TabConfig newTabConfig = tabConfig.copy().id(componentId.id).build();
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
                final TabConfig newTabConfig = newTabConfigMap.get(component.getId());
                component.setTabConfig(newTabConfig);
                duplicatedComponents.add(component);
            }
        }

        // Now link all the components.
        for (final Component component : duplicatedComponents) {
            component.link();
        }

        if (!duplicatedComponents.isEmpty()) {
            final TabConfig firstTabConfig = getFirstTabConfig(tabLayoutConfig);
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
    public void removeTab(final TabLayoutConfig tabLayoutConfig, final TabConfig tab) {
        removeTabs(tabLayoutConfig, Collections.singletonList(tab));
    }

    @Override
    public void removeTabPanel(final TabLayoutConfig tabLayoutConfig) {
        removeTabs(tabLayoutConfig, new ArrayList<>(tabLayoutConfig.getTabs()));
    }

    private void removeTabs(final TabLayoutConfig tabLayoutConfig, final List<TabConfig> tabs) {
        // Figure out what tabs would remain after removal.
        int hiddenCount = 0;
        int totalCount = 0;
        for (final TabConfig tab : tabLayoutConfig.getTabs()) {
            if (!tabs.contains(tab)) {
                if (!tab.visible()) {
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
                    for (final TabConfig tab : tabs) {
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

    public void maximiseTabs(final TabConfig tabConfig) {
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
    public void onDirty(final boolean dirty) {
        super.onDirty(dirty);

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
                final TabConfig tabConfig = new TabConfig(componentId.id, true);
                componentPresenter.setTabConfig(tabConfig);

                final TabConfig firstTabConfig = getFirstTabConfig(layoutPresenter.getLayoutConfig());
                if (firstTabConfig == null) {
                    // Add the panel directly.
                    // Note that as we don't have any panels then size it to fit the visible area.
                    final Size visibleSize = layoutPresenter.getVisibleSize();
                    preferredSize.setWidth(visibleSize.getWidth());
                    preferredSize.setHeight(visibleSize.getHeight());

                    final TabLayoutConfig tabLayoutConfig =
                            new TabLayoutConfig(visibleSize, null, 0);
                    tabLayoutConfig.add(tabConfig);

                    layoutPresenter.configure(tabLayoutConfig, layoutConstraints, preferredSize);
                    setDirty(true);

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

    private Element getFirstComponentElement(final TabConfig firstTabConfig) {
        if (firstTabConfig != null) {
            final Component component = components.get(firstTabConfig.getId());
            if (component != null) {
                final MyPresenterWidget<?> myPresenterWidget = (MyPresenterWidget<?>) component;
                return myPresenterWidget.getWidget().getElement();
            }
        }
        return getWidget().getElement();
    }

    private TabConfig getFirstTabConfig(final LayoutConfig layoutConfig) {
        if (layoutConfig != null) {
            if (layoutConfig instanceof final SplitLayoutConfig splitLayoutConfig) {
                final List<LayoutConfig> list = splitLayoutConfig.getChildren();
                if (list != null) {
                    for (final LayoutConfig child : list) {
                        final TabConfig tabConfig = getFirstTabConfig(child);
                        if (tabConfig != null) {
                            return tabConfig;
                        }
                    }
                }

            } else if (layoutConfig instanceof final TabLayoutConfig tabLayoutConfig) {
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

//    private void addTabPanel(final TabLayoutConfig tabLayoutConfig) {
//        // Choose where to put the new component in the layout data.
//        LayoutConfig layoutConfig = layoutPresenter.getLayoutConfig();
//        if (layoutConfig == null) {
//            // There is no existing layout so add the new item as a
//            // single item layout.
//
//            layoutConfig = tabLayoutConfig;
//
//        } else if (layoutConfig instanceof TabLayoutConfig) {
//            // If the layout is a single item then replace it with a
//            // split layout.
//            final SplitLayoutConfig splitLayoutConfig =
//                    new SplitLayoutConfig(layoutConfig.getPreferredSize().copy().build(), Dimension.Y);
//            splitLayoutConfig.add(layoutConfig);
//            splitLayoutConfig.add(tabLayoutConfig);
//            layoutConfig = splitLayoutConfig;
//
//        } else {
//            // If the layout is already a split then add a new component
//            // to the split.
//            final SplitLayoutConfig parent = (SplitLayoutConfig) layoutConfig;
//
//            // Add the new component.
//            parent.add(tabLayoutConfig);
//
//            // Fix the heights of the components to fit the new
//            // component in.
//            fixHeights(parent);
//        }
//
//        layoutPresenter.configure(layoutConfig, layoutConstraints, preferredSize);
//        setDirty(true);
//    }
//
//    private void fixHeights(final SplitLayoutConfig parent) {
//        // Create a default size to use.
//        final Size defaultSize = new Size();
//
//        if (parent.count() > 1) {
//            final LayoutConfig previousComponent = parent.get(parent.count() - 2);
//            final int height = previousComponent.getPreferredSize().getHeight();
//
//            // See if the previous component has enough height to be split
//            // to include the new component.
//            if (height > (defaultSize.getHeight() * 2)) {
//                previousComponent.getPreferredSize().setHeight(height - defaultSize.getHeight());
//            } else {
//                // The previous component isn't high enough so resize all
//                // components to fit.
//                lazyRedistribution(parent);
//            }
//        }
//    }
//
//    private void lazyRedistribution(final SplitLayoutConfig parent) {
//        // Create a default size to use.
//        final Size defaultSize = new Size();
//
//        // See if we can get the currently presented position and size for
//        // the parent layout.
//        final PositionAndSize positionAndSize = layoutPresenter.getPositionAndSize(parent);
//        if (positionAndSize != null) {
//            // Get the current height of the split layout.
//            final double height = positionAndSize.getHeight();
//
//            final double totalHeight = getTotalHeight(parent);
//            if (height > 0 && totalHeight > height) {
//                double amountToSave = totalHeight - height;
//
//                // Try and set heights to the default height to claw back
//                // space we want to save.
//                for (int i = parent.count() - 1; i >= 0; i--) {
//                    final LayoutConfig ld = parent.get(i);
//                    final Size size = ld.getPreferredSize();
//                    final double diff = size.getHeight() - defaultSize.getHeight();
//                    if (diff > 0) {
//                        if (diff > amountToSave) {
//                            size.setHeight((int) (size.getHeight() - amountToSave));
//                            amountToSave = 0;
//                            break;
//                        } else {
//                            size.setHeight(defaultSize.getHeight());
//                            amountToSave -= diff;
//                        }
//                    }
//                }
//
//                // If we have more space we need to save then try and
//                // distribute space evenly between widgets.
//                if (amountToSave > 0) {
//                    fairRedistribution(parent, height);
//                }
//            }
//        } else {
//            // We have no idea what size the parnet container is occupying
//            // so just reset all heights.
//            resetAllHeights(parent);
//        }
//    }
//
//    private void fairRedistribution(final SplitLayoutConfig parent, final double height) {
//        // Find out how high each component could be if they were all the
//        // same height.
//        double fairHeight = (height / parent.count());
//        fairHeight = Math.max(0D, fairHeight);
//
//        double used = 0;
//        int count = 0;
//
//        // Try and find the components that are bigger than their fair size
//        // and remember the amount of space used by smaller components.
//        for (int i = parent.count() - 1; i >= 0; i--) {
//            final LayoutConfig ld = parent.get(i);
//            final Size size = ld.getPreferredSize();
//            if (size.getHeight() > fairHeight) {
//                count++;
//            } else {
//                used += size.getHeight();
//            }
//        }
//
//        // Calculate the height to set all components that are bigger than
//        // the available height.
//        if (count > 0) {
//            final double newHeight = ((height - used) / count);
//            for (int i = parent.count() - 1; i >= 0; i--) {
//                final LayoutConfig ld = parent.get(i);
//                final Size size = ld.getPreferredSize();
//                if (size.getHeight() > fairHeight) {
//                    size.setHeight((int) newHeight);
//                }
//            }
//        }
//    }
//
//    private void resetAllHeights(final SplitLayoutConfig parent) {
//        final Size defaultSize = new Size();
//        for (int i = 0; i < parent.count(); i++) {
//            final LayoutConfig ld = parent.get(i);
//            final Size size = ld.getPreferredSize();
//            if (size.getHeight() > defaultSize.getHeight()) {
//                size.setHeight(defaultSize.getHeight());
//            }
//        }
//    }
//
//    private double getTotalHeight(final SplitLayoutConfig parent) {
//        double totalHeight = 0;
//        for (int i = parent.count() - 1; i >= 0; i--) {
//            final LayoutConfig ld = parent.get(i);
//            final Size size = ld.getPreferredSize();
//            totalHeight += size.getHeight();
//        }
//        return totalHeight;
//    }

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

