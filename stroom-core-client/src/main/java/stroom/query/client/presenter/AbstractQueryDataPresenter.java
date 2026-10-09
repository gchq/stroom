package stroom.query.client.presenter;

import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.Size;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.presenter.DocPresenter;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.TokenError;
import stroom.util.shared.Version;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractQueryDataPresenter<V extends QueryDataView, D>
        extends DocPresenter<V, D>
        implements QueryDataUiHandlers {

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final QueryModel queryModel;
    private final QueryResultTablePresenter tablePresenter;
    private final RestFactory restFactory;
    private QueryTablePreferences queryTablePreferences = QueryTablePreferences.builder().build();
    private DocRef currentDocRef;
    private D currentDoc;

    public AbstractQueryDataPresenter(final EventBus eventBus,
                                      final V view,
                                      final QueryResultTablePresenter tablePresenter,
                                      final RestFactory restFactory,
                                      final DateTimeSettingsFactory dateTimeSettingsFactory,
                                      final ResultStoreModel resultStoreModel) {
        super(eventBus, view);
        this.tablePresenter = tablePresenter;
        this.restFactory = restFactory;

        view.setUiHandlers(this);
        view.setTable(tablePresenter.getView());

        tablePresenter.setEmptyText("No data");
        tablePresenter.setQueryTablePreferencesSupplier(() -> queryTablePreferences);
        tablePresenter.setQueryTablePreferencesConsumer(qtp -> queryTablePreferences = qtp);
        tablePresenter.setData(null);

        queryModel = new QueryModel(
                eventBus,
                restFactory,
                dateTimeSettingsFactory,
                resultStoreModel,
                () -> queryTablePreferences);
        queryModel.addResultComponent(QueryModel.TABLE_COMPONENT_ID, tablePresenter);
        queryModel.addSearchErrorListener(errors -> {
            if (errors != null && !errors.isEmpty()) {
                final String errorMsg = errors.stream()
                        .map(ErrorMessage::getMessage)
                        .collect(Collectors.joining("\n"));
                getView().setError(errorMsg);
            } else {
                getView().clearError();
            }
        });
        queryModel.addTokenErrorListener(tokenError -> {
            if (tokenError != null) {
                final String detailedMessage = tokenError.getText() +
                        " at line " + tokenError.getFrom().getLineNo() +
                        ", column " + tokenError.getFrom().getColNo();
                getView().setError(detailedMessage);
                selectOffendingToken(tokenError);
            }
        });
    }

    @Override
    protected void onRead(final DocRef docRef, final D doc, final boolean readOnly) {
        this.currentDocRef = docRef;
        this.currentDoc = doc;
        tablePresenter.setPreferredColumns(getPreferredColumns(doc));
        getView().setQuery(getDefaultQuery(docRef, doc));
        getView().clearError();
    }

    @Override
    protected D onWrite(final D doc) {
        return doc;
    }

    @Override
    public void onRun() {
        getView().clearError();
        queryModel.startNewSearch(
                QueryModel.TABLE_COMPONENT_ID,
                "Table",
                getView().getQuery(),
                Collections.emptyList(),
                null,
                false,
                false,
                null,
                null);
    }

    @Override
    public void onStop() {
        queryModel.stop();
    }

    @Override
    public void onReset() {
        if (currentDocRef != null && currentDoc != null) {
            getView().setQuery(getDefaultQuery(currentDocRef, currentDoc));
            getView().clearError();
        }
    }

    protected abstract String getDefaultQuery(DocRef docRef, D doc);

    protected abstract List<Column> getPreferredColumns(D doc);

    private void selectOffendingToken(final TokenError tokenError) {
        final String queryText = getView().getQuery();
        if (queryText == null || queryText.isEmpty()) {
            return;
        }

        final int startOffset = getCharOffset(
                queryText, tokenError.getFrom().getLineNo(), tokenError.getFrom().getColNo());
        final int endOffset = getCharOffset(queryText, tokenError.getTo().getLineNo(), tokenError.getTo().getColNo());

        if (startOffset >= 0 && endOffset >= startOffset && endOffset <= queryText.length()) {
            getView().selectQueryRange(startOffset, endOffset - startOffset);
        }
    }

    private int getCharOffset(final String text, final int lineNo, final int colNo) {
        if (lineNo < 1 || colNo < 0) {
            return -1;
        }
        int offset = 0;
        int currentLine = 1;
        for (int i = 0; i < text.length(); i++) {
            if (currentLine == lineNo) {
                return offset + colNo;
            }
            final char c = text.charAt(i);
            offset++;
            if (c == '\n') {
                currentLine++;
            }
        }
        return currentLine == lineNo ? offset + colNo : -1;
    }

    @Override
    public void onCreateDashboard() {
        if (currentDocRef == null) {
            return;
        }

        final String queryText = getView().getQuery();
        final stroom.query.shared.QueryTablePreferences tablePrefs = this.queryTablePreferences;

        ShowCreateDocumentDialogEvent.fire(
                this,
                "Create Dashboard from Query",
                null,
                DashboardDoc.TYPE,
                currentDocRef.getName() + " Dashboard",
                false,
                newDocExplorerNode -> {
                    final DocRef dashboardRef = newDocExplorerNode.getDocRef();
                    configureDashboard(dashboardRef, queryText, tablePrefs);
                }
        );
    }

    private void configureDashboard(final DocRef dashboardRef,
                                    final String queryText,
                                    final stroom.query.shared.QueryTablePreferences tablePrefs) {
        restFactory.create(QUERY_RESOURCE)
                .method(res -> res.parseQuery(queryText))
                .onSuccess(expressionOperator -> {
                    restFactory.create(DASHBOARD_RESOURCE)
                            .method(res -> res.fetch(dashboardRef.getUuid()))
                            .onSuccess(dashboardDoc -> {
                                final DashboardConfig dashboardConfig =
                                        buildDashboardConfig(expressionOperator, tablePrefs);

                                final DashboardDoc updatedDoc = dashboardDoc.copy()
                                        .dashboardConfig(dashboardConfig)
                                        .build();

                                restFactory.create(DASHBOARD_RESOURCE)
                                        .method(res -> res.update(dashboardRef.getUuid(), updatedDoc))
                                        .onSuccess(savedDoc -> {
                                            OpenDocumentEvent.fire(this, dashboardRef, true);
                                        })
                                        .exec();
                            })
                            .exec();
                })
                .exec();
    }

    private DashboardConfig buildDashboardConfig(final ExpressionOperator expressionOperator,
                                                 final stroom.query.shared.QueryTablePreferences tablePrefs) {
        final String queryId = "query-1";
        final String tableId = "table-1";
        final List<ComponentConfig> components = new ArrayList<>();

        final QueryComponentSettings querySettings = QueryComponentSettings.builder()
                .dataSource(currentDocRef)
                .expression(expressionOperator)
                .build();
        final ComponentConfig queryComponent = ComponentConfig.builder()
                .type("query")
                .id(queryId)
                .name("Query")
                .settings(querySettings)
                .build();
        components.add(queryComponent);

        final TableComponentSettings tableSettings = TableComponentSettings.builder()
                .queryId(queryId)
                .dataSourceRef(currentDocRef)
                .columns(tablePrefs.getColumns())
                .showValueFilters(tablePrefs.getShowValueFilters())
                .build();
        final ComponentConfig tableComponent = ComponentConfig.builder()
                .type("table")
                .id(tableId)
                .name("Table")
                .settings(tableSettings)
                .build();
        components.add(tableComponent);

        final List<LayoutConfig> layoutChildren = List.of(
                TabLayoutConfig.builder()
                        .preferredSize(new Size(200, 200))
                        .tabs(List.of(new TabConfig(queryId, true)))
                        .selected(0)
                        .build(),
                TabLayoutConfig.builder()
                        .preferredSize(new Size(200, 200))
                        .tabs(List.of(new TabConfig(tableId, true)))
                        .selected(0)
                        .build()
        );
        final SplitLayoutConfig splitLayout = SplitLayoutConfig.builder()
                .preferredSize(new Size(200, 200))
                .dimension(Dimension.Y)
                .children(layoutChildren)
                .build();

        return DashboardConfig.builder()
                .components(components)
                .layout(splitLayout)
                .designMode(true)
                .modelVersion(Version.of(7, 2, 0).toString())
                .build();
    }
}
