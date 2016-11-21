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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Command;
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
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.shared.DocRef;
import stroom.explorer.client.presenter.ExplorerDropDownTreePresenter;
import stroom.explorer.shared.ExplorerData;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.pipeline.client.event.ChangeDataEvent;
import stroom.pipeline.client.event.ChangeDataEvent.ChangeDataHandler;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.processor.shared.CreateProcessorAction;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionUiHandlers;
import stroom.query.shared.Automate;
import stroom.query.shared.ComponentSettings;
import stroom.query.shared.ExpressionItem;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFieldsMap;
import stroom.query.shared.Limits;
import stroom.query.shared.QueryData;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.button.client.ImageButtonView;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.ImageIcon;

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
    private final Provider<ExplorerDropDownTreePresenter> pipelineSelection;
    private final ProcessorLimitsPresenter processorLimitsPresenter;
    private final Resources resources;
    private final MenuListPresenter menuListPresenter;
    private final ClientDispatchAsync dispatcher;

    private final IndexLoader indexLoader;
    private final SearchModel searchModel;
    private final ImageButtonView addOperatorButton;
    private final GlyphButtonView addTermButton;
    private final GlyphButtonView disableItemButton;
    private final GlyphButtonView deleteItemButton;
    private final ImageButtonView historyButton;
    private final ImageButtonView favouriteButton;
    private final ImageButtonView warningsButton;

    private String params;
    private QueryData queryData;
    private String currentWarnings;
    private ImageButtonView processButton;
    private long defaultProcessorTimeLimit = DEFAULT_TIME_LIMIT;
    private long defaultProcessorRecordLimit = DEFAULT_RECORD_LIMIT;
    private boolean initialised;
    private Timer autoRefreshTimer;

    @Inject
    public QueryPresenter(final EventBus eventBus, final QueryView view, final SearchBus searchBus,
                          final Provider<QuerySettingsPresenter> settingsPresenterProvider,
                          final ExpressionTreePresenter expressionPresenter, final QueryHistoryPresenter historyPresenter,
                          final QueryFavouritesPresenter favouritesPresenter,
                          final Provider<ExplorerDropDownTreePresenter> pipelineSelection,
                          final ProcessorLimitsPresenter processorLimitsPresenter, final Resources resources,
                          final MenuListPresenter menuListPresenter, final ClientDispatchAsync dispatcher,
                          final ClientSecurityContext securityContext, final ClientPropertyCache clientPropertyCache,
                          final TimeZones timeZones) {
        super(eventBus, view, settingsPresenterProvider);
        this.expressionPresenter = expressionPresenter;
        this.historyPresenter = historyPresenter;
        this.favouritesPresenter = favouritesPresenter;
        this.pipelineSelection = pipelineSelection;
        this.processorLimitsPresenter = processorLimitsPresenter;
        this.menuListPresenter = menuListPresenter;
        this.resources = resources;
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

        addTermButton = view.addButton(GlyphIcons.ADD);
        addTermButton.setTitle("Add Term");
        addOperatorButton = view.addButton("Add Operator", resources.addOperator(), resources.addOperator(), true);
        disableItemButton = view.addButton(GlyphIcons.DISABLE);
        deleteItemButton = view.addButton(GlyphIcons.DELETE);
        historyButton = view.addButton("History", resources.history(), null, true);
        favouriteButton = view.addButton("Favourites", resources.favourite(), null, true);

        if (securityContext.hasAppPermission(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)) {
            processButton = view.addButton("Process", resources.pipeline(), resources.pipelineDisabled(), true);
        }

        warningsButton = view.addButton("Show Warnings", resources.warning(), null, true);
        warningsButton.setVisible(false);

        indexLoader = new IndexLoader(getEventBus(), dispatcher);
        searchModel = new SearchModel(searchBus, this, indexLoader, timeZones);

        clientPropertyCache.get(new AsyncCallbackAdaptor<ClientProperties>() {
            @Override
            public void onSuccess(final ClientProperties result) {
                defaultProcessorTimeLimit = result.getLong(ClientProperties.PROCESS_TIME_LIMIT, DEFAULT_TIME_LIMIT);
                defaultProcessorRecordLimit = result.getLong(ClientProperties.PROCESS_RECORD_LIMIT,
                        DEFAULT_RECORD_LIMIT);
            }

            @Override
            public void onFailure(final Throwable caught) {
                AlertEvent.fireError(QueryPresenter.this, caught.getMessage(), null);
            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(expressionPresenter.addDataSelectionHandler(new DataSelectionHandler<ExpressionItem>() {
            @Override
            public void onSelection(final DataSelectionEvent<ExpressionItem> event) {
                setButtonsEnabled();
            }
        }));
        registerHandler(expressionPresenter.addContextMenuHandler(new ContextMenuEvent.Handler() {
            @Override
            public void onContextMenu(final ContextMenuEvent event) {
                final List<Item> menuItems = addExpressionActionsToMenu();
                if (menuItems != null && menuItems.size() > 0) {
                    final PopupPosition popupPosition = new PopupPosition(event.getX(), event.getY());
                    showMenu(popupPosition, menuItems);
                }
            }
        }));
        registerHandler(addOperatorButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    addOperator();
                }
            }
        }));
        registerHandler(addTermButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    addTerm();
                }
            }
        }));
        registerHandler(disableItemButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    disable();
                }
            }
        }));
        registerHandler(deleteItemButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    delete();
                }
            }
        }));
        registerHandler(historyButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    historyPresenter.show(QueryPresenter.this, getComponents().getDashboard().getId());
                }
            }
        }));
        registerHandler(favouriteButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    final ExpressionOperator root = new ExpressionOperator();
                    expressionPresenter.write(root);
                    favouritesPresenter.show(QueryPresenter.this, getComponents().getDashboard().getId(),
                            getSettings().getDataSource(), root);
                }
            }
        }));
        if (processButton != null) {
            registerHandler(processButton.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(final ClickEvent event) {
                    if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                        choosePipeline();
                    }
                }
            }));
        }
        registerHandler(warningsButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    showWarnings();
                }
            }
        }));
        registerHandler(indexLoader.addChangeDataHandler(new ChangeDataHandler<IndexLoader>() {
            @Override
            public void onChange(final ChangeDataEvent<IndexLoader> event) {
                loadedDataSource(indexLoader.getLoadedDataSourceRef(), indexLoader.getIndexFieldsMap());
            }
        }));
    }

    public void setErrors(final String errors) {
        currentWarnings = errors;
        warningsButton.setVisible(currentWarnings != null && currentWarnings.length() > 0);
    }

    private void setButtonsEnabled() {
        final ExpressionItem selectedItem = getSelectedItem();

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
        expressionPresenter.setFields(indexedFields);

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
        final ExpressionOperator root = new ExpressionOperator();
        expressionPresenter.write(root);

        final QueryData queryData = new QueryData();
        queryData.setDataSource(this.queryData.getDataSource());
        queryData.setExpression(root);

        final ExplorerDropDownTreePresenter chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Process Results With");
        chooser.setIncludedTypes(PipelineEntity.ENTITY_TYPE);
        chooser.setRequiredPermissions(DocumentPermissionNames.USE);
        chooser.addDataSelectionHandler(new DataSelectionHandler<ExplorerData>() {
            @Override
            public void onSelection(final DataSelectionEvent<ExplorerData> event) {
                final DocRef pipeline = chooser.getSelectedEntityReference();
                if (pipeline != null) {
                    setProcessorLimits(queryData, pipeline);
                }
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
        dispatcher.execute(new CreateProcessorAction(pipeline, findStreamCriteria, true, 1),
                new AsyncCallbackAdaptor<StreamProcessorFilter>() {
                    @Override
                    public void onSuccess(final StreamProcessorFilter streamProcessorFilter) {
                        CreateProcessorEvent.fire(QueryPresenter.this, streamProcessorFilter);
                    }
                });
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
        run(true);
    }

    @Override
    public void stop() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
            autoRefreshTimer = null;
        }
        searchModel.destroy();
    }

    private void run(final boolean incremental) {
        final DocRef dataSourceRef = queryData.getDataSource();

        if (dataSourceRef == null) {
            warnNoDataSource();
        } else {
            currentWarnings = null;
            expressionPresenter.clearSelection();

            warningsButton.setVisible(false);

            // Write expression.
            final ExpressionOperator root = new ExpressionOperator();
            expressionPresenter.write(root);

            searchModel.search(root, params, incremental);
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
            root = new ExpressionOperator();
        }
        setExpression(root);
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        // Write expression.
        ExpressionOperator root = queryData.getExpression();
        if (root == null) {
            root = new ExpressionOperator();
            queryData.setExpression(root);
        }
        expressionPresenter.write(root);

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
                run(true);
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
                            QueryPresenter.this.run(false);
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
        final ExpressionItem selectedItem = getSelectedItem();
        final boolean hasSelection = selectedItem != null;

        final List<Item> menuItems = new ArrayList<Item>();
        menuItems.add(new IconMenuItem(1, GlyphIcons.ADD, GlyphIcons.ADD, "Add Term", null, true, new Command() {
            @Override
            public void execute() {
                addTerm();
            }
        }));
        menuItems.add(new IconMenuItem(2, ImageIcon.create(resources.addOperator()), ImageIcon.create(resources.addOperator()), "Add Operator", null,
                true, new Command() {
            @Override
            public void execute() {
                addOperator();
            }
        }));
        menuItems.add(new IconMenuItem(3, GlyphIcons.DISABLE, GlyphIcons.DISABLE, getEnableDisableText(),
                null, hasSelection, new Command() {
            @Override
            public void execute() {
                disable();
            }
        }));
        menuItems.add(new IconMenuItem(4, GlyphIcons.DELETE, GlyphIcons.DELETE, "Delete", null,
                hasSelection, new Command() {
            @Override
            public void execute() {
                delete();
            }
        }));

        return menuItems;
    }

    private String getEnableDisableText() {
        final ExpressionItem selectedItem = getSelectedItem();
        if (selectedItem != null && !selectedItem.isEnabled()) {
            return "Enable";
        }
        return "Disable";
    }

    private ExpressionItem getSelectedItem() {
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
        ImageButtonView addButton(String title, ImageResource enabledImage, ImageResource disabledImage,
                                  boolean enabled);

        GlyphButtonView addButton(GlyphIcon preset);

        void setExpressionView(View view);

        void setMode(SearchModel.Mode mode);

        void setEnabled(boolean enabled);
    }

    public interface Resources extends ClientBundle {
        ImageResource addOperator();

        ImageResource search();

        ImageResource history();

        ImageResource favourite();

        ImageResource pipeline();

        ImageResource pipelineDisabled();

        ImageResource warning();
    }
}
