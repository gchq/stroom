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
 */

package stroom.query.client.presenter;

import stroom.core.client.event.WindowCloseEvent;
import stroom.dashboard.client.query.QueryInfo;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.editor.client.view.Marker;
import stroom.entity.client.presenter.HasToolbar;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.Param;
import stroom.query.api.QLVisResult;
import stroom.query.api.Result;
import stroom.query.api.TimeRange;
import stroom.query.client.component.QueryHelpController;
import stroom.query.client.component.QueryToolbarController;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.client.view.QueryResultTabsView;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class QueryEditPresenter
        extends MyPresenterWidget<QueryEditView>
        implements HasToolbar, HasHandlers, HasValueChangeHandlers<String> {

    private static final int DEBOUNCE_DELAY_MS = 400;
    private static final TabData TABLE = new TabDataImpl("Table");
    private static final TabData VISUALISATION = new TabDataImpl("Visualisation");

    private final QueryHelpController queryHelp;
    private final QueryToolbarController queryToolbar;
    private final EditorPresenter editorPresenter;
    private final QueryResultTableSplitPresenter queryResultPresenter;
    private final QueryModel queryModel;
    private final QueryResultTabsView linkTabsLayoutView;
    private final QueryInfo queryInfo;
    private QueryTablePreferences queryTablePreferences = QueryTablePreferences.builder().build();

    private final Provider<QueryResultVisPresenter> visPresenterProvider;

    private QueryResultVisPresenter currentVisPresenter;
    private String currentQuery;
    private Timer requestTimer;
    private Map<String, String> queryVariables;

    private Runnable onChange = () -> {
    };

    @Inject
    public QueryEditPresenter(final EventBus eventBus,
                              final QueryEditView view,
                              final QueryHelpController queryHelp,
                              final QueryToolbarController queryToolbar,
                              final EditorPresenter editorPresenter,
                              final QueryResultTableSplitPresenter queryResultPresenter,
                              final Provider<QueryResultVisPresenter> visPresenterProvider,
                              final RestFactory restFactory,
                              final DateTimeSettingsFactory dateTimeSettingsFactory,
                              final ResultStoreModel resultStoreModel,
                              final QueryResultTabsView linkTabsLayoutView,
                              final QueryInfo queryInfo) {
        super(eventBus, view);
        own(queryHelp);
        own(queryToolbar);
        own(editorPresenter);
        own(queryResultPresenter);
        this.queryHelp = queryHelp;
        this.queryToolbar = queryToolbar;
        this.queryResultPresenter = queryResultPresenter;
        this.visPresenterProvider = visPresenterProvider;
        this.linkTabsLayoutView = linkTabsLayoutView;
        this.queryInfo = queryInfo;

        queryResultPresenter.setQueryTablePreferencesSupplier(() -> queryTablePreferences);
        queryResultPresenter.setQueryTablePreferencesConsumer(qtp -> {
            if (qtp != null) {
                queryTablePreferences = qtp;
            }
        });

        final ResultComponent resultConsumer = new ResultComponent() {
            boolean start;
            boolean hasData;

            @Override
            public OffsetRange getRequestedRange() {
                return null;
            }

            @Override
            public GroupSelection getGroupSelection() {
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
                        start = true;
                    }

                    final QLVisResult visResult = (QLVisResult) componentResult;
                    if (!NullSafe.isBlankString(visResult.getJsonData())) {
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

            @Override
            public void setQueryModel(final QueryModel queryModel) {

            }
        };

        queryModel = new QueryModel(
                eventBus,
                restFactory,
                dateTimeSettingsFactory,
                resultStoreModel,
                () -> queryTablePreferences);
        // Shows its requests' progress as this presenter does (its context's)
        queryModel.setTaskMonitorFactory(this);
        queryModel.addResultComponent(QueryModel.TABLE_COMPONENT_ID, queryResultPresenter);
        queryModel.addResultComponent(QueryModel.VIS_COMPONENT_ID, resultConsumer);

        queryModel.addSearchErrorListener(queryToolbar);
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
            final Marker marker = new Marker(range, "code-editor__error-underline", AceMarkerType.TEXT, true);
            editorPresenter.setIndicators(indicatorLines);
            editorPresenter.setMarkers(Collections.singletonList(marker));
        });
        queryModel.addSearchStateListener(queryToolbar);

        this.editorPresenter = editorPresenter;
        this.editorPresenter.setMode(AceEditorMode.STROOM_QUERY);
        this.editorPresenter.getBasicAutoCompletionOption().setOff();

//        // This glues the editor code completion to the query help's completion provider
//        // Need to do this via addAttachHandler so the editor is fully loaded
//        // else it moans about the id not being a thing on the AceEditor
//        this.editorPresenter.getWidget().addAttachHandler(event ->
//                this.editorPresenter.registerCompletionProviders(queryHelp.getKeyedAceCompletionProvider()));

        view.setQueryHelp(queryHelp);
        view.setQueryEditor(this.editorPresenter.getView());
        view.setResultView(linkTabsLayoutView);

        linkTabsLayoutView.getTabBar().addTab(TABLE);
        linkTabsLayoutView.getTabBar().addTab(VISUALISATION);
        setVisHidden(true);

        queryToolbar.setEnabled(true);
        queryToolbar.onSearching(false);

        //noinspection unused event
        own(editorPresenter.addValueChangeHandler(event -> {
            final String query = editorPresenter.getText();
            updateQuery(query);
            onChange();
        }));
        //noinspection unused event
        own(editorPresenter.addFormatHandler(event -> onChange()));
        queryToolbar.setOnStart(this::toggleStart);
        queryToolbar.setOnTimeRangeChange(() -> {
            run();
            onChange();
        });
        queryHelp.linkToEditor(editorPresenter.getController());

        //noinspection unused event
        listen(WindowCloseEvent.getType(), event -> {
            // If a user is even attempting to close the browser or browser tab then destroy the query.
            queryModel.reset(DestroyReason.WINDOW_CLOSE);
        });
        own(linkTabsLayoutView.getTabBar().addSelectionHandler(e ->
                selectTab(e.getSelectedItem())));
        queryResultPresenter.setOnChange(this::onChange);
    }

    private void focus() {
        // Only if the focus hasn't moved since, other than within the query (e.g. as the screen is
        // opened), so this doesn't take it from a menu or dialog the user has opened in the meantime
        final Element focusBefore = getActiveElement();
        Scheduler.get().scheduleFixedDelay(() -> {
            if (isFocusUnmoved(focusBefore, getWidget().getElement())) {
                editorPresenter.focus();
            }
            return false;
        }, 500);
    }

    private static native Element getActiveElement() /*-{
        return $doc.activeElement;
    }-*/;

    // Whether the focus is where it was, in the query, or has gone nowhere (to the page, or with an
    // element that has gone)
    private static native boolean isFocusUnmoved(Element before, Element query) /*-{
        var active = $doc.activeElement;
        return !active || active === before || active === $doc.body || !active.isConnected
            || query.contains(active);
    }-*/;

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
        currentVisPresenter = make(visPresenterProvider);
        currentVisPresenter.setQueryModel(queryModel);
        queryResultPresenter.setQueryResultVisPresenter(currentVisPresenter);
        if (VISUALISATION.equals(linkTabsLayoutView.getTabBar().getSelectedTab())) {
            linkTabsLayoutView.getLayerContainer().show(currentVisPresenter);
        }
    }

    private void destroyCurrentVis() {
        if (currentVisPresenter != null) {
            currentVisPresenter.onRemove();
            release(currentVisPresenter);
            currentVisPresenter = null;
        }
    }

    @Override
    public List<Widget> getToolbars() {
        return Collections.singletonList(queryToolbar.asWidget());
    }

    public void updateQuery(final String query) {
        // Debounce requests so we don't spam the backend
        if (requestTimer != null) {
            requestTimer.cancel();
        }

        requestTimer = new Timer() {
            @Override
            public void run() {
                if (!Objects.equals(currentQuery, query)) {
                    currentQuery = query;
                    queryHelp.setQuery(query);
                    queryResultPresenter.setQuery(query);
                }
            }
        };
        requestTimer.schedule(DEBOUNCE_DELAY_MS);
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

    public void onClose() {
        queryModel.reset(DestroyReason.TAB_CLOSE);
        destroyCurrentVis();
    }

    void toggleStart() {
        if (queryModel.isSearching()) {
            queryModel.stop();
        } else {
            run();
        }
    }

    public void start() {
        if (queryModel.isSearching()) {
            queryModel.stop();
        }
        run();
    }

    public void stop() {
        queryModel.stop();
    }

    /**
     * Registers a listener notified whenever this query starts or stops searching.
     *
     * <p>Needed by consumers that must act only on a <em>finished</em> result set.
     * Searches here run incrementally, so the result table is updated on every
     * poll with whatever the store holds at that moment; a consumer that treats
     * each of those updates as a result set acts on a half-filled store. The
     * searching-to-idle transition is the only signal that the rows are final.</p>
     *
     * @param listener called with {@code true} when a search starts and
     *                 {@code false} when it completes, is stopped or is reset
     * @return the registration, to be removed when the caller unbinds
     */
    public HandlerRegistration addSearchStateListener(final SearchStateListener listener) {
        queryModel.addSearchStateListener(listener);
        return () -> queryModel.removeSearchStateListener(listener);
    }

    private void run() {
        // No point running the search if there is no query
        if (!NullSafe.isBlankString(editorPresenter.getText())) {
            queryInfo.prompt(() -> run(Function.identity()), this);
        }
    }

    // The incremental and storeHistory flags are always true, so they are hardcoded at the
    // startNewSearch call below rather than taken as parameters. If a caller ever needs to pass
    // something other than true, restore the parameters and thread them through.
    // The expressionDecorator parameter below is unused (it is always called with
    // Function.identity()).
    @SuppressWarnings("unused")
    private void run(final Function<ExpressionOperator, ExpressionOperator> expressionDecorator) {
        // Clear the table selection and any markers.
        queryResultPresenter.clear();
        editorPresenter.setMarkers(Collections.emptyList());
        editorPresenter.setIndicators(new IndicatorLines(new Indicators()));

        // Destroy any previous query.
        queryModel.reset(DestroyReason.NO_LONGER_NEEDED);

        // The query variables are passed as Params. FloorMap's param('FactStore') /
        // param('EventStore') references, which are how a floor map selects its temporal stores,
        // depend on them.
        //
        // The query text is sent exactly as written. It used to be rewritten first — every
        // param('key') replaced by its quoted value — because the from clause accepted only a
        // string literal. SearchRequestFactory.resolveDataSourceName now resolves param() there,
        // so the values travel as ordinary Params and the text is left alone. That also keeps the
        // error offsets the editor highlights aligned with what the user is looking at, which
        // rewriting the text silently broke.
        List<Param> params = null;
        if (queryVariables != null && !queryVariables.isEmpty()) {
            params = new ArrayList<>();
            for (final Map.Entry<String, String> entry : queryVariables.entrySet()) {
                params.add(new Param(entry.getKey(), entry.getValue()));
            }
        }
        queryModel.startNewSearch(
                null,
                null,
                editorPresenter.getText(),
                params,
                queryToolbar.getTimeRange(),
                true,
                true,
                queryInfo.getMessage(),
                null);
    }

    public TimeRange getTimeRange() {
        return queryToolbar.getTimeRange();
    }

    public void setTimeRange(final TimeRange timeRange) {
        queryToolbar.setTimeRange(timeRange);
    }

    /**
     * Sets query parameters to be made available during query execution.
     *
     * <p>They are passed natively as {@link Param}s and the query text is sent unmodified, so a
     * {@code param('key')} reference resolves server-side wherever it appears — including the
     * {@code from} clause, which {@code SearchRequestFactory.resolveDataSourceName} handles.</p>
     *
     * @param queryVariables parameter key → value map, or {@code null}
     */
    public void setQueryVariables(final Map<String, String> queryVariables) {
        this.queryVariables = queryVariables;
    }

    public void setQuery(final DocRef docRef, final String query) {
        // Read only as its document is
        final boolean readOnly = context(Contexts.READ_ONLY);
        queryModel.init(docRef);
        if (query != null) {
            if (NullSafe.isBlankString(editorPresenter.getText())
                || !Objects.equals(editorPresenter.getText(), query)) {
                editorPresenter.setText(query);
                updateQuery(query);
            }
        }

        editorPresenter.setReadOnly(readOnly);
        editorPresenter.getFormatAction().setAvailable(!readOnly);
        focus();
    }

    public String getQuery() {
        return editorPresenter.getText();
    }

    private void onChange() {
        onChange.run();
    }

    /// @param onChange What to run when the user changes the query or its settings.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    public QueryTablePreferences write() {
        return queryTablePreferences;
    }

    public void read(final QueryTablePreferences queryTablePreferences) {
        if (queryTablePreferences != null) {
            this.queryTablePreferences = queryTablePreferences;
            queryResultPresenter.updateQueryTablePreferences();
        }
    }

    public void onContentTabVisible(final boolean visible) {
        queryResultPresenter.onContentTabVisible(visible);
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<String> handler) {
        return editorPresenter.addValueChangeHandler(handler);
    }

    public QueryResultTableSplitPresenter getQueryResultPresenter() {
        return queryResultPresenter;
    }

    // --------------------------------------------------------------------------------

    public interface QueryEditView extends View {

        void setQueryHelp(IsWidget queryHelp);

        void setQueryEditor(View view);

        void setResultView(View view);
    }
}
