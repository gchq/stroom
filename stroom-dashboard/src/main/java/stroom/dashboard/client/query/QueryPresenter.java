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

package stroom.dashboard.client.query;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.IndexLoader;
import stroom.dashboard.client.main.SearchBus;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.main.UsesParams;
import stroom.dashboard.client.table.TimeZones;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.QueryKeyImpl;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.shared.DocRef;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.processor.shared.CreateProcessorAction;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionUiHandlers;
import stroom.query.shared.Automate;
import stroom.query.shared.ComponentSettings;
import stroom.query.shared.ExpressionBuilder;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFieldsMap;
import stroom.query.shared.Limits;
import stroom.query.shared.QueryData;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueryPresenter extends AbstractComponentPresenter<QueryPresenter.QueryView>
        implements QueryUiHandlers, HasDirtyHandlers, UsesParams {

    public static final ComponentType TYPE = new ComponentType(0, "query", "Query");
    public static final int TEN_SECONDS = 10000;

    private static final long DEFAULT_TIME_LIMIT = 30L;
    private static final long DEFAULT_RECORD_LIMIT = 1000000L;

    private final ExpressionTreePresenter expressionPresenter;
    private final QueryHistoryPresenter historyPresenter;
    private final QueryFavouritesPresenter favouritesPresenter;
    private final Provider<EntityChooser> pipelineSelection;
    private final Provider<SearchPurposePresenter> searchInfoPresenterProvider;
    private final ProcessorLimitsPresenter processorLimitsPresenter;
    private final MenuListPresenter menuListPresenter;
    private final ClientDispatchAsync dispatcher;

    private final IndexLoader indexLoader;
    private final SearchModel searchModel;
    private final ButtonView addOperatorButton;
    private final ButtonView addTermButton;
    private final ButtonView disableItemButton;
    private final ButtonView deleteItemButton;
    private final ButtonView historyButton;
    private final ButtonView favouriteButton;
    private final ButtonView warningsButton;

    private String params;
    private QueryData queryData;
    private String currentWarnings;
    private ButtonView processButton;
    private long defaultProcessorTimeLimit = DEFAULT_TIME_LIMIT;
    private long defaultProcessorRecordLimit = DEFAULT_RECORD_LIMIT;
    private boolean initialised;
    private Timer autoRefreshTimer;
    private boolean searchPurposeRequired;
    private String lastUsedSearchPurpose;

    @Inject
    public QueryPresenter(final EventBus eventBus,
                          final QueryView view,
                          final SearchBus searchBus,
                          final Provider<QuerySettingsPresenter> settingsPresenterProvider,
                          final ExpressionTreePresenter expressionPresenter, final QueryHistoryPresenter historyPresenter,
                          final QueryFavouritesPresenter favouritesPresenter,
                          final Provider<EntityChooser> pipelineSelection,
                          final Provider<SearchPurposePresenter> searchInfoPresenterProvider,
                          final ProcessorLimitsPresenter processorLimitsPresenter,
                          final MenuListPresenter menuListPresenter, final ClientDispatchAsync dispatcher,
                          final ClientSecurityContext securityContext, final ClientPropertyCache clientPropertyCache,
                          final TimeZones timeZones) {
        super(eventBus, view, settingsPresenterProvider);
        this.expressionPresenter = expressionPresenter;
        this.historyPresenter = historyPresenter;
        this.favouritesPresenter = favouritesPresenter;
        this.pipelineSelection = pipelineSelection;
        this.searchInfoPresenterProvider = searchInfoPresenterProvider;
        this.processorLimitsPresenter = processorLimitsPresenter;
        this.menuListPresenter = menuListPresenter;
        this.dispatcher = dispatcher;

        view.setExpressionView(expressionPresenter.getView());
        view.setUiHandlers(this);

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

        if (securityContext.hasAppPermission(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)) {
            processButton = view.addButton(SvgPresets.PROCESS.enabled(true));
        }

        warningsButton = view.addButton(SvgPresets.ALERT.title("Show Warnings"));
        warningsButton.setVisible(false);

        indexLoader = new IndexLoader(getEventBus(), dispatcher);
        searchModel = new SearchModel(searchBus, this, indexLoader, timeZones);

        clientPropertyCache.get()
                .onSuccess(result -> {
                    defaultProcessorTimeLimit = result.getLong(ClientProperties.PROCESS_TIME_LIMIT, DEFAULT_TIME_LIMIT);
                    defaultProcessorRecordLimit = result.getLong(ClientProperties.PROCESS_RECORD_LIMIT,
                            DEFAULT_RECORD_LIMIT);
                    searchPurposeRequired = result.getBoolean(ClientProperties.SEARCH_PURPOSE_REQUIRED, false); // default to false?
                })
                .onFailure(caught -> AlertEvent.fireError(QueryPresenter.this, caught.getMessage(), null));
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(expressionPresenter.addDataSelectionHandler(event -> setButtonsEnabled()));
        registerHandler(expressionPresenter.addContextMenuHandler(event -> {
            final List<Item> menuItems = addExpressionActionsToMenu();
            if (menuItems != null && menuItems.size() > 0) {
                final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                showMenu(popupPosition, menuItems);
            }
        }));
        registerHandler(addOperatorButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                addOperator();
            }
        }));
        registerHandler(addTermButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                addTerm();
            }
        }));
        registerHandler(disableItemButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                disable();
            }
        }));
        registerHandler(deleteItemButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                delete();
            }
        }));
        registerHandler(historyButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                historyPresenter.show(QueryPresenter.this, getComponents().getDashboard().getId());
            }
        }));
        registerHandler(favouriteButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                final ExpressionOperator root = expressionPresenter.write();
                favouritesPresenter.show(QueryPresenter.this, getComponents().getDashboard().getId(),
                        getSettings().getDataSource(), root);
            }
        }));
        if (processButton != null) {
            registerHandler(processButton.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    choosePipeline();
                }
            }));
        }
        registerHandler(warningsButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                showWarnings();
            }
        }));
        registerHandler(indexLoader.addChangeDataHandler(event -> loadedDataSource(indexLoader.getLoadedDataSourceRef(), indexLoader.getIndexFieldsMap())));
    }

    public void setErrors(final String errors) {
        currentWarnings = errors;
        warningsButton.setVisible(currentWarnings != null && currentWarnings.length() > 0);
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
    }

    private void loadDataSource(final DocRef dataSourceRef) {
        searchModel.getIndexLoader().loadDataSource(dataSourceRef);
    }

    private void loadedDataSource(final DocRef dataSourceRef, final IndexFieldsMap indexFieldsMap) {
        // Create a list of index fields.
        final List<IndexField> indexedFields = new ArrayList<>();
        if (indexFieldsMap != null) {
            for (final IndexField indexField : indexFieldsMap.values()) {
                if (indexField.isIndexed()) {
                    indexedFields.add(indexField);
                }
            }
        }
        Collections.sort(indexedFields);
        expressionPresenter.init(dispatcher, dataSourceRef, indexedFields);

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(queryData.getDataSource(), dataSourceRef);

        if (!builder.isEquals()) {
            queryData.setDataSource(dataSourceRef);
            setDirty(true);
        }

        // Only allow searching if we have a data source and have loaded fields from it successfully.
        getView().setEnabled(dataSourceRef != null && indexedFields.size() > 0);

        init();
    }

    private void addOperator() {
        expressionPresenter.addOperator();
    }

    private void addTerm() {
        final DocRef dataSourceRef = queryData.getDataSource();

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
        queryData.setDataSource(this.queryData.getDataSource());
        queryData.setExpression(root);

        final EntityChooser chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Process Results With");
        chooser.setIncludedTypes(PipelineEntity.ENTITY_TYPE);
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
        final PopupSize popupSize = new PopupSize(321, 102, false);
        ShowPopupEvent.fire(this, processorLimitsPresenter, PopupType.OK_CANCEL_DIALOG, popupSize,
                "Process Search Results", new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
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
                        HidePopupEvent.fire(QueryPresenter.this, processorLimitsPresenter);
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                    }
                });
    }

    private void openEditor(final QueryData queryData, final DocRef pipeline) {
        // Now create the processor filter using the find stream criteria.
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.setQueryData(queryData);
        dispatcher.exec(new CreateProcessorAction(pipeline, findStreamCriteria, true, 1)).onSuccess(streamProcessorFilter -> CreateProcessorEvent.fire(QueryPresenter.this, streamProcessorFilter));
    }

    private void showWarnings() {
        if (currentWarnings != null && currentWarnings.length() > 0) {
            AlertEvent.fireWarn(this, "The following warnings have been created while running this search:",
                    currentWarnings, null);
        }
    }

    @Override
    public void onParamsChanged(final String params) {
        this.params = params;
        if (initialised) {
            stop();
            start();
        }
    }

    @Override
    public void start() {
        run(true, true);
    }

    @Override
    public void stop() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
            autoRefreshTimer = null;
        }
        searchModel.destroy();
    }

    private void run(final boolean incremental,
                     final boolean storeHistory) {
        final DocRef dataSourceRef = queryData.getDataSource();

        if (dataSourceRef == null) {
            warnNoDataSource();
        } else {
            currentWarnings = null;
            expressionPresenter.clearSelection();

            warningsButton.setVisible(false);

            // Write expression.
            final ExpressionOperator root = expressionPresenter.write();

            if (searchPurposeRequired && SearchModel.Mode.INACTIVE.equals(searchModel.getMode())) {
                final SearchPurposePresenter searchPurposePresenter = searchInfoPresenterProvider.get();
                searchPurposePresenter.setSearchPurpose(lastUsedSearchPurpose);
                final PopupSize popupSize = new PopupSize(640, 480, true);
                ShowPopupEvent.fire(this,
                        searchPurposePresenter,
                        PopupType.OK_CANCEL_DIALOG,
                        popupSize,
                        "Please Provide a Justification for the Search",
                        new PopupUiHandlers() {
                            @Override
                            public void onHideRequest(final boolean autoClose, final boolean ok) {
                                if (ok) {
                                    lastUsedSearchPurpose = searchPurposePresenter.getSearchPurpose();
                                    searchModel.search(root, params, incremental, storeHistory, lastUsedSearchPurpose);
                                }
                                HidePopupEvent.fire(QueryPresenter.this, searchPurposePresenter);
                            }

                            @Override
                            public void onHide(final boolean autoClose, final boolean ok) {
                            }
                        });
            } else {
                searchModel.search(root, params, incremental, storeHistory, null);
            }

        }
    }

    @Override
    public void read(final ComponentConfig componentData) {
        super.read(componentData);
        queryData = getSettings();

        // Create and register the search model.
        final Dashboard dashboard = getComponents().getDashboard();
        final QueryKeyImpl initialQueryKey = new QueryKeyImpl(dashboard.getId(), dashboard.getName(),
                getComponentData().getId());
        searchModel.setInitialQueryKey(initialQueryKey);

        // Read data source.
        loadDataSource(queryData.getDataSource());

        // Read expression.
        ExpressionOperator root = queryData.getExpression();
        if (root == null) {
            root = new ExpressionBuilder(Op.AND).build();
        }
        setExpression(root);
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        // Write expression.
        queryData.setExpression(expressionPresenter.write());
        componentData.setSettings(queryData);
    }

    @Override
    public void onRemove() {
        super.onRemove();
        searchModel.destroy();
    }

    @Override
    public void link() {
    }

    private void init() {
        if (!initialised) {
            initialised = true;
            // An auto search can only commence if the UI has fully loaded and the data source has also loaded from the server.
            final Automate automate = getAutomate();
            if (automate.isOpen()) {
                run(true, false);
            }
        }
    }

    @Override
    public void changeSettings() {
        super.changeSettings();
        loadDataSource(queryData.getDataSource());
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public ComponentType getType() {
        return TYPE;
    }

    private QueryData getSettings() {
        ComponentSettings settings = getComponentData().getSettings();
        if (settings == null || !(settings instanceof QueryData)) {
            settings = createSettings();
            getComponentData().setSettings(settings);
        }

        return (QueryData) settings;
    }

    private Automate getAutomate() {
        final QueryData queryData = getSettings();
        Automate automate = queryData.getAutomate();
        if (automate == null) {
            automate = new Automate();
            queryData.setAutomate(automate);
        }

        return automate;
    }

    private ComponentSettings createSettings() {
        return new QueryData();
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

        final Automate automate = getAutomate();
        if (automate.isRefresh()) {
            try {
                final String interval = automate.getRefreshInterval();
                int millis = ModelStringUtil.parseDurationString(interval).intValue();

                // Ensure that the refresh interval is not less than 10 seconds.
                millis = Math.max(millis, TEN_SECONDS);

                autoRefreshTimer = new Timer() {
                    @Override
                    public void run() {
                        // Make sure search is currently inactive before we attempt to execute a new query.
                        if (SearchModel.Mode.INACTIVE.equals(searchModel.getMode())) {
                            QueryPresenter.this.run(false, false);
                        }
                    }
                };
                autoRefreshTimer.schedule(millis);
            } catch (final Exception e) {
                // Ignore as we cannot display this error now.
            }
        }
    }

    private List<Item> addExpressionActionsToMenu() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        final boolean hasSelection = selectedItem != null;

        final List<Item> menuItems = new ArrayList<Item>();
        menuItems.add(new IconMenuItem(1, SvgPresets.ADD, SvgPresets.ADD, "Add Term", null, true, () -> addTerm()));
        menuItems.add(new IconMenuItem(2, SvgPresets.OPERATOR, SvgPresets.OPERATOR, "Add Operator", null,
                true, () -> addOperator()));
        menuItems.add(new IconMenuItem(3, SvgPresets.DISABLE, SvgPresets.DISABLE, getEnableDisableText(),
                null, hasSelection, () -> disable()));
        menuItems.add(new IconMenuItem(4, SvgPresets.DELETE, SvgPresets.DELETE, "Delete", null,
                hasSelection, () -> delete()));

        return menuItems;
    }

    private String getEnableDisableText() {
        final stroom.query.client.Item selectedItem = getSelectedItem();
        if (selectedItem != null && !selectedItem.enabled()) {
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

    private void showMenu(final PopupPosition popupPosition, final List<Item> menuItems) {
        menuListPresenter.setData(menuItems);

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                HidePopupEvent.fire(QueryPresenter.this, menuListPresenter);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };
        ShowPopupEvent.fire(this, menuListPresenter, PopupType.POPUP, popupPosition, popupUiHandlers);
    }

    public interface QueryView extends View, HasUiHandlers<QueryUiHandlers> {
        ButtonView addButton(SvgPreset preset);

        void setExpressionView(View view);

        void setMode(SearchModel.Mode mode);

        void setEnabled(boolean enabled);
    }
}
