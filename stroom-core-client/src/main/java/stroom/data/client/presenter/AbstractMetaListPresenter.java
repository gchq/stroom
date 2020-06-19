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

package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.core.client.LocationManager;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.shared.DataResource;
import stroom.data.table.client.Refreshable;
import stroom.datasource.api.v2.AbstractField;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaInfoSection;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.meta.shared.UpdateStatusRequest;
import stroom.pipeline.client.event.CreateProcessorEvent;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.CreateReprocessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.ReprocessDataInfo;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionUtil;
import stroom.security.shared.DocumentPermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;
import stroom.util.shared.Severity;
import stroom.util.shared.Sort;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import javax.inject.Provider;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractMetaListPresenter extends MyPresenterWidget<DataGridView<MetaRow>> implements HasDataSelectionHandlers<Selection<Long>>, Refreshable {
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);
    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final TooltipPresenter tooltipPresenter;

    private final Selection<Long> selection = new Selection<>(false, new HashSet<>());
    private final RestFactory restFactory;
    private final LocationManager locationManager;
    private final FindMetaCriteria criteria;
    private final RestDataProvider<MetaRow, ResultPage<MetaRow>> dataProvider;
    private final Provider<SelectionSummaryPresenter> selectionSummaryPresenterProvider;
    private final Provider<ProcessChoicePresenter> processChoicePresenterProvider;
    private final Provider<EntityChooser> pipelineSelection;

    private ResultPage<MetaRow> resultPage;
    private boolean initialised;

    AbstractMetaListPresenter(final EventBus eventBus,
                              final RestFactory restFactory,
                              final TooltipPresenter tooltipPresenter,
                              final LocationManager locationManager,
                              final Provider<SelectionSummaryPresenter> selectionSummaryPresenterProvider,
                              final Provider<ProcessChoicePresenter> processChoicePresenterProvider,
                              final Provider<EntityChooser> pipelineSelection,
                              final boolean allowSelectAll) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.tooltipPresenter = tooltipPresenter;
        this.restFactory = restFactory;
        this.locationManager = locationManager;
        this.selectionSummaryPresenterProvider = selectionSummaryPresenterProvider;
        this.processChoicePresenterProvider = processChoicePresenterProvider;
        this.pipelineSelection = pipelineSelection;

        selection.setMatchAll(false);
        addColumns(allowSelectAll);

        criteria = new FindMetaCriteria();
        criteria.setSort(MetaFields.CREATE_TIME.getName(), Sort.Direction.DESCENDING, false);

        final PageRequest pageRequest = criteria.obtainPageRequest();
        pageRequest.setOffset(0L);
        pageRequest.setLength(PageRequest.DEFAULT_PAGE_SIZE);
        dataProvider = new RestDataProvider<MetaRow, ResultPage<MetaRow>>(eventBus, pageRequest) {
            @Override
            protected void exec(final Consumer<ResultPage<MetaRow>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                if (criteria.getExpression() != null) {
                    final Rest<ResultPage<MetaRow>> rest = restFactory.create();
                    rest
                            .onSuccess(dataConsumer)
                            .onFailure(throwableConsumer)
                            .call(META_RESOURCE)
                            .findMetaRow(criteria);
                } else {
                    dataConsumer.accept(new ResultPage<>(Collections.emptyList()));
                }
            }

            @Override
            protected void changeData(final ResultPage<MetaRow> data) {
                super.changeData(onProcessData(data));
            }
        };

        getView().addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                if (event.isSortAscending()) {
                    criteria.setSort(orderByColumn.getField(), Sort.Direction.ASCENDING, orderByColumn.isIgnoreCase());
                } else {
                    criteria.setSort(orderByColumn.getField(), Sort.Direction.DESCENDING, orderByColumn.isIgnoreCase());
                }
                refresh();
            }
        });
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

        MetaRow selected = getView().getSelectionModel().getSelected();
        if (selected != null) {
            if (!resultPage.getValues().contains(selected)) {
                getView().getSelectionModel().setSelected(selected, false);
            }
        }

        return data;
    }

    protected abstract void addColumns(boolean allowSelectAll);

    void addSelectedColumn(final boolean allowSelectAll) {
        final TickBoxCell.MarginAppearance tickBoxAppearance = GWT.create(TickBoxCell.MarginAppearance.class);

        // Select Column
        final Column<MetaRow, TickBoxState> column = new Column<MetaRow, TickBoxState>(
                TickBoxCell.create(tickBoxAppearance, false, false)) {
            @Override
            public TickBoxState getValue(final MetaRow object) {
                return TickBoxState.fromBoolean(selection.isMatch(object.getMeta().getId()));
            }
        };
        if (allowSelectAll) {
            final Header<TickBoxState> header = new Header<TickBoxState>(
                    TickBoxCell.create(tickBoxAppearance, false, false)) {
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
            getView().addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

            header.setUpdater(value -> {
                if (value.equals(TickBoxState.UNTICK)) {
                    selection.clear();
                    selection.setMatchAll(false);
                }
                if (value.equals(TickBoxState.TICK)) {
                    selection.clear();
                    selection.setMatchAll(true);
                }
                if (dataProvider != null) {
                    dataProvider.updateRowData(dataProvider.getRanges()[0].getStart(), resultPage.getValues());
                }
                DataSelectionEvent.fire(AbstractMetaListPresenter.this, selection, false);
            });

        } else {
            getView().addColumn(column, "", ColumnSizeConstants.CHECKBOX_COL);
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
            getView().redrawHeaders();
            DataSelectionEvent.fire(AbstractMetaListPresenter.this, selection, false);
        });
    }

    private SvgPreset getInfoCellState(final MetaRow object) {
        // Should only show unlocked ones by default
        final Status status = object.getMeta().getStatus();
        if (Status.UNLOCKED.equals(status)) {
            return SvgPresets.INFO.title("Info (Unlocked)");
        } else if (Status.DELETED.equals(status)) {
            return SvgPresets.INFO_DELETED.title("Info (Deleted)");
        } else if (Status.LOCKED.equals(status)) {
            return SvgPresets.INFO_WARNING.title("Info (Locked)");
        } else {
            throw new RuntimeException("Unknown status " + status);
        }
    }

    void addInfoColumn() {
        // Info column.
        final InfoColumn<MetaRow> infoColumn = new InfoColumn<MetaRow>() {
            @Override
            public SvgPreset getValue(final MetaRow object) {
                return getInfoCellState(object);
            }

            @Override
            protected void showInfo(final MetaRow row, final int x, final int y) {
                final Rest<List<MetaInfoSection>> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            final TooltipUtil.Builder builder = TooltipUtil.builder();

                            for (int i = 0; i < result.size(); i++) {
                                final MetaInfoSection section = result.get(i);
                                builder.addHeading(section.getTitle());
                                section.getEntries()
                                        .forEach(entry ->
                                                builder.addLine(entry.getKey(), entry.getValue()));
                                if (i < result.size() - 1) {
                                    builder.addBreak();
                                }
                            }

                            tooltipPresenter.setHTML(builder.build());
                            final PopupPosition popupPosition = new PopupPosition(x, y);
                            ShowPopupEvent.fire(
                                    AbstractMetaListPresenter.this,
                                    tooltipPresenter,
                                    PopupType.POPUP,
                                    popupPosition,
                                    null);
                        })
                        .call(META_RESOURCE)
                        .fetchFullMetaInfo(row.getMeta().getId());
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    void addCreatedColumn() {
        // Created.
        getView().addResizableColumn(new OrderByColumn<MetaRow, String>(new TextCell(), MetaFields.CREATE_TIME) {
            @Override
            public String getValue(final MetaRow row) {
                return ClientDateUtil.toISOString(row.getMeta().getCreateMs());
            }
        }, "Created", ColumnSizeConstants.DATE_COL);
    }

//    void addEffectiveColumn() {
//        // Effective.
//        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
//            @Override
//            public String getValue(final StreamAttributeMap row) {
//                return ClientDateUtil.toISOString(row.getMeta().getEffectiveMs());
//            }
//        }, "Effective", ColumnSizeConstants.DATE_COL);
//    }

    void addFeedColumn() {
        // if (securityContext.hasAppPermission(Feed.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new OrderByColumn<MetaRow, String>(new TextCell(), MetaFields.FEED_NAME) {
            @Override
            public String getValue(final MetaRow row) {
                if (row != null && row.getMeta() != null && row.getMeta().getFeedName() != null) {
                    return row.getMeta().getFeedName();
                }
                return "";
            }
        }, "Feed", ColumnSizeConstants.BIG_COL);
        // }
    }

    void addStreamTypeColumn() {
        // if (securityContext.hasAppPermission(StreamType.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new OrderByColumn<MetaRow, String>(new TextCell(), MetaFields.TYPE_NAME) {
            @Override
            public String getValue(final MetaRow row) {
                if (row != null && row.getMeta() != null && row.getMeta().getTypeName() != null) {
                    return row.getMeta().getTypeName();
                }
                return "";
            }
        }, "Type", 80);
        // }
    }

    void addPipelineColumn() {
        // if (securityContext.hasAppPermission(PipelineEntity.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<MetaRow, String>(new TextCell()) {
            @Override
            public String getValue(final MetaRow row) {
                if (row.getMeta().getProcessorUuid() != null) {
                    if (row.getPipelineName() != null) {
                        return row.getPipelineName();
                    } else {
                        return "Not visible";
                    }
                }
                return "";

            }
        }, "Pipeline", ColumnSizeConstants.BIG_COL);
        // }
    }

    protected MultiSelectionModel<MetaRow> getSelectionModel() {
        return getView().getSelectionModel();
    }

    Selection<Long> getSelection() {
        return selection;
    }

    private Set<Long> getResultStreamIdSet() {
        final HashSet<Long> rtn = new HashSet<>();
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

    void addAttributeColumn(final String name, final AbstractField attribute, final Function<String, String> formatter, final int size) {
        final Column<MetaRow, String> column = new Column<MetaRow, String>(new TextCell()) {
            @Override
            public String getValue(final MetaRow row) {
                final String value = row.getAttributeValue(attribute.getName());
                if (value == null) {
                    return null;
                }
                return formatter.apply(value);
            }
        };
        getView().addResizableColumn(column, name, size);
    }

    public void setExpression(final ExpressionOperator expression) {
        this.criteria.setExpression(expression);
        this.criteria.obtainPageRequest().setOffset(0L);
        this.criteria.obtainPageRequest().setLength(PageRequest.DEFAULT_PAGE_SIZE);
        refresh();
    }

    public void download() {
        validateSelection("download", () -> {
            final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
            if (expression != null) {
                final FindMetaCriteria criteria = expressionToNonPagedCriteria(expression);
                showSummary(
                        criteria,
                        "downloaded",
                        "download",
                        "Confirm Download",
                        false,
                        () -> download(criteria));
            }
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
            processChoicePresenter.show(processChoiceUiHandler);
        } else {
            AlertEvent.fireError(AbstractMetaListPresenter.this, "You have not selected any items", null);
        }
    }

    private void doProcess(final ProcessChoice processChoice) {
        choosePipeline(docRef -> {
            if (docRef != null) {
                validateSelection("process", () -> {
                    final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
                    if (expression != null) {
                        final FindMetaCriteria criteria = expressionToNonPagedCriteria(expression);
                        showSummary(
                                criteria,
                                "processed",
                                "process",
                                "Confirm Process",
                                false,
                                () -> process(docRef, criteria, processChoice));
                    }
                });
            }
        });
    }

    private void doReprocess(final ProcessChoice processChoice) {
        validateSelection("reprocess", () -> {
            final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
            if (expression != null) {
                final FindMetaCriteria criteria = expressionToNonPagedCriteria(expression);
                showSummary(
                        criteria,
                        "reprocessed",
                        "reprocess",
                        "Confirm Reprocess",
                        true,
                        () -> reprocess(criteria, processChoice));
            }
        });
    }

    private void choosePipeline(final Consumer<DocRef> consumer) {
        final EntityChooser chooser = pipelineSelection.get();
        chooser.setCaption("Choose Pipeline To Process Data With");
        chooser.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        chooser.setRequiredPermissions(DocumentPermissionNames.READ);
        chooser.addDataSelectionHandler(event -> {
            final DocRef pipeline = chooser.getSelectedEntityReference();
            consumer.accept(pipeline);
        });
        chooser.show();
    }

    public void delete() {
        validateSelection("delete", () -> {
            final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
            if (expression != null) {
                final Builder not = new ExpressionOperator.Builder(ExpressionOperator.Op.NOT);
                not.addTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.DELETED.getDisplayValue());

                final Builder builder = new ExpressionOperator.Builder();
                builder.addOperator(expression);
                builder.addOperator(not.build());

                final FindMetaCriteria criteria = expressionToNonPagedCriteria(builder.build());
                showSummary(
                        criteria,
                        "deleted",
                        "delete",
                        "Confirm Delete",
                        false,
                        update(criteria, "Deleted", null, Status.DELETED));
            }
        });
    }

    public void restore() {
        validateSelection("restore", () -> {
            final ExpressionOperator expression = selectionToExpression(this.criteria, getSelection());
            if (expression != null) {
                final Builder builder = new ExpressionOperator.Builder();
                builder.addOperator(expression);
                builder.addTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.DELETED.getDisplayValue());

                final FindMetaCriteria criteria = expressionToNonPagedCriteria(builder.build());
                showSummary(
                        criteria,
                        "restored",
                        "restore",
                        "Confirm Restore",
                        false,
                        update(criteria, "Restored", Status.DELETED, Status.UNLOCKED));
            }
        });
    }

    private Runnable update(final FindMetaCriteria criteria, final String text, final Status currentStatus, final Status newStatus) {
        return () -> {
            final Rest<Integer> rest = restFactory.create();
            rest
                    .onSuccess(result ->
                            AlertEvent.fireInfo(AbstractMetaListPresenter.this,
                                    text + " " + result + " record" + ((result.longValue() > 1) ? "s" : ""), this::refresh))
                    .call(META_RESOURCE)
                    .updateStatus(new UpdateStatusRequest(criteria, currentStatus, newStatus));
        };
    }

    private void download(final FindMetaCriteria criteria) {
        final Rest<ResourceGeneration> rest = restFactory.create();
        rest
                .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, null, result))
                .call(DATA_RESOURCE)
                .download(criteria);
    }

    private void process(final DocRef pipeline,
                         final FindMetaCriteria criteria,
                         final ProcessChoice processChoice) {
        final QueryData queryData = new QueryData(MetaFields.STREAM_STORE_DOC_REF, criteria.getExpression(), null);
        final CreateProcessFilterRequest request = new CreateProcessFilterRequest(
                pipeline,
                queryData,
                processChoice.getPriority(),
                processChoice.isAutoPriority(),
                processChoice.isEnabled());

        final Rest<ProcessorFilter> rest = restFactory.create();
        rest
                .onSuccess(processorFilter -> {
                    if (processorFilter != null) {
                        CreateProcessorEvent.fire(AbstractMetaListPresenter.this, processorFilter);
                    } else {
                        AlertEvent.fireInfo(this, "Created processor filter", null);
                    }
                })
                .call(PROCESSOR_FILTER_RESOURCE)
                .create(request);

    }

    private void reprocess(final FindMetaCriteria criteria,
                           final ProcessChoice processChoice) {
        final QueryData queryData = new QueryData(MetaFields.STREAM_STORE_DOC_REF, criteria.getExpression(), null);
        final CreateReprocessFilterRequest request = new CreateReprocessFilterRequest(
                queryData,
                processChoice.getPriority(),
                processChoice.isAutoPriority(),
                processChoice.isEnabled());

        final Rest<List<ReprocessDataInfo>> rest = restFactory.create();
        rest
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
                                    AlertEvent.fireInfo(AbstractMetaListPresenter.this, "Result Details",
                                            sb.toString().trim(), null);
                                    break;
                                case WARNING:
                                    AlertEvent.fireWarn(AbstractMetaListPresenter.this, "Result Details",
                                            sb.toString().trim(), null);
                                    break;
                                case ERROR:
                                    AlertEvent.fireError(AbstractMetaListPresenter.this, "Result Details",
                                            sb.toString().trim(), null);
                                    break;
                                case FATAL_ERROR:
                                    AlertEvent.fireError(AbstractMetaListPresenter.this, "Result Details",
                                            sb.toString().trim(), null);
                                    break;
                            }
                        }
                    }
                })
                .call(PROCESSOR_FILTER_RESOURCE)
                .reprocess(request);

    }

    private void showSummary(final FindMetaCriteria criteria,
                             final String postAction,
                             final String action,
                             final String caption,
                             final boolean reprocess,
                             final Runnable runnable) {
        selectionSummaryPresenterProvider.get().show(
                criteria,
                postAction,
                action,
                caption,
                reprocess,
                runnable);
    }

    private void validateSelection(final String actionType, final Runnable runnable) {
        final Selection<Long> selection = getSelection();
        if (!selection.isMatchNothing()) {
            ConfirmEvent.fire(this,
                    "Are you sure you want to " + actionType + " the selected items?",
                    confirm -> {
                        if (confirm) {
                            if (selection.isMatchAll()) {
                                ConfirmEvent.fireWarn(AbstractMetaListPresenter.this,
                                        "You have selected all items.  Are you sure you want to " + actionType + " all the selected items?",
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
            AlertEvent.fireError(AbstractMetaListPresenter.this, "You have not selected any items", null);
        }
    }

    private ExpressionOperator selectionToExpression(final FindMetaCriteria criteria, final Selection<Long> selection) {
//        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder();
        // First make sure there is some sort of selection, either
        // individual streams have been selected or all streams have been
        // selected.
        if (selection.isMatchAll()) {
            if (criteria != null && criteria.getExpression() != null) {
//                builder.addOperator(criteria.getExpression());
                return ExpressionUtil.copyOperator(criteria.getExpression());
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
            dataProvider.addDataDisplay(getView().getDataDisplay());
        } else {
            dataProvider.refresh();
        }
    }

    MetaRow getSelected() {
        return getView().getSelectionModel().getSelected();
    }

    @Override
    public com.google.web.bindery.event.shared.HandlerRegistration addDataSelectionHandler(
            final DataSelectionHandler<Selection<Long>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    public ButtonView add(final SvgPreset preset) {
        return getView().addButton(preset);
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }
}
