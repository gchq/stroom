/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.event.WindowCloseEvent;
import stroom.datasource.shared.DataSourceResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.Documentation;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.editor.client.view.Marker;
import stroom.entity.client.presenter.HasToolbar;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QLVisResult;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpDataSupplier;
import stroom.query.client.view.QueryResultTabsView;
import stroom.query.shared.QueryHelpItemsRequest;
import stroom.query.shared.QueryHelpItemsRequest.HelpItemType;
import stroom.query.shared.QueryHelpItemsResult;
import stroom.query.shared.QueryResource;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.view.client.presenter.DataSourceFieldsMap;
import stroom.view.client.presenter.IndexLoader;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Provider;

public class QueryEditPresenter
        extends MyPresenterWidget<QueryEditView>
        implements HasDirtyHandlers, HasToolbar, HasHandlers {

    private static final Set<HelpItemType> SUPPORTED_HELP_TYPES = EnumSet.of(
            HelpItemType.DATA_SOURCE,
            HelpItemType.STRUCTURE,
            HelpItemType.FIELD,
            HelpItemType.FUNCTION);

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);
    private static final DataSourceResource DATA_SOURCE_RESOURCE = GWT.create(DataSourceResource.class);
    private static final TabData TABLE = new TabDataImpl("Table");
    private static final TabData VISUALISATION = new TabDataImpl("Visualisation");

    private final QueryHelpPresenter queryHelpPresenter;
    private final QueryToolbarPresenter queryToolbarPresenter;
    private final EditorPresenter editorPresenter;
    private final QueryResultPresenter queryResultPresenter;
    private final IndexLoader indexLoader;
    private final Views views;
    private boolean dirty;
    private boolean reading;
    private boolean readOnly = true;
    private final QueryModel queryModel;
    private final RestFactory restFactory;
    private final QueryResultTabsView linkTabsLayoutView;

    private final Provider<QueryResultVisPresenter> visPresenterProvider;

    private QueryResultVisPresenter currentVisPresenter;

    @Inject
    public QueryEditPresenter(final EventBus eventBus,
                              final QueryEditView view,
                              final QueryHelpPresenter queryHelpPresenter,
                              final QueryToolbarPresenter queryToolbarPresenter,
                              final EditorPresenter editorPresenter,
                              final QueryResultPresenter queryResultPresenter,
                              final Provider<QueryResultVisPresenter> visPresenterProvider,
                              final RestFactory restFactory,
                              final IndexLoader indexLoader,
                              final Views views,
                              final DateTimeSettingsFactory dateTimeSettingsFactory,
                              final ResultStoreModel resultStoreModel,
                              final QueryResultTabsView linkTabsLayoutView) {
        super(eventBus, view);
        this.queryHelpPresenter = queryHelpPresenter;
        this.queryToolbarPresenter = queryToolbarPresenter;
        this.queryResultPresenter = queryResultPresenter;
        this.visPresenterProvider = visPresenterProvider;
        this.indexLoader = indexLoader;
        this.views = views;
        this.restFactory = restFactory;
        this.linkTabsLayoutView = linkTabsLayoutView;


        final ResultConsumer resultConsumer = new ResultConsumer() {
            boolean start;
            boolean hasData;

            @Override
            public OffsetRange getRequestedRange() {
                return null;
            }

            @Override
            public Set<String> getOpenGroups() {
                return null;
            }

            @Override
            public void reset() {
                hasData = false;
            }

            @Override
            public void startSearch() {
                hasData = false;
            }

            @Override
            public void endSearch() {
                if (currentVisPresenter != null) {
                    currentVisPresenter.endSearch();
                }
                start = false;
                if (!hasData) {
                    destroyCurrentVis();
                    setVisHidden(true);
                }
            }

            @Override
            public void setData(final Result componentResult) {
                if (componentResult != null) {
                    if (!start) {
                        createNewVis();
                        currentVisPresenter.startSearch();
                        currentVisPresenter.reset();
                        start = true;
                    }

                    final QLVisResult visResult = (QLVisResult) componentResult;
                    if (visResult.getJsonData() != null && visResult.getJsonData().length() > 0) {
                        hasData = true;
                        setVisHidden(false);
                    }

                    currentVisPresenter.setData(componentResult);
                } else {
                    if (start) {
                        currentVisPresenter.clear();
                        currentVisPresenter.endSearch();
                        start = false;
                        hasData = false;
                        destroyCurrentVis();
                        setVisHidden(true);
                    }
                }
            }
        };


        queryModel = new QueryModel(
                restFactory,
                dateTimeSettingsFactory,
                resultStoreModel,
                queryResultPresenter,
                resultConsumer);
        queryResultPresenter.setQueryModel(queryModel);
        queryModel.addSearchErrorListener(queryToolbarPresenter);
        queryModel.addTokenErrorListener(e -> {
            final Indicators indicators = new Indicators();
            final DefaultLocation from = e.getFrom();
            final DefaultLocation to = e.getTo();
            indicators.add(new StoredError(Severity.ERROR, from, null, e.getText()));
            final IndicatorLines indicatorLines = new IndicatorLines(indicators);
            final AceRange range = AceRange.create(
                    from.getLineNo() - 1,
                    from.getColNo(),
                    to.getLineNo() - 1,
                    to.getColNo());
            final Marker marker = new Marker(range, "err", AceMarkerType.TEXT, true);
            editorPresenter.setIndicators(indicatorLines);
            editorPresenter.setMarkers(Collections.singletonList(marker));
        });
        queryModel.addSearchStateListener(queryToolbarPresenter);

        this.editorPresenter = editorPresenter;
        this.editorPresenter.setMode(AceEditorMode.STROOM_QUERY);

        // This glues the editor code completion to the QueryHelpPresenter's completion provider
        // Need to do this via addAttachHandler so the editor is fully loaded
        // else it moans about the id not being a thing on the AceEditor
        this.editorPresenter.getWidget().addAttachHandler(event ->
                this.editorPresenter.registerCompletionProviders(queryHelpPresenter.getKeyedAceCompletionProvider()));

        view.setQueryHelp(queryHelpPresenter.getView());
        view.setQueryEditor(this.editorPresenter.getView());
        view.setResultView(linkTabsLayoutView);

        linkTabsLayoutView.getTabBar().addTab(TABLE);
        linkTabsLayoutView.getTabBar().addTab(VISUALISATION);
        setVisHidden(true);
    }

    private void setVisHidden(final boolean state) {
        final boolean currentState = linkTabsLayoutView.getTabBar().isTabHidden(VISUALISATION);
        if (currentState != state) {
            linkTabsLayoutView.getTabBar().setTabHidden(VISUALISATION, state);
            if (state) {
                selectTab(TABLE);
            } else {
                selectTab(VISUALISATION);
            }
        }
    }

    private void createNewVis() {
        destroyCurrentVis();
        currentVisPresenter = visPresenterProvider.get();
        if (VISUALISATION.equals(linkTabsLayoutView.getTabBar().getSelectedTab())) {
            linkTabsLayoutView.getLayerContainer().show(currentVisPresenter);
        }
    }

    private void destroyCurrentVis() {
        if (currentVisPresenter != null) {
            currentVisPresenter.onRemove();
            currentVisPresenter = null;
        }
    }

    @Override
    public List<Widget> getToolbars() {
        return Collections.singletonList(queryToolbarPresenter.getWidget());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(editorPresenter.addValueChangeHandler(event -> {
            queryHelpPresenter.updateQuery(editorPresenter.getText(), indexLoader::loadDataSource);
            setDirty(true);
        }));
        registerHandler(editorPresenter.getView().asWidget().addDomHandler(e -> {
            if (KeyCodes.KEY_ENTER == e.getNativeKeyCode() &&
                    (e.isShiftKeyDown() || e.isControlKeyDown())) {
                e.preventDefault();
                run(true, true);
            } else if (KeyCodes.KEY_ESCAPE == e.getNativeKeyCode() &&
                    (e.isShiftKeyDown() || e.isControlKeyDown())) {
                e.preventDefault();
                stop();
            }
        }, KeyDownEvent.getType()));
        registerHandler(editorPresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(queryToolbarPresenter.addStartQueryHandler(e -> startStop()));
        registerHandler(queryToolbarPresenter.addTimeRangeChangeHandler(e -> run(true, true)));
        queryHelpPresenter.linkToEditor(editorPresenter);

        registerHandler(getEventBus().addHandler(WindowCloseEvent.getType(), event -> {
            // If a user is even attempting to close the browser or browser tab then destroy the query.
            queryModel.reset(DestroyReason.WINDOW_CLOSE);
        }));
        registerHandler(linkTabsLayoutView.getTabBar().addSelectionHandler(e ->
                selectTab(e.getSelectedItem())));

        setupQueryHelpDataSupplier();
    }

    private void selectTab(final TabData tabData) {
        if (TABLE.equals(tabData)) {
            linkTabsLayoutView.getTabBar().selectTab(tabData);
            linkTabsLayoutView.getLayerContainer().show(queryResultPresenter);
        } else if (VISUALISATION.equals(tabData)) {
            linkTabsLayoutView.getTabBar().selectTab(tabData);
            linkTabsLayoutView.getLayerContainer().show(currentVisPresenter);
        }
    }

    private void setupQueryHelpDataSupplier() {
        queryHelpPresenter.setQueryHelpDataSupplier(new QueryHelpDataSupplier() {

            @Override
            public String decorateFieldName(final String fieldName) {
                return GwtNullSafe.get(fieldName, str ->
                        str.contains(" ")
                                ? "\"" + str + "\""
                                : str);
            }

            @Override
            public void registerChangeHandler(final Consumer<DataSourceFieldsMap> onChange) {
                registerHandler(indexLoader.addChangeDataHandler(e ->
                        onChange.accept(indexLoader.getDataSourceFieldsMap())));
            }

            @Override
            public boolean isSupported(final HelpItemType helpItemType) {
                return helpItemType != null && SUPPORTED_HELP_TYPES.contains(helpItemType);
            }

            @Override
            public void fetchQueryHelpItems(final String filterInput,
                                            final Consumer<QueryHelpItemsResult> resultConsumer) {
                final QueryHelpItemsRequest queryHelpItemsRequest = QueryHelpItemsRequest.fromQuery(
                        getQuery(),
                        filterInput,
                        SUPPORTED_HELP_TYPES);

                final Rest<QueryHelpItemsResult> rest = restFactory.create();
                rest
                        .onSuccess(result ->
                                GwtNullSafe.consume(result, resultConsumer))
                        .onFailure(throwable -> AlertEvent.fireError(
                                QueryEditPresenter.this,
                                throwable.getMessage(),
                                null))
                        .call(QUERY_RESOURCE)
                        .fetchQueryHelpItems(queryHelpItemsRequest);
            }

            @Override
            public void fetchDataSourceDescription(final DocRef dataSourceDocRef,
                                                   final Consumer<String> descriptionConsumer) {

                if (dataSourceDocRef != null) {
                    final Rest<Documentation> rest = restFactory.create();
                    rest
                            .onSuccess(documentation -> {
                                GWT.log("Description:\n" + documentation);
                                GwtNullSafe.consume(
                                        documentation,
                                        Documentation::getMarkdown,
                                        descriptionConsumer);
                            })
                            .onFailure(throwable -> AlertEvent.fireError(
                                    QueryEditPresenter.this,
                                    throwable.getMessage(),
                                    null))
                            .call(DATA_SOURCE_RESOURCE)
                            .fetchDocumentation(dataSourceDocRef);
                }
            }
        });
    }

    private void setDirty(final boolean dirty) {
        if (!reading && this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);
        }
    }

    public boolean isDirty() {
        return !readOnly && dirty;
    }

    public void onClose() {
        queryModel.reset(DestroyReason.TAB_CLOSE);
        destroyCurrentVis();
    }

    private void startStop() {
        if (queryModel.isSearching()) {
            queryModel.stop();
        } else {
            run(true, true);
        }
    }

    private void stop() {
        queryModel.stop();
    }

    private void run(final boolean incremental,
                     final boolean storeHistory) {
        run(incremental, storeHistory, Function.identity());
    }

    private void run(final boolean incremental,
                     final boolean storeHistory,
                     final Function<ExpressionOperator, ExpressionOperator> expressionDecorator) {
//        final DocRef dataSourceRef = getQuerySettings().getDataSource();
//
//        if (dataSourceRef == null) {
//            warnNoDataSource();
//        } else {
//            currentWarnings = null;
//            expressionPresenter.clearSelection();
//
//            warningsButton.setVisible(false);
//
//            // Write expression.
//            final ExpressionOperator root = expressionPresenter.write();
//            final ExpressionOperator decorated = expressionDecorator.apply(root);


        // Clear the table selection and any markers.
        queryResultPresenter.clear();
        editorPresenter.setMarkers(Collections.emptyList());
        editorPresenter.setIndicators(new IndicatorLines(new Indicators()));

        // Destroy any previous query.
        queryModel.reset(DestroyReason.NO_LONGER_NEEDED);

        // Start search.
        queryModel.startNewSearch(
                editorPresenter.getText(),
                null, //getDashboardContext().getCombinedParams(),
                queryToolbarPresenter.getTimeRange(),
                incremental,
                storeHistory,
                null);
//        }
    }

    public void setQuery(final DocRef docRef, final String query, final boolean readOnly) {
        this.readOnly = readOnly;

        queryModel.init(docRef.getUuid());
        if (query != null) {
            reading = true;
            if (editorPresenter.getText().length() == 0 || !editorPresenter.getText().equals(query)) {
                editorPresenter.setText(query);
                queryHelpPresenter.setQuery(query, indexLoader::loadDataSource);
            }
            reading = false;
        }
        queryToolbarPresenter.setEnabled(true);
        queryToolbarPresenter.onSearching(false);

        editorPresenter.setReadOnly(readOnly);
        editorPresenter.getFormatAction().setAvailable(!readOnly);

        dirty = false;
    }

    public String getQuery() {
        return editorPresenter.getText();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setSourceType(final SourceType sourceType) {
        this.queryModel.setSourceType(sourceType);
    }


    // --------------------------------------------------------------------------------


    public interface QueryEditView extends View {

        void setQueryHelp(View view);

        void setQueryEditor(View view);

        void setResultView(View view);
    }
}
