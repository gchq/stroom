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

package stroom.data.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.LocationManager;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.ExpressionValidator;
import stroom.data.client.ProcessChoice;
import stroom.data.client.RestDataProvider;
import stroom.data.client.component.MetaList.Columns;
import stroom.data.shared.DataResource;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.ExplorerPopupController;
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
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.util.shared.Severity;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.Listeners;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// The data browser's streams ([MetaList]) that match an expression, a page at a time, and what can be
/// done with those chosen: their summary, download, process or reprocess, delete and restore. The
/// stepping screen's and the related streams' lists extend it.
public class MetaListController extends Controller {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);
    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final Selection<Long> selection = new Selection<>(false, new HashSet<>());
    private final RestFactory restFactory;
    private final LocationManager locationManager;
    private final DateTimeFormatter dateTimeFormatter;
    private final FindMetaCriteria criteria;
    private final RestDataProvider<MetaRow, ResultPage<MetaRow>> dataProvider;
    private final Provider<SelectionSummaryController> selectionSummaryControllerProvider;
    private final Provider<ProcessChoiceDialogController> processChoiceDialogProvider;
    private final Provider<ExplorerPopupController> pipelineSelection;
    private final ExpressionValidator expressionValidator;

    private final RefCellsController refCells;
    private final Columns columns;

    private MetaList view;
    private ResultPage<MetaRow> resultPage;
    private boolean initialised;

    private Consumer<ResultPage<MetaRow>> onResult = resultPage -> {
    };

    private final Listeners<Selection<Long>> selectionListeners = new Listeners<>();

    /// Made by GIN, for the data browser's list of streams; [#attach(MetaList)] gives it its list.
    ///
    /// @param eventBus                           The application's event bus.
    /// @param restFactory                        Finds and changes the streams.
    /// @param locationManager                    Opens a download.
    /// @param dateTimeFormatter                  Formats the times shown.
    /// @param selectionSummaryControllerProvider Makes the summary of the streams chosen.
    /// @param processChoiceDialogProvider        Makes the dialog that chooses how to process them.
    /// @param pipelineSelection                  Makes the popup that picks a pipeline.
    /// @param expressionValidator                Checks an expression with the server.
    /// @param refCells                           Lets the feed and pipeline cells open them.
    @Inject
    public MetaListController(final EventBus eventBus,
                              final RestFactory restFactory,
                              final LocationManager locationManager,
                              final DateTimeFormatter dateTimeFormatter,
                              final Provider<SelectionSummaryController> selectionSummaryControllerProvider,
                              final Provider<ProcessChoiceDialogController> processChoiceDialogProvider,
                              final Provider<ExplorerPopupController> pipelineSelection,
                              final ExpressionValidator expressionValidator,
                              final RefCellsController refCells) {
        this(eventBus,
                restFactory,
                locationManager,
                dateTimeFormatter,
                selectionSummaryControllerProvider,
                processChoiceDialogProvider,
                pipelineSelection,
                expressionValidator,
                refCells,
                Columns.SELECTABLE);
    }

    /// Made by a list that extends this, with the columns it shows.
    ///
    /// @param eventBus                           The application's event bus.
    /// @param restFactory                        Finds and changes the streams.
    /// @param locationManager                    Opens a download.
    /// @param dateTimeFormatter                  Formats the times shown.
    /// @param selectionSummaryControllerProvider Makes the summary of the streams chosen.
    /// @param processChoiceDialogProvider        Makes the dialog that chooses how to process them.
    /// @param pipelineSelection                  Makes the popup that picks a pipeline.
    /// @param expressionValidator                Checks an expression with the server.
    /// @param refCells                           Lets the feed and pipeline cells open them.
    /// @param columns                            Which of the optional columns to show.
    protected MetaListController(final EventBus eventBus,
                                 final RestFactory restFactory,
                                 final LocationManager locationManager,
                                 final DateTimeFormatter dateTimeFormatter,
                                 final Provider<SelectionSummaryController> selectionSummaryControllerProvider,
                                 final Provider<ProcessChoiceDialogController> processChoiceDialogProvider,
                                 final Provider<ExplorerPopupController> pipelineSelection,
                                 final ExpressionValidator expressionValidator,
                                 final RefCellsController refCells,
                                 final Columns columns) {
        super(eventBus);
        this.refCells = own(refCells);
        this.columns = columns;
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        this.dateTimeFormatter = dateTimeFormatter;
        this.selectionSummaryControllerProvider = selectionSummaryControllerProvider;
        this.processChoiceDialogProvider = processChoiceDialogProvider;
        this.pipelineSelection = pipelineSelection;
        this.expressionValidator = expressionValidator;

        selection.setMatchAll(false);
        criteria = new FindMetaCriteria();
        dataProvider = new RestDataProvider<>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<MetaRow>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                if (criteria.getExpression() != null) {
                    CriteriaUtil.setRange(criteria, range);
                    final ColumnSortList columnSortList = view.getSortList();
                    final List<CriteriaFieldSort> sortList = CriteriaUtil.createSortList(columnSortList);
                    // Add the meta id to the sort list otherwise we are never guaranteed rows returned
                    // in same order. It has to be appended to the list actually sent, AFTER the user's
                    // own sorts, so that it only ever breaks ties: two rows with an equal Create Time
                    // would otherwise be free to swap between pages.
                    // Break the ties in the same direction as the sort we are breaking them for. Either
                    // direction gives us the total order we need, but an index can only provide rows in its
                    // own order or the exact reverse of it, so mixing the directions means the database has
                    // to sort the whole result set to answer what is usually just the first page of it.
                    if (!CriteriaUtil.hasSortColumn(columnSortList, MetaFields.FIELD_ID)) {
                        final boolean isDescending = !sortList.isEmpty()
                                                     && sortList.get(sortList.size() - 1).isDesc();
                        sortList.add(new CriteriaFieldSort(MetaFields.FIELD_ID, isDescending, false));
                    }
                    criteria.setSortList(sortList);
                    restFactory
                            .create(META_RESOURCE)
                            .method(res -> res.findMetaRow(criteria))
                            .onSuccess(resultSet -> {
                                dataConsumer.accept(resultSet);
                                onResult.accept(resultSet);
                            })
                            .onFailure(errorHandler)
                            .owner(MetaListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
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

    /// Attaches this to a list, e.g. one a template declares, which it fills from the server. The
    /// list is then this controller's widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public MetaListController attach(final MetaList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.setSelection(selection);
        view.addColumns(columns,
                dateTimeFormatter::format,
                refCells.configure(new FeedRefCell.Builder<>()),
                refCells.configure(new DocRefCell.Builder<>()),
                this::buildExpander);
        view.setOnSelectAll(this::setMatchAll);
        view.setOnSelectionChange(() -> selectionListeners.fire(selection));
        view.setOnSortChange(this::refresh);
        return this;
    }

    /// @param tableName The list's name, for assistive technology.
    protected void setTableName(final String tableName) {
        view.setTableName(tableName);
    }

    /// @return The list, for a subclass.
    protected MetaList getView() {
        return view;
    }

    /// Gives a stream's place in the tree of streams, for a list with [Columns#LINEAGE].
    ///
    /// @param row A stream.
    /// @return Its place, or `null` when the list has no tree.
    protected Expander buildExpander(final MetaRow row) {
        return null;
    }

    /// @param onResult What to run with each page of data the list fetches.
    public void setOnResult(final Consumer<ResultPage<MetaRow>> onResult) {
        this.onResult = onResult;
    }

    /// Keeps the streams chosen that are still shown, as the rows change, and tells the listeners.
    ///
    /// @param data A page of streams fetched.
    /// @return The page to show.
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
        selectionListeners.fire(selection);

        final MetaRow selected = getSelectionModel().getSelected();
        if (selected != null) {
            if (!resultPage.getValues().contains(selected)) {
                getSelectionModel().setSelected(selected, false);
            }
        }

        return data;
    }

    private void setMatchAll(final boolean select) {
        selection.clear();
        selection.setMatchAll(select);
        if (dataProvider != null) {
            dataProvider.updateRowData(dataProvider.getRanges()[0].getStart(), resultPage.getValues());
        }
        selectionListeners.fire(selection);
    }

    /// @return The selection, one stream at most.
    public MultiSelectionModel<MetaRow> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// @return The streams chosen with the tick boxes.
    public Selection<Long> getSelection() {
        return selection;
    }

    /// @return The page of streams last fetched, or `null`.
    public ResultPage<MetaRow> getResultPage() {
        return resultPage;
    }

    /// Sets an expression this client built itself, with no validation round-trip.
    ///
    /// [#setExpression] validates because a user-edited filter can be wrong, and the server's
    /// message is the feedback. A seed expression built from a [stroom.docref.DocRef] by
    /// `MetaExpressionUtil` cannot be wrong, so validating one spends a
    /// `POST /expression/v1/validate` on this client's own output before the `meta/find` it was
    /// always going to run. `ExpressionValidator` already makes exactly this exemption for
    /// `ALL_UNLOCKED_EXPRESSION`; this extends it to the rest of the seeds.
    ///
    /// @param expression      The expression.
    /// @param onSetExpression Called after the expression has been set on the criteria. Can be null.
    public void setSeedExpression(final ExpressionOperator expression, final Runnable onSetExpression) {
        this.criteria.setExpression(expression);
        NullSafe.run(onSetExpression);
    }

    /// Sets an expression the user built, once the server has validated it.
    ///
    /// @param expression      The expression.
    /// @param onSetExpression Called after the expression has been successfully validated and set
    ///                        on the criteria. Can be null.
    public void setExpression(final ExpressionOperator expression, final Runnable onSetExpression) {
        validateExpression(expression, expression2 -> {
            this.criteria.setExpression(expression2);
            NullSafe.run(onSetExpression);
        });
    }

    /// Shows a summary of the streams chosen.
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

    /// Downloads the streams chosen, once confirmed.
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

    /// Processes or reprocesses the streams chosen, as the user chooses.
    public void process() {
        final Selection<Long> selection = getSelection();
        if (!selection.isMatchNothing()) {
            processChoiceDialogProvider.get().show(this, selection, choice -> {
                if (choice.isReprocess()) {
                    doReprocess(choice);
                } else {
                    doProcess(choice);
                }
            });
        } else {
            AlertEvent.fireError(
                    this,
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
                this,
                validFields,
                expression,
                consumer,
                view.getTaskMonitor());
    }

    private void choosePipeline(final Consumer<DocRef> consumer) {
        final ExplorerPopupController chooser = pipelineSelection.get();
        chooser.setIncludeNullSelection(false);
        chooser.setCaption("Choose Pipeline To Process Data With");
        chooser.setIncludedTypes(PipelineDoc.TYPE);
        chooser.setRequiredPermissions(DocumentPermission.VIEW);
        chooser.showOnce(this, consumer);
    }

    /// @return The effective criteria taking into account the active filter and any row selections.
    public FindMetaCriteria getSelectedCriteria() {
        return expressionToNonPagedCriteria(selectionToExpression(this.criteria, getSelection()));
    }

    /// Deletes the streams chosen, once confirmed.
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

    /// Restores the deleted streams chosen, once confirmed.
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
                                    this,
                                    text + " " + result + " record" + ((result.longValue() > 1)
                                            ? "s"
                                            : ""),
                                    this::refresh))
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        };
    }

    private void download(final FindMetaCriteria criteria) {
        restFactory
                .create(DATA_RESOURCE)
                .method(res -> res.download(criteria))
                .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, this, result))
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
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
                        CreateProcessorEvent.fire(this, processorFilter);
                    } else {
                        AlertEvent.fireInfo(this, "Created processor filter", null);
                    }
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
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
                                            MetaListController.this,
                                            "Result Details",
                                            sb.toString().trim(),
                                            null);
                                    break;
                                case WARNING:
                                    AlertEvent.fireWarn(
                                            MetaListController.this,
                                            "Result Details",
                                            sb.toString().trim(),
                                            null);
                                    break;
                                case ERROR:
                                case FATAL_ERROR:
                                    AlertEvent.fireError(
                                            MetaListController.this,
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
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();

    }

    private void showSummary(final FindMetaCriteria criteria,
                             final DocumentPermission permission,
                             final String postAction,
                             final String action,
                             final String caption,
                             final boolean reprocess,
                             final Runnable runnable) {
        selectionSummaryControllerProvider.get().show(this,
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
                                ConfirmEvent.fireWarn(this,
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
                    this,
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
        // First make sure there is some sort of selection, either
        // individual streams have been selected or all streams have been
        // selected.
        if (selection.isMatchAll()) {
            if (NullSafe.nonNull(criteria, FindMetaCriteria::getExpression)) {
                final Predicate<ExpressionItem> termFilter = removeStatusTerms
                        ? MetaListController::isNotStatusTerm
                        : null;
                return ExpressionUtil.copyOperator(criteria.getExpression(), termFilter);
            }
        } else if (selection.size() > 0) {
            // If we aren't matching all then create a criteria that
            // only includes the selected streams.
            return MetaExpressionUtil
                    .createDataIdSetExpression(selection.getSet());
        }
        return null;
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

    /// Finds the streams again; the first time, starts the list.
    public void refresh() {
        if (!initialised) {
            initialised = true;
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// @return The selected stream, or `null`.
    public MetaRow getSelected() {
        return getSelectionModel().getSelected();
    }

    /// Listens for the user selecting streams, until the registration is removed.
    ///
    /// @param listener Told what is selected.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<Selection<Long>> listener) {
        return selectionListeners.add(listener);
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// @return The criteria the streams are found by.
    public FindMetaCriteria getCriteria() {
        return criteria;
    }
}
