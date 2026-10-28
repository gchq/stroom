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

package stroom.dashboard.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.FireAlertEventFunction;
import stroom.core.client.LocationManager;
import stroom.core.client.event.WindowCloseEvent;
import stroom.core.client.messages.ErrorMessageTemplates;
import stroom.dashboard.client.component.QueryPane.QueryPaneAction;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.main.IndexLoader;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.query.QueryInfo;
import stroom.dashboard.client.query.SelectionHandlerExpressionBuilder;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Limits;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.QueryData;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultStoreInfo;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.DynamicFieldSelectionListModel;
import stroom.query.client.ResultStoreModel;
import stroom.query.client.SearchErrorListener;
import stroom.query.client.SearchStateListener;
import stroom.query.client.component.ExpressionTreeController;
import stroom.query.shared.ResultStoreResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.ErrorMessages;
import stroom.util.shared.Severity;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.popup.client.PopupPosition;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// A dashboard's query pane ([QueryPane]): its expression, which it searches the data source with,
/// the search's messages, its history and favourites, downloading it and processing it with a
/// pipeline.
public class QueryPaneController
        extends RefreshableDashboardComponentController
        implements SearchStateListener, SearchErrorListener {

    public static final String TAB_TYPE = "query-component";
    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    private static final ResultStoreResource RESULT_STORE_RESOURCE = GWT.create(ResultStoreResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);
    private static final ErrorMessageTemplates ERROR_MESSAGE_TEMPLATES = GWT.create(ErrorMessageTemplates.class);

    public static final ComponentType TYPE = new ComponentType(0, "query", "Query", ComponentUse.PANEL);

    private final ExpressionTreeController expressionTree;
    private final QueryHistoryController history;
    private final QueryFavouritesController favourites;
    private final Provider<ExplorerPopupController> pipelineSelection;
    private final Provider<ProcessorLimitsController> processorLimitsControllerProvider;
    private final RestFactory restFactory;
    private final UiConfigCache clientPropertyCache;
    private final LocationManager locationManager;
    private final IndexLoader indexLoader;
    private final DynamicFieldSelectionListModel fieldSelectionBoxModel;
    private final QueryPane view;
    private final SearchModel searchModel;
    private ErrorMessages currentErrors;
    private boolean initialised;
    private boolean queryOnOpen;
    private QueryInfo queryInfo;
    private ExpressionOperator currentSelectionQuery;

    /// Made by GIN, with its content and its parts.
    @Inject
    public QueryPaneController(final EventBus eventBus,
                               final QueryPane view,
                               final Provider<QuerySettingsController> settingsControllerProvider,
                               final ExpressionTreeController expressionTree,
                               final QueryHistoryController history,
                               final QueryFavouritesController favourites,
                               final Provider<ExplorerPopupController> pipelineSelection,
                               final Provider<ProcessorLimitsController> processorLimitsControllerProvider,
                               final IndexLoader indexLoader,
                               final RestFactory restFactory,
                               final ClientSecurityContext securityContext,
                               final UiConfigCache clientPropertyCache,
                               final LocationManager locationManager,
                               final DateTimeSettingsFactory dateTimeSettingsFactory,
                               final ResultStoreModel resultStoreModel,
                               final DynamicFieldSelectionListModel fieldSelectionBoxModel) {
        super(eventBus, settingsControllerProvider);
        this.view = view;
        initWidget(view);
        own(expressionTree);
        own(history);
        own(favourites);
        this.expressionTree = expressionTree;
        this.history = history;
        this.favourites = favourites;
        this.pipelineSelection = pipelineSelection;
        this.processorLimitsControllerProvider = processorLimitsControllerProvider;
        this.indexLoader = indexLoader;
        this.restFactory = restFactory;
        this.clientPropertyCache = clientPropertyCache;
        this.locationManager = locationManager;
        this.fieldSelectionBoxModel = fieldSelectionBoxModel;
        // Shows its requests' progress as this controller's owners do (its context's)
        fieldSelectionBoxModel.setTaskMonitorFactory(taskMonitor());

        view.setExpressionTree(expressionTree);
        view.setOnAction(this::onAction);
        view.setProcessVisible(securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION));

        expressionTree.setOnChange(this::onChange);
        expressionTree.setOnSearch(this::promptAndStart);

        searchModel = new SearchModel(
                eventBus,
                restFactory,
                indexLoader,
                dateTimeSettingsFactory,
                resultStoreModel);
        // Shows its requests' progress as this controller's owners do (its context's)
        searchModel.setTaskMonitorFactory(taskMonitor());
        searchModel.addSearchErrorListener(this);
        searchModel.addSearchStateListener(this);

        own(expressionTree.addSelectionListener(event -> setButtonsEnabled()));
        own(expressionTree.addContextMenuHandler(event -> {
            // On a term's field, the field's edit items come first
            final List<Item> menuItems = ContextMenuManager.withEditItems(
                    event.getEditTarget(), addExpressionActionsToMenu());
            if (!menuItems.isEmpty()) {
                showMenu(menuItems, event.getPopupPosition(), event.getEditTarget());
            }
        }));
        own(indexLoader.addChangeListener(loader ->
                loadedDataSource(loader.getLoadedDataSourceRef())));

        listen(WindowCloseEvent.getType(), event -> {
            // If a user is even attempting to close the browser or browser tab then destroy the query.
            searchModel.reset(DestroyReason.WINDOW_CLOSE);
        });
    }

    private void onAction(final QueryPaneAction action) {
        switch (action) {
            case ADD_TERM -> addTerm();
            case ADD_OPERATOR -> addOperator();
            case COPY -> copy();
            case DISABLE -> disable();
            case DELETE -> delete();
            case HISTORY -> onHistory();
            case FAVOURITES -> onFavourite();
            case DOWNLOAD -> downloadQuery();
            case PROCESS -> choosePipeline();
            case SHOW_MESSAGES -> showErrors();
            case RUN -> onRunPressed();
        }
    }


    private void onHistory() {
        history.show(getId(),
                getDashboardContext().getDashboardDocRef().getUuid(),
                this::setExpression);
    }

    private void onFavourite() {
        final ExpressionOperator root = expressionTree.write();
        favourites.show(
                getId(),
                getDashboardContext().getDashboardDocRef().getUuid(),
                getQuerySettings().getDataSource(),
                root,
                this::setExpression);
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        super.setDashboardContext(dashboardContext);
        own(dashboardContext.addContextChangeListener(() -> {
            if (initialised) {
                final ExpressionOperator selectionQuery = dashboardContext
                        .createSelectionHandlerExpression(getQuerySettings().getSelectionQuery())
                        .orElse(null);
                if (!Objects.equals(currentSelectionQuery, selectionQuery)) {
                    currentSelectionQuery = selectionQuery;
                    searchModel.reset(DestroyReason.NO_LONGER_NEEDED);
                    run(true, true, selectionQuery);
                }
            }

//            if (component instanceof HasAbstractFields) {
//                final VisPresenter visController = (VisPresenter) component;
//                final List<Map<String, String>> selection = visController.getCurrentSelection();
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
//                    if (component instanceof TableController) {
//                        currentTableController = (TableController) component;
//                        update(currentTableController);
//                    }
//                } else if (Objects.equals(getTextSettings().getTableId(), event.getComponentId())) {
//                    if (component instanceof TableController) {
//                        currentTableController = (TableController) component;
//                        update(currentTableController);
//                    }
//                }
//            }
        }));
    }

    @Override
    public void addSearchStateListener(final SearchStateListener listener) {
        searchModel.addSearchStateListener(listener);
    }

    @Override
    public void removeSearchStateListener(final SearchStateListener listener) {
        searchModel.removeSearchStateListener(listener);
    }

    @Override
    public void addSearchErrorListener(final SearchErrorListener listener) {
        searchModel.addSearchErrorListener(listener);
    }

    public void removeSearchErrorListener(final SearchErrorListener listener) {
        searchModel.removeSearchErrorListener(listener);
    }

    @Override
    public void setResultStoreInfo(final ResultStoreInfo resultStoreInfo) {
        searchModel.setResultStoreInfo(resultStoreInfo);
    }

    @Override
    public void onError(final List<ErrorMessage> errors) {
        currentErrors = new ErrorMessages(errors);
        view.setMessages(!currentErrors.isEmpty(), currentErrors.isEmpty()
                ? null
                : currentErrors.getHighestSeverity());
    }

    @Override
    public List<ErrorMessage> getCurrentErrors() {
        // Null until this query has searched, as for EmbeddedQueryController
        return currentErrors != null
                ? currentErrors.getErrorMessages()
                : null;
    }

    private void setButtonsEnabled() {
        final stroom.query.client.Item selectedItem = getSelectedItem();

        view.setItemActionsEnabled(selectedItem != null, getEnableDisableText() + " Selected Expression Item");
        view.setDownloadEnabled(getQuerySettings().getDataSource() != null);
    }

    private void loadDataSource(final DocRef dataSourceRef) {
        searchModel.getIndexLoader().loadDataSource(dataSourceRef);
        setButtonsEnabled();
    }

    private void loadedDataSource(final DocRef dataSourceRef) {
        fieldSelectionBoxModel.setDataSourceRefConsumer(consumer -> consumer.accept(dataSourceRef));
        // We only want queryable fields.
        fieldSelectionBoxModel.setQueryable(true);
        expressionTree.init(dataSourceRef, fieldSelectionBoxModel);

        if (!Objects.equals(getQuerySettings().getDataSource(), dataSourceRef)) {
            setSettings(getQuerySettings()
                    .copy()
                    .dataSource(dataSourceRef)
                    .build());
            onChange();
        }

        // Only allow searching if we have a data source and have loaded fields from it successfully.
        view.setEnabled(dataSourceRef != null);

        setButtonsEnabled();

        // Defer init until all data source load handlers have had a chance to update the loaded data source.
        Scheduler.get().scheduleDeferred(() -> {
            // Not once the pane has gone
            if (!isDisposed()) {
                init();
            }
        });
    }

    private void addOperator() {
        expressionTree.addOperator();
    }

    private void copy() {
        expressionTree.copy();
    }

    private void addTerm() {
        final DocRef dataSourceRef = getQuerySettings().getDataSource();

        if (dataSourceRef == null) {
            warnNoDataSource();
        } else {
            expressionTree.addTerm();
        }
    }

    private void warnNoDataSource() {
        AlertEvent.fireWarn(this, "No data source has been chosen to search", null);
    }

    private void disable() {
        expressionTree.disable();
        setButtonsEnabled();
    }

    private void delete() {
        expressionTree.delete();
    }

    private void choosePipeline() {
        expressionTree.clearSelection();
        // Write expression.
        final ExpressionOperator root = expressionTree.write();

        final DashboardContext dashboardContext = getDashboardContext();
        final QueryData queryData = QueryData
                .builder()
                .dataSource(getQuerySettings().getDataSource())
                .expression(root)
                .params(dashboardContext.getParams())
                .timeRange(dashboardContext.getResolvedTimeRange())
                .build();

        final ExplorerPopupController chooser = pipelineSelection.get();
        chooser.setIncludeNullSelection(false);
        chooser.setCaption("Choose Pipeline To Process Results With");
        chooser.setIncludedTypes(PipelineDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.USE);
        chooser.showOnce(this, pipeline -> {
            if (pipeline != null) {
                setProcessorLimits(queryData, pipeline);
            }
        });
    }

    private void setProcessorLimits(final QueryData queryData, final DocRef pipeline) {
        clientPropertyCache.get(guard(result -> {
            if (result != null) {
                final long defaultProcessorTimeLimit = result.getProcess().getDefaultTimeLimit();
                final long defaultProcessorRecordLimit = result.getProcess().getDefaultRecordLimit();

                processorLimitsControllerProvider.get().show(this,
                        defaultProcessorRecordLimit,
                        defaultProcessorTimeLimit,
                        (recordLimit, timeLimitMins) -> {
                            final Limits limits = Limits.builder()
                                    .eventCount(recordLimit)
                                    .durationMs(timeLimitMins * 60 * 1000)
                                    .build();
                            createProcessFilter(queryData.copy().limits(limits).build(), pipeline);
                        });
            }
        }), taskMonitor());
    }

    private void createProcessFilter(final QueryData queryData,
                                     final DocRef pipeline) {
        // Now create the processor filter using the find stream criteria.
        final CreateProcessFilterRequest request = CreateProcessFilterRequest
                .builder()
                .pipeline(pipeline)
                .queryData(queryData)
                .priority(1)
                .build();
        restFactory
                .create(PROCESSOR_FILTER_RESOURCE)
                .method(res -> res.create(request))
                .onSuccess(streamProcessorFilter -> {
                    if (streamProcessorFilter != null) {
                        CreateProcessorEvent.fire(QueryPaneController.this, streamProcessorFilter);
                    } else {
                        AlertEvent.fireInfo(this, "Created batch processor", null);
                    }
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void showErrors() {
        if (!currentErrors.isEmpty()) {
            if (currentErrors.containsAny(Severity.FATAL_ERROR, Severity.ERROR)) {
                fireAlertEvent(AlertEvent::fireError);
            } else if (currentErrors.containsAny(Severity.WARNING)) {
                fireAlertEvent(AlertEvent::fireWarn);
            } else if (currentErrors.containsAny(Severity.INFO)) {
                fireAlertEvent(AlertEvent::fireInfo);
            }
        }
    }

    private void fireAlertEvent(final FireAlertEventFunction fireAlertEventFunction) {
        final List<ErrorMessage> errorMessages = currentErrors.getErrorMessagesOrderedBySeverity();
        final String msg = getAlertMessage(errorMessages.size());
        final List<String> messages = errorMessages.stream()
                .map(this::toDisplayMessage)
                .collect(Collectors.toList());

        fireAlertEventFunction.apply(this, msg, String.join("\n", messages), null);
    }

    private String toDisplayMessage(final ErrorMessage errorMessage) {
        if (errorMessage.getNode() == null) {
            return ERROR_MESSAGE_TEMPLATES.errorMessage(errorMessage.getSeverity().getDisplayValue(),
                    errorMessage.getMessage());
        }
        return ERROR_MESSAGE_TEMPLATES.errorMessageWithNode(errorMessage.getSeverity().getDisplayValue(),
                errorMessage.getMessage(), errorMessage.getNode());
    }

    private String getAlertMessage(final int numberOfMessages) {
        return numberOfMessages == 1
                ? ERROR_MESSAGE_TEMPLATES.errorMessageCreatedSingular()
                :
                        ERROR_MESSAGE_TEMPLATES.errorMessagesCreatedPlural();
    }

    @Override
    public void setQueryInfo(final QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }

    @Override
    public void setQueryOnOpen(final boolean queryOnOpen) {
        this.queryOnOpen = queryOnOpen;
    }

    // The run button: stops the query while it runs, else runs it
    private void onRunPressed() {
        if (searchModel.isSearching()) {
            stop();
        } else {
            promptAndStart();
        }
    }

    private void promptAndStart() {
        queryInfo.prompt(this::start, taskMonitor());
    }

    @Override
    public void start() {
        run(true, true);
    }

    @Override
    public void stop() {
        cancelRefresh();
        searchModel.stop();
    }

    @Override
    public boolean getSearchState() {
        return searchModel.isSearching();
    }

    @Override
    public void run(final boolean incremental,
                    final boolean storeHistory) {
        run(incremental, storeHistory, null);
    }

    private void run(final boolean incremental,
                     final boolean storeHistory,
                     final ExpressionOperator expressionDecorator) {
        final DocRef dataSourceRef = getQuerySettings().getDataSource();

        if (dataSourceRef == null) {
            warnNoDataSource();
        } else {
            currentErrors = null;
            expressionTree.clearSelection();

            view.setMessages(false, null);

            // Write expression.
            final ExpressionOperator root = expressionTree.write();
            ExpressionOperator decorated = ExpressionUtil.combine(root, expressionDecorator);

            final DashboardContext dashboardContext = getDashboardContext();
            decorated = dashboardContext.replaceExpression(decorated, true);

            // Start search.
            searchModel.startNewSearch(
                    decorated,
                    dashboardContext.getParams(),
                    dashboardContext.getResolvedTimeRange(),
                    incremental,
                    storeHistory,
                    queryInfo.getMessage(),
                    null,
                    null);
        }
    }

    private void resume(final String node,
                        final QueryKey queryKey) {
        final DocRef dataSourceRef = getQuerySettings().getDataSource();

        if (dataSourceRef == null) {
            warnNoDataSource();
        } else {
            currentErrors = null;
            expressionTree.clearSelection();

            view.setMessages(false, null);

            // Write expression.
            ExpressionOperator root = expressionTree.write();
            final DashboardContext dashboardContext = getDashboardContext();
            root = dashboardContext.replaceExpression(root, true);

            // Start search.
            searchModel.startNewSearch(
                    root,
                    dashboardContext.getParams(),
                    dashboardContext.getResolvedTimeRange(),
                    true,
                    false,
                    queryInfo.getMessage(),
                    node,
                    queryKey);
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

        // Fix legacy selection filters.
        setSettings(getQuerySettings()
                .copy()
                .selectionQuery(SelectionHandlerExpressionBuilder
                        .fixLegacySelectionHandlers(getQuerySettings().getSelectionQuery()))
                .build());

        // Set the dashboard UUID for the search model to be able to store query history for this dashboard.
        final DocRef dashboardDocRef = getDashboardContext().getDashboardDocRef();
        searchModel.init(dashboardDocRef, componentConfig.getId());

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
        setSettings(getQuerySettings().copy().expression(expressionTree.write()).build());
        return super.write();
    }

    private QueryComponentSettings getQuerySettings() {
        return (QueryComponentSettings) getSettings();
    }

    @Override
    public void onClose() {
        super.onClose();
        searchModel.reset(DestroyReason.TAB_CLOSE);
        initialised = false;
    }

    @Override
    public void onRemove() {
        super.onRemove();
        searchModel.reset(DestroyReason.NO_LONGER_NEEDED);
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
    public ComponentType getComponentType() {
        return TYPE;
    }

    public SearchModel getSearchModel() {
        return searchModel;
    }

    @Override
    public boolean isSearching() {
        return searchModel.isSearching();
    }

    @Override
    public boolean isInitialised() {
        return initialised;
    }

    @Override
    public Automate getAutomate() {
        return getQuerySettings().getAutomate();
    }

    public void setExpression(final ExpressionOperator root) {
        expressionTree.read(root);
    }

    @Override
    public void onSearching(final boolean searching) {
        view.setSearching(searching);

        // If this is the end of a query then schedule a refresh.
        if (!searching) {
            scheduleRefresh();
        }
    }

    private List<Item> addExpressionActionsToMenu() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        final boolean hasSelection = selectedItem != null;

        final List<Item> menuItems = new ArrayList<>();
        menuItems.add(new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.ADD)
                .text("Add Term")
                .command(this::addTerm)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.OPERATOR)
                .text("Add Operator")
                .command(this::addOperator)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(3)
                .icon(SvgImage.COPY)
                .text("Duplicate")
                .command(this::copy)
                .enabled(hasSelection)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(4)
                .icon(SvgImage.DISABLE)
                .text(getEnableDisableText())
                .enabled(hasSelection)
                .command(this::disable)
                .build());
        menuItems.add(new IconMenuItem.Builder()
                .priority(5)
                .icon(SvgImage.DELETE)
                .text("Delete")
                .enabled(hasSelection)
                .command(this::delete)
                .build());
        menuItems.addAll(expressionTree.getMoveMenuItems(6));

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
        if (expressionTree.getSelectionModel() != null) {
            return expressionTree.getSelectionModel().getSelectedObject();
        }
        return null;
    }

    private void showMenu(final List<Item> menuItems,
                          final PopupPosition popupPosition,
                          final TextEditTarget textField) {
        final ShowMenuEvent.Builder builder = ShowMenuEvent
                .builder()
                .name(getMenuName())
                .items(menuItems)
                .popupPosition(popupPosition);
        if (textField != null) {
            // Opened on a term's field: the focus goes back to it
            builder.onHide(e -> textField.restore());
        }
        builder.fire(this);
    }

    // The context menu's accessible name, saying which term or operator it acts on
    private String getMenuName() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        final String text = selectedItem != null
                ? selectedItem.toString().trim()
                : "";
        return text.isEmpty()
                ? "Expression Actions"
                : "Actions for expression item '" + text + "'";
    }

    private void downloadQuery() {
        if (getQuerySettings().getDataSource() != null) {

            final DashboardContext dashboardContext = getDashboardContext();
            final DashboardSearchRequest searchRequest = searchModel.createDownloadQueryRequest(
                    expressionTree.write(),
                    dashboardContext.getParams(),
                    dashboardContext.getResolvedTimeRange());

            restFactory
                    .create(DASHBOARD_RESOURCE)
                    .method(res -> res.downloadQuery(searchRequest))
                    .onSuccess(result ->
                            ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
