/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.query;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.LocationManager;
import stroom.dashboard.client.HasSelection;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.client.main.DashboardUUID;
import stroom.dashboard.client.main.DataSourceFieldsMap;
import stroom.dashboard.client.main.IndexLoader;
import stroom.dashboard.client.main.Queryable;
import stroom.dashboard.client.main.SearchBus;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.main.SearchModel.Mode;
import stroom.dashboard.client.table.TimeZones;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadQueryRequest;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.datasource.api.v2.AbstractField;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.shared.PipelineDoc;
import stroom.preferences.client.UserPreferencesManager;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Limits;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionUiHandlers;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResourceGeneration;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QueryPresenter extends AbstractComponentPresenter<QueryPresenter.QueryView>
        implements HasDirtyHandlers, Queryable {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    public static final ComponentType TYPE = new ComponentType(0, "query", "Query");
    static final int TEN_SECONDS = 10000;

    private final ExpressionTreePresenter expressionPresenter;
    private final QueryHistoryPresenter historyPresenter;
    private final QueryFavouritesPresenter favouritesPresenter;
    private final Provider<EntityChooser> pipelineSelection;
    private final Provider<QueryInfoPresenter> queryInfoPresenterProvider;
    private final ProcessorLimitsPresenter processorLimitsPresenter;
    private final RestFactory restFactory;
    private final LocationManager locationManager;

    private final IndexLoader indexLoader;
    private final SearchModel searchModel;
    private final ButtonView addOperatorButton;
    private final ButtonView addTermButton;
    private final ButtonView disableItemButton;
    private final ButtonView deleteItemButton;
    private final ButtonView historyButton;
    private final ButtonView favouriteButton;
    private final ButtonView downloadQueryButton;
    private final ButtonView warningsButton;

    private String params;
    private List<String> currentWarnings;
    private ButtonView processButton;
    private long defaultProcessorTimeLimit;
    private long defaultProcessorRecordLimit;
    private boolean initialised;
    private Timer autoRefreshTimer;
    private String lastUsedQueryInfo;
    private boolean queryOnOpen;

    @Inject
    public QueryPresenter(final EventBus eventBus,
                          final QueryView view,
                          final SearchBus searchBus,
                          final Provider<QuerySettingsPresenter> settingsPresenterProvider,
                          final ExpressionTreePresenter expressionPresenter,
                          final QueryHistoryPresenter historyPresenter,
                          final QueryFavouritesPresenter favouritesPresenter,
                          final Provider<EntityChooser> pipelineSelection,
                          final Provider<QueryInfoPresenter> queryInfoPresenterProvider,
                          final ProcessorLimitsPresenter processorLimitsPresenter,
                          final RestFactory restFactory,
                          final ClientSecurityContext securityContext,
                          final UiConfigCache clientPropertyCache,
                          final LocationManager locationManager,
                          final TimeZones timeZones,
                          final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view, settingsPresenterProvider);
        this.expressionPresenter = expressionPresenter;
        this.historyPresenter = historyPresenter;
        this.favouritesPresenter = favouritesPresenter;
        this.pipelineSelection = pipelineSelection;
        this.queryInfoPresenterProvider = queryInfoPresenterProvider;
        this.processorLimitsPresenter = processorLimitsPresenter;
        this.restFactory = restFactory;
        this.locationManager = locationManager;

        view.setExpressionView(expressionPresenter.getView());
        view.getQueryButtons().setUiHandlers(new QueryUiHandlers() {
            @Override
            public void start() {
                switch (searchModel.getMode()) {
                    case ACTIVE:
                        QueryPresenter.this.pause();
                        break;
                    case INACTIVE:
                        queryInfoPresenterProvider.get().show(lastUsedQueryInfo, state -> {
                            if (state.isOk()) {
                                lastUsedQueryInfo = state.getQueryInfo();
                                QueryPresenter.this.start();
                            }
                        });
                        break;
                    case PAUSED:
                        QueryPresenter.this.resume();
                        break;
                }
            }

            @Override
            public void stop() {
                QueryPresenter.this.stop();
            }
        });

        expressionPresenter.setUiHandlers(new ExpressionUiHandlers() {
            @Override
            public void fireDirty() {
                setDirty(true);
            }

            @Override
            public void search() {
                start();
            }
        });

        addTermButton = view.addButton(SvgPresets.ADD);
        addTermButton.setTitle("Add Term");
        addOperatorButton = view.addButton(SvgPresets.OPERATOR);
        disableItemButton = view.addButton(SvgPresets.DISABLE);
        deleteItemButton = view.addButton(SvgPresets.DELETE);
        historyButton = view.addButton(SvgPresets.HISTORY.enabled(true));
        favouriteButton = view.addButton(SvgPresets.FAVOURITES.enabled(true));
        downloadQueryButton = view.addButton(SvgPresets.DOWNLOAD);

        if (securityContext.hasAppPermission(PermissionNames.MANAGE_PROCESSORS_PERMISSION)) {
            processButton = view.addButton(SvgPresets.PROCESS.enabled(true));
        }

        warningsButton = view.addButton(SvgPresets.ALERT.title("Show Warnings"));
        warningsButton.setVisible(false);

        indexLoader = new IndexLoader(getEventBus(), restFactory);
        searchModel = new SearchModel(searchBus, indexLoader, timeZones, userPreferencesManager);
        searchModel.addErrorListener(this::setErrors);
        searchModel.addModeListener(this::setMode);

        clientPropertyCache.get()
                .onSuccess(result -> {
                    defaultProcessorTimeLimit = result.getProcess().getDefaultTimeLimit();
                    defaultProcessorRecordLimit = result.getProcess().getDefaultRecordLimit();
                })
                .onFailure(caught -> AlertEvent.fireError(QueryPresenter.this, caught.getMessage(), null));
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(expressionPresenter.addDataSelectionHandler(event -> setButtonsEnabled()));
        registerHandler(expressionPresenter.addContextMenuHandler(event -> {
            final List<Item> menuItems = addExpressionActionsToMenu();
            if (menuItems.size() > 0) {
                showMenu(menuItems, event.getPopupPosition());
            }
        }));
        registerHandler(addOperatorButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                addOperator();
            }
        }));
        registerHandler(addTermButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                addTerm();
            }
        }));
        registerHandler(disableItemButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                disable();
            }
        }));
        registerHandler(deleteItemButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                delete();
            }
        }));
        registerHandler(historyButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                historyPresenter.show(QueryPresenter.this, getComponents().getDashboard().getUuid());
            }
        }));
        registerHandler(favouriteButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                final ExpressionOperator root = expressionPresenter.write();
                favouritesPresenter.show(
                        QueryPresenter.this,
                        getComponents().getDashboard().getUuid(),
                        getQuerySettings().getDataSource(),
                        root);

            }
        }));
        if (processButton != null) {
            registerHandler(processButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    choosePipeline();
                }
            }));
        }
        registerHandler(warningsButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                showWarnings();
            }
        }));
        registerHandler(indexLoader.addChangeDataHandler(event ->
                loadedDataSource(indexLoader.getLoadedDataSourceRef(), indexLoader.getDataSourceFieldsMap())));

        registerHandler(downloadQueryButton.addClickHandler(event -> downloadQuery()));
    }

    @Override
    public void setComponents(final Components components) {
        super.setComponents(components);
        registerHandler(components.addComponentChangeHandler(event -> {
            if (initialised) {
                final Component component = event.getComponent();
                if (component instanceof HasSelection) {
                    final HasSelection hasSelection = (HasSelection) component;
                    final List<Map<String, String>> selection = hasSelection.getSelection();
                    final List<ComponentSelectionHandler> selectionHandlers = getQuerySettings().getSelectionHandlers();
                    if (selectionHandlers != null) {
                        final List<ComponentSelectionHandler> matchingHandlers = selectionHandlers
                                .stream()
                                .filter(ComponentSelectionHandler::isEnabled)
                                .filter(selectionHandler -> selectionHandler.getComponentId() == null ||
                                        selectionHandler.getComponentId().equals(component.getId()))
                                .collect(Collectors.toList());

                        if (matchingHandlers.size() > 0) {
                            final Function<ExpressionOperator, ExpressionOperator> decorator = (in) -> {
                                final ExpressionOperator.Builder innerBuilder = ExpressionOperator
                                        .builder();
                                boolean added = false;
                                for (final ComponentSelectionHandler selectionHandler : matchingHandlers) {
                                    for (final Map<String, String> params : selection) {
                                        ExpressionOperator ex = selectionHandler.getExpression();
                                        ex = ExpressionUtil.replaceExpressionParameters(ex, params);
                                        innerBuilder.addOperator(ex);

                                        if (!added) {
                                            added = true;
                                        } else {
                                            innerBuilder.op(Op.OR);
                                        }
                                    }
                                }

                                if (added) {
                                    return ExpressionOperator
                                            .builder()
                                            .addOperator(in)
                                            .addOperator(innerBuilder.build())
                                            .build();
                                }

                                return in;
                            };

//                          this.params = params;
//                          lastUsedQueryInfo = null;

                            stop();
                            run(true, true, decorator);
                        }
                    }
                }
            }

//            if (component instanceof HasAbstractFields) {
//                final VisPresenter visPresenter = (VisPresenter) component;
//                final List<Map<String, String>> selection = visPresenter.getCurrentSelection();
//                String params = "";
//                if (selection != null) {
//                    for (final Map<String, String> map : selection) {
//                        for (final Entry<String, String> entry : map.entrySet()) {
//                            params += entry.getKey() + "=" + entry.getValue() + " ";
//                        }
//                    }
//                }
//                onQuery(params, null);
//            }

//                if (getTextSettings().getTableId() == null) {
//                    if (component instanceof TablePresenter) {
//                        currentTablePresenter = (TablePresenter) component;
//                        update(currentTablePresenter);
//                    }
//                } else if (EqualsUtil.isEquals(getTextSettings().getTableId(), event.getComponentId())) {
//                    if (component instanceof TablePresenter) {
//                        currentTablePresenter = (TablePresenter) component;
//                        update(currentTablePresenter);
//                    }
//                }
//            }
        }));
    }

    @Override
    public void addModeListener(final Consumer<Mode> consumer) {
        searchModel.addModeListener(consumer);
    }

    @Override
    public void removeModeListener(final Consumer<Mode> consumer) {
        searchModel.removeModeListener(consumer);
    }

    public void setErrors(final List<String> errors) {
        currentWarnings = errors;
        warningsButton.setVisible(currentWarnings != null && !currentWarnings.isEmpty());
    }

    private void setButtonsEnabled() {
        final stroom.query.client.Item selectedItem = getSelectedItem();

        if (selectedItem == null) {
            disableItemButton.setEnabled(false);
            disableItemButton.setTitle("");
        } else {
            disableItemButton.setEnabled(true);
            disableItemButton.setTitle(getEnableDisableText());
        }

        if (selectedItem == null) {
            deleteItemButton.setEnabled(false);
            deleteItemButton.setTitle("");
        } else {
            deleteItemButton.setEnabled(true);
            deleteItemButton.setTitle("Delete");
        }

        final DocRef dataSourceRef = getQuerySettings().getDataSource();

        if (dataSourceRef == null) {
            downloadQueryButton.setEnabled(false);
            downloadQueryButton.setTitle("");
        } else {
            downloadQueryButton.setEnabled(true);
            downloadQueryButton.setTitle("Download Query");
        }
    }

    private void loadDataSource(final DocRef dataSourceRef) {
        searchModel.getIndexLoader().loadDataSource(dataSourceRef);
        setButtonsEnabled();
    }

    private void loadedDataSource(final DocRef dataSourceRef, final DataSourceFieldsMap dataSourceFieldsMap) {
        // Create a list of index fields.
        final List<AbstractField> fields = new ArrayList<>();
        if (dataSourceFieldsMap != null) {
            for (final AbstractField field : dataSourceFieldsMap.values()) {
                // Protection from default values of false not being in the serialised json
                if (field.getQueryable() != null
                        ? field.getQueryable()
                        : false) {
                    fields.add(field);
                }
            }
        }
        fields.sort(Comparator.comparing(AbstractField::getName, String.CASE_INSENSITIVE_ORDER));
        expressionPresenter.init(restFactory, dataSourceRef, fields);

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(getQuerySettings().getDataSource(), dataSourceRef);

        if (!builder.isEquals()) {
            setSettings(getQuerySettings()
                    .copy()
                    .dataSource(dataSourceRef)
                    .build());
            setDirty(true);
        }

        // Only allow searching if we have a data source and have loaded fields from it successfully.
        getView().setEnabled(dataSourceRef != null && fields.size() > 0);

        init();
        setButtonsEnabled();
    }

    private void addOperator() {
        expressionPresenter.addOperator();
    }

    private void addTerm() {
        final DocRef dataSourceRef = getQuerySettings().getDataSource();

        if (dataSourceRef == null) {
            warnNoDataSource();
        } else {
            expressionPresenter.addTerm();
        }
    }

    private void warnNoDataSource() {
        AlertEvent.fireWarn(this, "No data source has been chosen to search", null);
    }

    private void disable() {
        expressionPresenter.disable();
        setButtonsEnabled();
    }

    private void delete() {
        expressionPresenter.delete();
    }

    private void choosePipeline() {
        expressionPresenter.clearSelection();
        // Write expression.
        final ExpressionOperator root = expressionPresenter.write();

        final QueryData queryData = new QueryData();
        queryData.setDataSource(getQuerySettings().getDataSource());
        queryData.setExpression(root);
        queryData.setParams(params);

        final EntityChooser chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Process Results With");
        chooser.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        chooser.setRequiredPermissions(DocumentPermissionNames.USE);
        chooser.addDataSelectionHandler(event -> {
            final DocRef pipeline = chooser.getSelectedEntityReference();
            if (pipeline != null) {
                setProcessorLimits(queryData, pipeline);
            }
        });

        chooser.show();
    }

    private void setProcessorLimits(final QueryData queryData, final DocRef pipeline) {
        processorLimitsPresenter.setTimeLimitMins(defaultProcessorTimeLimit);
        processorLimitsPresenter.setRecordLimit(defaultProcessorRecordLimit);
        ShowPopupEvent.builder(processorLimitsPresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Process Search Results")
                .onShow(e -> processorLimitsPresenter.getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final Limits limits = new Limits();
                        if (processorLimitsPresenter.getRecordLimit() != null) {
                            limits.setEventCount(processorLimitsPresenter.getRecordLimit());
                        }
                        if (processorLimitsPresenter.getTimeLimitMins() != null) {
                            limits.setDurationMs(processorLimitsPresenter.getTimeLimitMins() * 60 * 1000);
                        }
                        queryData.setLimits(limits);
                        openEditor(queryData, pipeline);
                    }
                    e.hide();
                })
                .fire();
    }

    private void openEditor(final QueryData queryData, final DocRef pipeline) {
        // Now create the processor filter using the find stream criteria.
        final CreateProcessFilterRequest request = CreateProcessFilterRequest
                .builder()
                .pipeline(pipeline)
                .queryData(queryData)
                .priority(1)
                .build();
        final Rest<ProcessorFilter> rest = restFactory.create();
        rest
                .onSuccess(streamProcessorFilter -> {
                    if (streamProcessorFilter != null) {
                        CreateProcessorEvent.fire(QueryPresenter.this, streamProcessorFilter);
                    } else {
                        AlertEvent.fireInfo(this, "Created batch processor", null);
                    }
                })
                .call(PROCESSOR_FILTER_RESOURCE)
                .create(request);
    }

    private void showWarnings() {
        if (currentWarnings != null && !currentWarnings.isEmpty()) {
            final String errors = String.join("\n", currentWarnings);
            AlertEvent.fireWarn(this,
                    "The following warnings have been created while running this search:",
                    errors, null);
        }
    }

    @Override
    public void setParams(final String params) {
        this.params = params;
    }

    @Override
    public void setQueryInfo(final String queryInfo) {
        lastUsedQueryInfo = queryInfo;
    }

