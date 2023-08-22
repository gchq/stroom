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

import stroom.core.client.event.WindowCloseEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.editor.client.view.Marker;
import stroom.entity.client.presenter.HasToolbar;
import stroom.pipeline.shared.SourceLocation;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.client.presenter.QueryHelpPresenter.HelpItemType;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpDataSupplier;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.view.client.presenter.DataSourceFieldsMap;
import stroom.view.client.presenter.IndexLoader;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
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

public class QueryEditPresenter
        extends MyPresenterWidget<QueryEditView>
        implements HasDirtyHandlers, HasToolbar {

    private static final Set<HelpItemType> SUPPORTED_HELP_TYPES = EnumSet.of(
            HelpItemType.DATA_SOURCE,
            HelpItemType.STRUCTURE,
            HelpItemType.FIELD,
            HelpItemType.FUNCTION);

    private final QueryHelpPresenter queryHelpPresenter;
    private final QueryToolbarPresenter queryToolbarPresenter;
    private final EditorPresenter editorPresenter;
    private final QueryResultTablePresenter tablePresenter;
    private final IndexLoader indexLoader;
    private final TextPresenter textPresenter;
    private final Views views;
    private boolean dirty;
    private boolean reading;
    private boolean readOnly = true;
    private final QueryModel queryModel;
    private final ThinSplitLayoutPanel splitLayoutPanel;

    @Inject
    public QueryEditPresenter(final EventBus eventBus,
                              final QueryEditView view,
                              final QueryHelpPresenter queryHelpPresenter,
                              final QueryToolbarPresenter queryToolbarPresenter,
                              final EditorPresenter editorPresenter,
                              final QueryResultTablePresenter tablePresenter,
                              final RestFactory restFactory,
                              final IndexLoader indexLoader,
                              final TextPresenter textPresenter,
                              final Views views,
                              final DateTimeSettingsFactory dateTimeSettingsFactory,
                              final ResultStoreModel resultStoreModel) {
        super(eventBus, view);
        this.queryHelpPresenter = queryHelpPresenter;
        this.queryToolbarPresenter = queryToolbarPresenter;
        this.tablePresenter = tablePresenter;
        this.indexLoader = indexLoader;
        this.textPresenter = textPresenter;
        this.views = views;

        queryModel = new QueryModel(
                restFactory,
                indexLoader,
                dateTimeSettingsFactory,
                resultStoreModel,
                tablePresenter);
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

        splitLayoutPanel = new ThinSplitLayoutPanel();
        splitLayoutPanel.addStyleName("max");

        view.setQueryHelp(queryHelpPresenter.getView());
        view.setQueryEditor(this.editorPresenter.getView());
        view.setTable(tablePresenter.getWidget());
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
            }
        }, KeyDownEvent.getType()));
        registerHandler(editorPresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(tablePresenter.addExpanderHandler(event -> queryModel.refresh()));
        registerHandler(tablePresenter.addRangeChangeHandler(event -> queryModel.refresh()));
        registerHandler(tablePresenter.getSelectionModel().addSelectionHandler(event ->
                onSelection(tablePresenter.getSelectionModel().getSelected())));
        registerHandler(queryToolbarPresenter.addStartQueryHandler(e -> run(true, true)));
        registerHandler(queryToolbarPresenter.addTimeRangeChangeHandler(e -> run(true, true)));
        queryHelpPresenter.linkToEditor(editorPresenter);

        registerHandler(getEventBus().addHandler(WindowCloseEvent.getType(), event -> {
            // If a user is even attempting to close the browser or browser tab then destroy the query.
            queryModel.reset(DestroyReason.WINDOW_CLOSE);
        }));

        setupQueryHelpDataSupplier();
    }

    private void onSelection(final TableRow tableRow) {
        if (tableRow == null) {
            getView().setTable(tablePresenter.getWidget());
        } else {
            final String streamId = tableRow.getText("StreamId");
            final String eventId = tableRow.getText("EventId");
            if (streamId != null && eventId != null && streamId.length() > 0 && eventId.length() > 0) {
                try {
                    final long strmId = Long.valueOf(streamId);
                    final long evtId = Long.valueOf(eventId);
                    final SourceLocation sourceLocation = SourceLocation
                            .builder(strmId)
                            .withPartIndex(0L)
                            .withRecordIndex(evtId - 1)
                            .build();
                    textPresenter.show(sourceLocation);
                    final double size = Math.max(100, getWidget().getElement().getOffsetWidth() / 2D);
                    splitLayoutPanel.addEast(textPresenter.getWidget(), size);
                    splitLayoutPanel.add(tablePresenter.getWidget());
                    getView().setTable(splitLayoutPanel);

                } catch (final RuntimeException e) {
                    getView().setTable(tablePresenter.getWidget());
                }
            } else {
                getView().setTable(tablePresenter.getWidget());
            }
        }
    }

    private void setupQueryHelpDataSupplier() {
        queryHelpPresenter.setQueryHelpDataSupplier(new QueryHelpDataSupplier() {

            @Override
            public DataSourceFieldsMap getDataSourceFieldsMap() {
                return indexLoader.getDataSourceFieldsMap();
            }

            @Override
            public String decorateFieldName(final String fieldName) {
                return GwtNullSafe.get(fieldName, str ->
                        str.contains(" ")
                                ? "\"" + str + "\""
                                : str);
            }

            @Override
            public void registerChangeHandler(final Consumer<DataSourceFieldsMap> onChange) {
                registerHandler(indexLoader.addChangeDataHandler(e -> {
                    onChange.accept(indexLoader.getDataSourceFieldsMap());
                }));
            }

            @Override
            public boolean isSupported(final HelpItemType helpItemType) {
                return helpItemType != null && SUPPORTED_HELP_TYPES.contains(helpItemType);
            }

            @Override
            public void fetchDataSources(final Consumer<List<DocRef>> dataSourceConsumer) {
                views.fetchViews(dataSourceConsumer);
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
        tablePresenter.getSelectionModel().clear();
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

        queryModel.init(docRef.getUuid(), "query");
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

        void setTable(Widget widget);
    }
}
