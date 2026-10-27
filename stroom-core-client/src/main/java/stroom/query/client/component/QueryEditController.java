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

package stroom.query.client.component;

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
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryModel;
import stroom.query.client.presenter.ResultComponent;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.query.client.presenter.SearchStateListener;
import stroom.query.shared.QueryTablePreferences;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Timer;
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

/// A StroomQL query ([QueryEdit]): its editor and help, the query toolbar that runs it, and its
/// results as a table and, when the query makes one, a visualisation. Shown by a query document and
/// an analytic rule's or report's Query tab, and by a floor map's query.
public class QueryEditController
        extends Controller
        implements HasToolbar, HasHandlers, HasValueChangeHandlers<String> {

    private static final int DEBOUNCE_DELAY_MS = 400;
    private static final TabData TABLE = new TabDataImpl("Table");
    private static final TabData VISUALISATION = new TabDataImpl("Visualisation");

    private final QueryHelpController queryHelp;
    private final QueryToolbarController queryToolbar;
    private final EditorPresenter editorPresenter;
    private final QueryResultTableSplitController queryResultPresenter;
    private final QueryModel queryModel;
    private final QueryEdit view;
    private final QueryInfo queryInfo;
    private QueryTablePreferences queryTablePreferences = QueryTablePreferences.builder().build();

    private final Provider<QueryResultVisController> visPresenterProvider;

    private QueryResultVisController currentVisPresenter;
    private String currentQuery;
    private Timer requestTimer;
    private Map<String, String> queryVariables;

    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its content and its parts.
    ///
    /// @param eventBus                The application's event bus.
    /// @param view                    The content.
    /// @param queryHelp               The query's help.
    /// @param queryToolbar            Runs the query, over a time range.
    /// @param editorPresenter         The query's editor.
    /// @param queryResultPresenter    The results as a table.
    /// @param visPresenterProvider    Makes the results' visualisation.
    /// @param restFactory             Runs the query.
    /// @param dateTimeSettingsFactory Gives the query the user's time settings.
    /// @param resultStoreModel        Keeps the query's results.
    /// @param queryInfo               Asks for the query's purpose, if Stroom is set to.
    @Inject
    public QueryEditController(final EventBus eventBus,
                               final QueryEdit view,
                               final QueryHelpController queryHelp,
                               final QueryToolbarController queryToolbar,
                               final EditorPresenter editorPresenter,
                               final QueryResultTableSplitController queryResultPresenter,
                               final Provider<QueryResultVisController> visPresenterProvider,
                               final RestFactory restFactory,
                               final DateTimeSettingsFactory dateTimeSettingsFactory,
                               final ResultStoreModel resultStoreModel,
                               final QueryInfo queryInfo) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(queryHelp);
        own(queryToolbar);
        own(editorPresenter);
        own(queryResultPresenter);
        this.queryHelp = queryHelp;
        this.queryToolbar = queryToolbar;
        this.queryResultPresenter = queryResultPresenter;
        this.visPresenterProvider = visPresenterProvider;
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
        // Shows its requests' progress as this controller's owners do (its context's)
        queryModel.setTaskMonitorFactory(taskMonitor());
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

        view.setHelp(queryHelp);
        view.setEditor(this.editorPresenter.getView());

        view.addTab(TABLE);
        view.addTab(VISUALISATION);
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
        view.setOnSelectTab(this::selectTab);
        // Destroys the query when its tab is closed, if not before
        onDispose(this::onClose);
        queryResultPresenter.setOnChange(this::onChange);
    }

    private void focus() {
        // Only if the focus hasn't moved since, other than within the query (e.g. as the screen is
        // opened), so this doesn't take it from a menu or dialog the user has opened in the meantime
        final Element focusBefore = getActiveElement();
        Scheduler.get().scheduleFixedDelay(() -> {
            if (isFocusUnmoved(focusBefore, asWidget().getElement())) {
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
        final boolean currentState = view.isTabHidden(VISUALISATION);
        if (currentState != state) {
            view.setTabHidden(VISUALISATION, state);
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
        queryResultPresenter.setQueryResultVis(currentVisPresenter);
        if (VISUALISATION.equals(view.getSelectedTab())) {
            view.showContent(currentVisPresenter);
        }
    }

    private void destroyCurrentVis() {
        if (currentVisPresenter != null) {
            currentVisPresenter.onRemove();
            release(currentVisPresenter);
            currentVisPresenter = null;
        }
    }

    // Lazily, as it is first used after this is owned
    private TaskMonitorFactory taskMonitor() {
        return () -> {
            final TaskMonitorFactory provided = getProvidedTaskMonitor();
            return provided != null
                    ? provided.createTaskMonitor()
                    : new DefaultTaskMonitorFactory(this).createTaskMonitor();
        };
    }

    @Override
    public List<Widget> getToolbars() {
        return Collections.singletonList(queryToolbar.asWidget());
    }

    /// Tells the help and the table's column menus of the query, once it has stopped changing.
    ///
    /// @param query The query.
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
            view.showTab(tabData, queryResultPresenter);
        } else if (VISUALISATION.equals(tabData)) {
            view.showTab(tabData, currentVisPresenter);
        }
    }

    /// Destroys the query on the server, as its tab is closed.
    public void onClose() {
        queryModel.reset(DestroyReason.TAB_CLOSE);
        destroyCurrentVis();
    }

    private void toggleStart() {
        if (queryModel.isSearching()) {
            queryModel.stop();
        } else {
            run();
        }
    }

    /// Runs the query again, stopping it first if it is running.
    public void start() {
        if (queryModel.isSearching()) {
            queryModel.stop();
        }
        run();
    }

    /// Stops the query.
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
            queryInfo.prompt(() -> run(Function.identity()), taskMonitor());
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

    /// @return The time range the query runs over.
    public TimeRange getTimeRange() {
        return queryToolbar.getTimeRange();
    }

    /// @param timeRange The time range the query runs over.
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

    /// Shows a query, read only if its document is.
    ///
    /// @param docRef The document the query is from.
    /// @param query  The query.
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

    /// @return The query, as edited.
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

    /// @return The table's preferences, e.g. its columns.
    public QueryTablePreferences write() {
        return queryTablePreferences;
    }

    /// @param queryTablePreferences The table's preferences, e.g. its columns.
    public void read(final QueryTablePreferences queryTablePreferences) {
        if (queryTablePreferences != null) {
            this.queryTablePreferences = queryTablePreferences;
            queryResultPresenter.updateQueryTablePreferences();
        }
    }

    /// @param visible Whether the query's tab is showing.
    public void onContentTabVisible(final boolean visible) {
        queryResultPresenter.onContentTabVisible(visible);
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<String> handler) {
        return editorPresenter.addValueChangeHandler(handler);
    }

    /// @return The results table.
    public QueryResultTableSplitController getQueryResultPresenter() {
        return queryResultPresenter;
    }
}
