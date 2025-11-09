/*
 * Copyright 2024 Crown Copyright
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

package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.core.client.LocationManager;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.shared.DataResource;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.meta.shared.UpdateStatusRequest;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.shared.PipelineDoc;
import stroom.preferences.client.DateTimeFormatter;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.util.shared.Severity;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.view.client.Range;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Provider;

public abstract class AbstractMetaListPresenter
        extends MyPresenterWidget<PagerView>
        implements HasDataSelectionHandlers<Selection<Long>>, Refreshable {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);
    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final Selection<Long> selection = new Selection<>(false, new HashSet<>());
    private final RestFactory restFactory;
    private final LocationManager locationManager;
    private final DateTimeFormatter dateTimeFormatter;
    private final FindMetaCriteria criteria;
    private final RestDataProvider<MetaRow, ResultPage<MetaRow>> dataProvider;
    private final Provider<SelectionSummaryPresenter> selectionSummaryPresenterProvider;
    private final Provider<ProcessChoicePresenter> processChoicePresenterProvider;
    private final Provider<DocSelectionPopup> pipelineSelection;
    private final ExpressionValidator expressionValidator;

    private ResultPage<MetaRow> resultPage;
    private boolean initialised;
    final MyDataGrid<MetaRow> dataGrid;
    private final MultiSelectionModelImpl<MetaRow> selectionModel;
    private final DataGridSelectionEventManager<MetaRow> selectionEventManager;

    AbstractMetaListPresenter(final EventBus eventBus,
                              final PagerView view,
                              final RestFactory restFactory,
                              final LocationManager locationManager,
                              final DateTimeFormatter dateTimeFormatter,
                              final Provider<SelectionSummaryPresenter> selectionSummaryPresenterProvider,
                              final Provider<ProcessChoicePresenter> processChoicePresenterProvider,
                              final Provider<DocSelectionPopup> pipelineSelection,
                              final ExpressionValidator expressionValidator,
                              final boolean allowSelectAll) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        this.dateTimeFormatter = dateTimeFormatter;
        this.selectionSummaryPresenterProvider = selectionSummaryPresenterProvider;
        this.processChoicePresenterProvider = processChoicePresenterProvider;
        this.pipelineSelection = pipelineSelection;
        this.expressionValidator = expressionValidator;

        this.dataGrid = new MyDataGrid<>(this);
        selectionModel = new MultiSelectionModelImpl<>();
        selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);

        view.setDataWidget(dataGrid);

        selection.setMatchAll(false);
        addColumns(allowSelectAll);

        criteria = new FindMetaCriteria();
        dataProvider = new RestDataProvider<MetaRow, ResultPage<MetaRow>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<MetaRow>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                if (criteria.getExpression() != null) {
                    CriteriaUtil.setRange(criteria, range);
                    CriteriaUtil.setSortList(criteria, dataGrid.getColumnSortList());
                    restFactory
                            .create(META_RESOURCE)
                            .method(res -> res.findMetaRow(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .taskMonitorFactory(view)
                            .exec();
                } else {
                    dataConsumer.accept(new ResultPage<>(Collections.emptyList()));
                }
            }

            @Override
            protected void changeData(final ResultPage<MetaRow> data) {
                super.changeData(onProcessData(data));
            }
        };
    }

    @Override
    protected void onBind() {
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    protected ResultPage<MetaRow> onProcessData(final ResultPage<MetaRow> data) {
        boolean equalsList = true;

        // We compare the old and new lists to see if we need to do
        // the work of refreshing selections etc

        // Lists may have same entities but different versions (e.g. status)
        if (data == null && resultPage != null) {
            equalsList = false;
        }
        if (data != null && resultPage == null) {
            equalsList = false;
        }
        if (data != null && resultPage != null) {
            final List<MetaRow> oldList = resultPage.getValues();
            final List<MetaRow> newList = data.getValues();

            if (oldList.size() != newList.size()) {
                equalsList = false;
            } else {
                for (int i = 0; i < oldList.size(); i++) {
                    final Meta oldMeta = oldList.get(i).getMeta();
                    final Meta newMeta = newList.get(i).getMeta();

                    if (!oldMeta.equals(newMeta)) {
                        equalsList = false;
                        break;
                    }
                }
            }
        }

        this.resultPage = data;

        if (!equalsList) {
            if (selection.getSet() != null && selection.getSet().size() > 0) {
                final boolean matchAll = selection.isMatchAll();
                final Set<Long> oldIdSet = new HashSet<>(selection.getSet());
                selection.clear();
                if (matchAll) {
                    selection.setMatchAll(matchAll);
                } else if (data != null && oldIdSet.size() > 0) {
                    for (final MetaRow map : data.getValues()) {
                        final long id = map.getMeta().getId();
                        if (oldIdSet.contains(id)) {
                            selection.add(id);
                        }
                    }
                }
            }
        }

        // There might have been a selection change so fire a data selection event.
        DataSelectionEvent.fire(AbstractMetaListPresenter.this, selection, false);

        final MetaRow selected = selectionModel.getSelected();
        if (selected != null) {
            if (!resultPage.getValues().contains(selected)) {
                selectionModel.setSelected(selected, false);
            }
        }

        return data;
    }

    protected abstract void addColumns(final boolean allowSelectAll);

    private void setMatchAll(final boolean select) {
        selection.clear();
        selection.setMatchAll(select);
        if (dataProvider != null) {
            dataProvider.updateRowData(dataProvider.getRanges()[0].getStart(), resultPage.getValues());
        }
        DataSelectionEvent.fire(AbstractMetaListPresenter.this, selection, false);
    }

    void addSelectedColumn(final boolean allowSelectAll) {
        // Select Column
        final Column<MetaRow, TickBoxState> column = new Column<MetaRow, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final MetaRow object) {
                return TickBoxState.fromBoolean(selection.isMatch(object.getMeta().getId()));
            }
        };
        if (allowSelectAll) {
            final Header<TickBoxState> header = new Header<TickBoxState>(
                    TickBoxCell.create(false, false)) {
                @Override
                public TickBoxState getValue() {
                    if (selection.isMatchAll()) {
                        return TickBoxState.TICK;
                    }
                    if (selection.size() > 0) {
                        return TickBoxState.HALF_TICK;
                    }
                    return TickBoxState.UNTICK;
                }
            };
            dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

            header.setUpdater(value -> {
                if (value.equals(TickBoxState.UNTICK)) {
                    setMatchAll(false);
                } else if (value.equals(TickBoxState.TICK)) {
                    setMatchAll(true);
                }
            });

            registerHandler(selectionEventManager.addSelectAllHandler(event ->
                    setMatchAll(!selection.isMatchAll())));

        } else {
            dataGrid.addColumn(column, "", ColumnSizeConstants.CHECKBOX_COL);
        }

        // Add Handlers
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selection.add(row.getMeta().getId());

            } else {
                // De-selecting one and currently matching all ?
                if (selection.isMatchAll()) {
                    selection.setMatchAll(false);

                    final Set<Long> resultStreamIdSet = getResultStreamIdSet();
                    selection.addAll(resultStreamIdSet);
                }
                selection.remove(row.getMeta().getId());
            }
            dataGrid.redrawHeaders();
            DataSelectionEvent.fire(AbstractMetaListPresenter.this, selection, false);
        });
    }

    protected Preset getInfoCellState(final MetaRow metaRow) {
        if (metaRow == null || metaRow.getMeta() == null || metaRow.getMeta().getStatus() == null) {
            return null;
        }

        // Should only show unlocked ones by default
        final Status status = metaRow.getMeta().getStatus();
        if (Status.UNLOCKED.equals(status)) {
            // Be default the screen only shows unlocked streams so have no icon for those
            return null;
        } else if (Status.DELETED.equals(status)) {
            return SvgPresets.DELETED.title("Deleted Stream");
        } else if (Status.LOCKED.equals(status)) {
            return SvgPresets.LOCKED_AMBER.title("Locked Stream");
        } else {
            throw new RuntimeException("Unknown status " + status);
        }
    }

    void addInfoColumn() {
        DataGridUtil.addStatusIconColumn(dataGrid, this::getInfoCellState);
    }

    void addCreatedColumn() {
        final Column<MetaRow, String> col = DataGridUtil.copyTextColumnBuilder((MetaRow metaRow) ->
                                dateTimeFormatter.format(metaRow.getMeta().getCreateMs()),
                        getEventBus())
                .withSorting(MetaFields.CREATE_TIME)
                .defaultSortAscending(false)
                .build();
        dataGrid.addResizableColumn(col,
                "Created",
                ColumnSizeConstants.DATE_COL);
        // Sort by create time by default.
        dataGrid.sort(col);
    }

    void addFeedColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.feedRefColumnBuilder((MetaRow metaRow) ->
                                        Optional.ofNullable(metaRow)
                                                .map(this::getFeed)
                                                .orElse(null),
                                getEventBus())
                        .withSorting(MetaFields.FEED)
                        .build(),
                "Feed",
                ColumnSizeConstants.BIG_COL);
    }

    void addStreamTypeColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((MetaRow metaRow) ->
                                        Optional.ofNullable(metaRow)
                                                .map(MetaRow::getMeta)
                                                .map(Meta::getTypeName)
                                                .orElse(""),
                                getEventBus())
                        .withSorting(MetaFields.TYPE)
                        .build(),
                "Type",
                80);
    }

    private String getFeed(final MetaRow metaRow) {
        if (metaRow.getMeta() != null && metaRow.getMeta().getFeedName() != null) {
            return metaRow.getMeta().getFeedName();
        }
        return null;
    }

    private DocRef getPipeline(final MetaRow metaRow) {
        if (metaRow.getMeta().getProcessorUuid() != null) {
            if (metaRow.getPipeline() != null) {
                return metaRow.getPipeline();
            }
        }
        return null;
    }

    void addPipelineColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.docRefColumnBuilder((MetaRow metaRow) ->
                                        Optional.ofNullable(metaRow)
                                                .map(this::getPipeline)
                                                .orElse(null),
                                getEventBus())
                        .withSorting(MetaFields.PIPELINE_NAME)
                        .build(),
                "Pipeline",
                ColumnSizeConstants.BIG_COL);
    }

    protected MultiSelectionModel<MetaRow> getSelectionModel() {
        return selectionModel;
    }

    Selection<Long> getSelection() {
        return selection;
    }

    private Set<Long> getResultStreamIdSet() {
        final Set<Long> rtn = new HashSet<>();
        if (resultPage != null) {
            for (final MetaRow e : resultPage.getValues()) {
                rtn.add(e.getMeta().getId());
            }
        }
        return rtn;

    }

    ResultPage<MetaRow> getResultPage() {
        return resultPage;
    }

    void addAttributeColumn(final String name,
                            final QueryField attribute,
                            final int size) {

        final Function<MetaRow, String> extractor = metaRow ->
                metaRow.getAttributeValue(attribute.getFldName());

        final Column<MetaRow, String> column = DataGridUtil.columnBuilder(extractor, TextCell::new)
                .build();

        dataGrid.addResizableColumn(
                column,
                name,
                size);
    }

    void addRightAlignedAttributeColumn(final String name,
                                        final QueryField attribute,
                                        final Function<String, String> formatter,
                                        final int size) {

        final Function<MetaRow, String> extractor = metaRow -> {
            final String value = metaRow.getAttributeValue(attribute.getFldName());
            if (value == null) {
                return null;
            } else {
                return formatter.apply(value);
            }
        };

        final Column<MetaRow, String> column = DataGridUtil.columnBuilder(extractor, TextCell::new)
                .rightAligned()
                .build();

        dataGrid.addResizableColumn(
                column,
                DataGridUtil.createRightAlignedHeader(name),
                size);
    }

    void addColouredSizeAttributeColumn(final String name,
                                        final QueryField attribute,
                                        final Function<String, String> formatter,
                                        final int size) {

        final Function<MetaRow, String> extractor = metaRow -> {
            final String value = metaRow.getAttributeValue(attribute.getFldName());
            if (value == null) {
                return null;
            } else {
                return formatter.apply(value);
            }
        };

        final Function<String, String> colourFunc = val -> {
            if (val == null) {
                return "black";
            } else if (val.endsWith("B")) {
                return "blue";
            } else if (val.endsWith("K")) {
                return "green";
            } else if (val.endsWith("M")) {
                return "#FF7F00";
            } else if (val.endsWith("G")) {
                return "red";
            } else {
                return "red";
            }
        };

        final Column<MetaRow, SafeHtml> column = DataGridUtil.htmlColumnBuilder(
                        DataGridUtil.colouredCellExtractor(extractor, colourFunc))
                .rightAligned()
                .build();

        dataGrid.addResizableColumn(
                column,
                DataGridUtil.createRightAlignedHeader(name),
                size);
    }

    void addEndColumn() {
        dataGrid.addEndColumn(new EndColumn<>());
    }

    /**
     * @param onSetExpression Called after the expression has been successfully
     *                        validated and set on the criteria. Can be null.
     */
    public void setExpression(final ExpressionOperator expression, final Runnable onSetExpression) {
        validateExpression(expression, expression2 -> {
            this.criteria.setExpression(expression2);
            NullSafe.run(onSetExpression);
        });
    }

    public void info() {
        final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
        validateExpression(expression, exp -> {
            final FindMetaCriteria criteria = expressionToNonPagedCriteria(exp);
            showSummary(
                    criteria,
                    DocumentPermission.VIEW,
                    null,
                    null,
                    "Selection Summary",
                    false,
                    null);
        });
    }

    public void download() {
        confirmSelection("download", () -> {
            final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
            validateExpression(expression, exp -> {
                final FindMetaCriteria criteria = expressionToNonPagedCriteria(exp);
                showSummary(
                        criteria,
                        DocumentPermission.VIEW,
                        "downloaded",
                        "download",
                        "Confirm Download",
                        false,
                        () -> download(criteria));
            });
        });
    }

    public void process() {
        final Selection<Long> selection = getSelection();
        if (!selection.isMatchNothing()) {
            final ProcessChoicePresenter processChoicePresenter = processChoicePresenterProvider.get();
            final ProcessChoiceUiHandler processChoiceUiHandler = choice -> {
                if (choice.isReprocess()) {
                    doReprocess(choice);
                } else {
                    doProcess(choice);
                }
            };
            processChoicePresenter.show(selection, processChoiceUiHandler);
        } else {
            AlertEvent.fireError(
                    AbstractMetaListPresenter.this,
                    "You have not selected any items",
                    null);
        }
    }

    private void doProcess(final ProcessChoice processChoice) {
        choosePipeline(docRef -> {
            if (docRef != null) {
                confirmSelection("process", () -> {
                    final Selection<Long> selection = getSelection();
                    final ExpressionOperator expression = selectionToExpression(this.criteria, selection);
                    validateExpression(expression, exp -> {
                        final FindMetaCriteria criteria = expressionToNonPagedCriteria(exp);
                        showSummary(
                                criteria,
                                DocumentPermission.VIEW,
                                "processed",
                                "process",
                                "Confirm Process",
                                false,
                                () -> process(docRef, criteria, processChoice));
                    });
                });
            }
        });
    }

    private void doReprocess(final ProcessChoice processChoice) {
        confirmSelection("reprocess", () -> {
            final ExpressionOperator expression = selectionToExpression(
                    this.criteria, getSelection(), true);
            validateExpression(expression, MetaFields.getProcessorFilterFields(), expressionOperator -> {
                final FindMetaCriteria criteria = expressionToNonPagedCriteria(expressionOperator);
                showSummary(
                        criteria,
                        DocumentPermission.VIEW,
                        "reprocessed",
                        "reprocess",
                        "Confirm Reprocess",
                        true,
                        () -> reprocess(criteria, processChoice));
            });
        });
    }

    private void validateExpression(final ExpressionOperator expression,
                                    final Consumer<ExpressionOperator> consumer) {
        validateExpression(expression, MetaFields.getAllFields(), consumer);
    }

    private void validateExpression(final ExpressionOperator expression,
                                    final List<QueryField> validFields,
                                    final Consumer<ExpressionOperator> consumer) {
        expressionValidator.validateExpression(
                AbstractMetaListPresenter.this,
                validFields,
                expression,
                consumer,
                getView());
    }

    private void choosePipeline(final Consumer<DocRef> consumer) {
        final DocSelectionPopup chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Process Data With");
        chooser.setIncludedTypes(PipelineDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.VIEW);
        chooser.show(consumer);
    }

    /**
     * @return The effective criteria taking into account the active filter and any
     * row selections.
     */
    FindMetaCriteria getSelectedCriteria() {
        return expressionToNonPagedCriteria(selectionToExpression(this.criteria, getSelection()));
    }

    public void delete() {
        confirmSelection("delete", () -> {
            final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
            validateExpression(expression, exp -> {
                final FindMetaCriteria criteria = expressionToNonPagedCriteria(exp);
                showSummary(
                        criteria,
                        DocumentPermission.DELETE,
                        "deleted",
                        "delete",
                        "Confirm Delete",
                        false,
                        update(criteria, "Deleted", null, Status.DELETED));
            });
        });
    }

    public void restore() {
        confirmSelection("restore", () -> {
            final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
            validateExpression(expression, exp -> {
                final FindMetaCriteria criteria = expressionToNonPagedCriteria(exp);
                showSummary(
                        criteria,
                        DocumentPermission.EDIT,
                        "restored",
                        "restore",
                        "Confirm Restore",
                        false,
                        update(criteria, "Restored", Status.DELETED, Status.UNLOCKED));
            });
        });
    }

    private Runnable update(final FindMetaCriteria criteria,
                            final String text,
                            final Status currentStatus,
                            final Status newStatus) {
        return () -> {
            restFactory
                    .create(META_RESOURCE)
                    .method(res -> res.updateStatus(new UpdateStatusRequest(criteria, currentStatus, newStatus)))
                    .onSuccess(result ->
                            AlertEvent.fireInfo(
                                    AbstractMetaListPresenter.this,
                                    text + " " + result + " record" + ((result.longValue() > 1)
                                            ? "s"
                                            : ""),
                                    this::refresh))
                    .taskMonitorFactory(getView())
                    .exec();
        };
    }

    private void download(final FindMetaCriteria criteria) {
        restFactory
                .create(DATA_RESOURCE)
                .method(res -> res.download(criteria))
                .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                .taskMonitorFactory(getView())
                .exec();
    }

    private void process(final DocRef pipeline,
                         final FindMetaCriteria criteria,
                         final ProcessChoice processChoice) {
        final QueryData queryData = QueryData
                .builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(criteria.getExpression())
                .build();
        final CreateProcessFilterRequest request = CreateProcessFilterRequest
                .builder()
                .pipeline(pipeline)
                .queryData(queryData)
                .priority(processChoice.getPriority())
                .autoPriority(processChoice.isAutoPriority())
                .enabled(processChoice.isEnabled())
                .minMetaCreateTimeMs(processChoice.getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(processChoice.getMaxMetaCreateTimeMs())
                .build();

        restFactory
                .create(PROCESSOR_FILTER_RESOURCE)
                .method(res -> res.create(request))
                .onSuccess(processorFilter -> {
                    if (processorFilter != null) {
                        CreateProcessorEvent.fire(AbstractMetaListPresenter.this, processorFilter);
                    } else {
                        AlertEvent.fireInfo(this, "Created processor filter", null);
                    }
                })
                .taskMonitorFactory(getView())
                .exec();

    }

    private void reprocess(final FindMetaCriteria criteria,
                           final ProcessChoice processChoice) {
        final QueryData queryData = QueryData
                .builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(criteria.getExpression())
                .build();
        final CreateProcessFilterRequest request = CreateProcessFilterRequest
                .builder()
                .queryData(queryData)
                .priority(processChoice.getPriority())
                .autoPriority(processChoice.isAutoPriority())
                .reprocess(true)
                .enabled(processChoice.isEnabled())
                .minMetaCreateTimeMs(processChoice.getMinMetaCreateTimeMs())
                .maxMetaCreateTimeMs(processChoice.getMaxMetaCreateTimeMs())
                .build();

        restFactory
                .create(PROCESSOR_FILTER_RESOURCE)
                .method(res -> res.reprocess(request))
                .onSuccess(result -> {
                    if (result != null && result.size() > 0) {
                        Severity maxSeverity = null;

                        final StringBuilder sb = new StringBuilder();
                        for (final ReprocessDataInfo info : result) {
                            if (maxSeverity == null || info.getSeverity().greaterThan(maxSeverity)) {
                                maxSeverity = info.getSeverity();
                            }

                            sb.append(info.getSeverity().getDisplayValue());
                            sb.append(": ");
                            sb.append(info.getMessage());
                            if (info.getDetails() != null) {
                                sb.append("\n\t");
                                sb.append(info.getDetails());
                            }
                            sb.append("\n");
                        }

                        if (maxSeverity != null) {
                            switch (maxSeverity) {
                                case INFO:
                                    AlertEvent.fireInfo(
                                            AbstractMetaListPresenter.this,
                                            "Result Details",
                                            sb.toString().trim(),
                                            null);
                                    break;
                                case WARNING:
                                    AlertEvent.fireWarn(
                                            AbstractMetaListPresenter.this,
                                            "Result Details",
                                            sb.toString().trim(),
                                            null);
                                    break;
                                case ERROR:
                                case FATAL_ERROR:
                                    AlertEvent.fireError(
                                            AbstractMetaListPresenter.this,
                                            "Result Details",
                                            sb.toString().trim(),
                                            null);
                                    break;
                                default:
                                    throw new RuntimeException("Unknown severity " + maxSeverity);
                            }
                        }
                    }
                })
                .taskMonitorFactory(getView())
                .exec();

    }

    private void showSummary(final FindMetaCriteria criteria,
                             final DocumentPermission permission,
                             final String postAction,
                             final String action,
                             final String caption,
                             final boolean reprocess,
                             final Runnable runnable) {
        selectionSummaryPresenterProvider.get().show(
                criteria,
                permission,
                postAction,
                action,
                caption,
                reprocess,
                runnable);
    }

    private void confirmSelection(final String actionType, final Runnable runnable) {
        // TODO For select ALL we get three confirm dialogs, else two.
        //  Not sure we need the initial one, just the summary one with the useful data in it.
        final Selection<Long> selection = getSelection();
        if (!selection.isMatchNothing()) {
            ConfirmEvent.fire(this,
                    "Are you sure you want to " + actionType + " the selected items?",
                    confirm -> {
                        if (confirm) {
                            if (selection.isMatchAll()) {
                                ConfirmEvent.fireWarn(AbstractMetaListPresenter.this,
                                        "You have clicked the select all checkbox.  " +
                                        "If you continue Stroom will " +
                                        actionType + " all items that match the filter, not just those " +
                                        "visible on screen.\n\nAre you sure you want to continue?",
                                        confirm1 -> {
                                            if (confirm1) {
                                                runnable.run();
                                            }
                                        });
                            } else {
                                runnable.run();
                            }
                        }
                    });
        } else {
            AlertEvent.fireError(
                    AbstractMetaListPresenter.this,
                    "You have not selected any items",
                    null);
        }
    }

    private ExpressionOperator selectionToExpression(final FindMetaCriteria criteria,
                                                     final Selection<Long> selection) {
        return selectionToExpression(criteria, selection, false);
    }

    private ExpressionOperator selectionToExpression(final FindMetaCriteria criteria,
                                                     final Selection<Long> selection,
                                                     final boolean removeStatusTerms) {
//        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        // First make sure there is some sort of selection, either
        // individual streams have been selected or all streams have been
        // selected.
        if (selection.isMatchAll()) {
            if (NullSafe.nonNull(criteria, FindMetaCriteria::getExpression)) {
                final Predicate<ExpressionItem> termFilter = removeStatusTerms
                        ? AbstractMetaListPresenter::isNotStatusTerm
                        : null;
                return ExpressionUtil.copyOperator(criteria.getExpression(), termFilter);
            }
        } else if (selection.size() > 0) {
            // If we aren't matching all then create a criteria that
            // only includes the selected streams.
            return MetaExpressionUtil
                    .createDataIdSetExpression(selection.getSet());
        }
//        else {
        return null;
//        }
//
//        return builder;
    }

    private static boolean isNotStatusTerm(final ExpressionItem expressionItem) {
        final boolean isStatusTerm = expressionItem instanceof final ExpressionTerm term
                                     && MetaFields.STATUS.getFldName().equals(term.getField());
        return !isStatusTerm;
    }

    private FindMetaCriteria expressionToNonPagedCriteria(final ExpressionOperator expression) {
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        // Paging is NA
        criteria.obtainPageRequest().setLength(null);
        criteria.obtainPageRequest().setOffset(null);
        return criteria;
    }

    @Override
    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    MetaRow getSelected() {
        return selectionModel.getSelected();
    }

    @Override
    public com.google.web.bindery.event.shared.HandlerRegistration addDataSelectionHandler(
            final DataSelectionHandler<Selection<Long>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    public ButtonView add(final Preset preset) {
        return getView().addButton(preset);
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }
}