//    @Override
//    public void onQuery(final String params, final String queryInfo) {
//        this.params = params;
//        lastUsedQueryInfo = queryInfo;
//        if (initialised) {
//            stop();
//            run(true, true);
//        }
//    }

    @Override
    public void setQueryOnOpen(final boolean queryOnOpen) {
        this.queryOnOpen = queryOnOpen;
    }

    @Override
    public void start() {
        run(true, true);
    }

    @Override
    public void pause() {
        searchModel.pause();
    }

    @Override
    public void resume() {
        searchModel.resume();
    }

    @Override
    public void stop() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
            autoRefreshTimer = null;
        }
        searchModel.stop();
    }

    @Override
    public Mode getMode() {
        return searchModel.getMode();
    }

    private void run(final boolean incremental,
                     final boolean storeHistory) {
        run(incremental, storeHistory, Function.identity());
    }

    private void run(final boolean incremental,
                     final boolean storeHistory,
                     final Function<ExpressionOperator, ExpressionOperator> expressionDecorator) {
        final DocRef dataSourceRef = getQuerySettings().getDataSource();

        if (dataSourceRef == null) {
            warnNoDataSource();
        } else {
            currentWarnings = null;
            expressionPresenter.clearSelection();

            warningsButton.setVisible(false);

            // Write expression.
            final ExpressionOperator root = expressionPresenter.write();
            final ExpressionOperator decorated = expressionDecorator.apply(root);

            // Start search.
            searchModel.reset();
            searchModel.startNewSearch(decorated, params, incremental, storeHistory, lastUsedQueryInfo);
        }
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof QueryComponentSettings)) {
            setSettings(QueryComponentSettings.builder()
                    .build());
        }

        if (getQuerySettings().getAutomate() == null) {
            final Automate automate = Automate.builder().build();
            setSettings(getQuerySettings()
                    .copy()
                    .automate(automate)
                    .build());
        }

        // Create and register the search model.
        final DashboardDoc dashboard = getComponents().getDashboard();
        final DashboardUUID dashboardUUID = new DashboardUUID(dashboard.getUuid(),
                dashboard.getName(),
                getComponentConfig().getId());
        searchModel.setDashboardUUID(dashboardUUID);

        // Read data source.
        loadDataSource(getQuerySettings().getDataSource());

        // Read expression.
        ExpressionOperator root = getQuerySettings().getExpression();
        if (root == null) {
            root = ExpressionOperator.builder().build();
        }
        setExpression(root);
    }

    @Override
    public ComponentConfig write() {
        // Write expression.
        setSettings(getQuerySettings()
                .copy()
                .expression(expressionPresenter.write())
                .build());
        return super.write();
    }

    private QueryComponentSettings getQuerySettings() {
        return (QueryComponentSettings) getSettings();
    }

    @Override
    public void onRemove() {
        super.onRemove();
        stop();
        initialised = false;
    }

    @Override
    public void link() {
    }

    private void init() {
        if (!initialised) {
            initialised = true;
            // An auto search can only commence if the UI has fully loaded and the data source has also
            // loaded from the server.
            final Automate automate = getQuerySettings().getAutomate();
            if (queryOnOpen || automate.isOpen()) {
                run(true, false);
            }
        }
    }

    @Override
    public void changeSettings() {
        super.changeSettings();
        loadDataSource(getQuerySettings().getDataSource());
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public ComponentType getType() {
        return TYPE;
    }

    public SearchModel getSearchModel() {
        return searchModel;
    }

    public void setExpression(final ExpressionOperator root) {
        expressionPresenter.read(root);
    }

    public void setMode(final SearchModel.Mode mode) {
        getView().setMode(mode);

        // If this is the end of a query then schedule a refresh.
        if (SearchModel.Mode.INACTIVE.equals(mode)) {
            scheduleRefresh();
        }
    }

    private void scheduleRefresh() {
        // Schedule auto refresh after a query has finished.
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
        }
        autoRefreshTimer = null;

        final Automate automate = getQuerySettings().getAutomate();
        if (initialised && automate.isRefresh()) {
            try {
                final String interval = automate.getRefreshInterval();
                int millis = ModelStringUtil.parseDurationString(interval).intValue();

                // Ensure that the refresh interval is not less than 10 seconds.
                millis = Math.max(millis, TEN_SECONDS);

                autoRefreshTimer = new Timer() {
                    @Override
                    public void run() {
                        if (!initialised) {
                            stop();
                        } else {
                            // Make sure search is currently inactive before we attempt to execute a new query.
                            if (SearchModel.Mode.INACTIVE.equals(searchModel.getMode())) {
                                QueryPresenter.this.run(false, false);
                            }
                        }
                    }
                };
                autoRefreshTimer.schedule(millis);
            } catch (final RuntimeException e) {
                // Ignore as we cannot display this error now.
            }
        }
    }

    private List<Item> addExpressionActionsToMenu() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        final boolean hasSelection = selectedItem != null;

        final List<Item> menuItems = new ArrayList<>();
        menuItems.add(new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgPresets.ADD)
                .text("Add Term")
                .command(this::addTerm)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgPresets.OPERATOR)
                .text("Add Operator")
                .command(this::addOperator)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(3)
                .icon(SvgPresets.DISABLE)
                .text(getEnableDisableText())
                .enabled(hasSelection)
                .command(this::disable)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(4)
                .icon(SvgPresets.DELETE)
                .text("Delete")
                .enabled(hasSelection)
                .command(this::delete)
                .build());

        return menuItems;
    }

    private String getEnableDisableText() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        if (selectedItem != null && !selectedItem.isEnabled()) {
            return "Enable";
        }
        return "Disable";
    }

    private stroom.query.client.Item getSelectedItem() {
        if (expressionPresenter.getSelectionModel() != null) {
            return expressionPresenter.getSelectionModel().getSelectedObject();
        }
        return null;
    }

    private void showMenu(final List<Item> menuItems,
                          final PopupPosition popupPosition) {
        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .fire(this);
    }

    private void downloadQuery() {
        if (getQuerySettings().getDataSource() != null) {

            final DashboardSearchRequest searchRequest = searchModel.createDownloadQueryRequest(
                    expressionPresenter.write(),
                    params,
                    false,
                    false,
                    null);

            final DashboardDoc dashboard = getComponents().getDashboard();
            final DashboardUUID dashboardUUID = new DashboardUUID(
                    dashboard.getUuid(),
                    dashboard.getName(),
                    getComponentConfig().getId());
            final DashboardQueryKey dashboardQueryKey = new DashboardQueryKey(
                    dashboardUUID.getUUID(),
                    dashboard.getUuid(),
                    dashboardUUID.getComponentId());

            final Rest<ResourceGeneration> rest = restFactory.create();
            rest
                    .onSuccess(result ->
                            ExportFileCompleteUtil.onSuccess(locationManager, null, result))
                    .call(DASHBOARD_RESOURCE)
                    .downloadQuery(new DownloadQueryRequest(dashboardQueryKey, searchRequest));
        }
    }

    public interface QueryView extends View {

        ButtonView addButton(Preset preset);

        void setExpressionView(View view);

        void setMode(SearchModel.Mode mode);

        void setEnabled(boolean enabled);

        QueryButtons getQueryButtons();
    }
}
