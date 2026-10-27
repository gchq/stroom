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

import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Processor tasks counted by pipeline, feed, priority and status ([ProcessorTaskSummaryList]),
/// of a pipeline, feed or folder, or matching an expression; the row selected kept selected as
/// its count changes.
public class ProcessorTaskSummaryListController extends Controller implements HasDocumentRead<Object> {

    private static final ProcessorTaskResource PROCESSOR_TASK_RESOURCE = GWT.create(ProcessorTaskResource.class);

    private final RestFactory restFactory;
    private final ProcessorTaskSummaryList view;
    private RestDataProvider<ProcessorTaskSummary, ResultPage<ProcessorTaskSummary>> dataProvider;
    private final ExpressionCriteria criteria;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             The content.
    /// @param restFactory      Counts the tasks.
    /// @param tooltipPresenter Shows a row's info.
    /// @param refCells         Lets the pipeline and feed cells open them.
    @Inject
    public ProcessorTaskSummaryListController(final EventBus eventBus,
                                              final ProcessorTaskSummaryList view,
                                              final RestFactory restFactory,
                                              final TooltipPresenter tooltipPresenter,
                                              final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        own(refCells);
        own(tooltipPresenter);
        this.restFactory = restFactory;
        initWidget(view);

        criteria = new ExpressionCriteria();
        view.addColumns(refCells.configure(new DocRefCell.Builder<ProcessorTaskSummary>()),
                refCells.configure(new FeedRefCell.Builder<ProcessorTaskSummary>()));
        view.setOnShowInfo((row, popupPosition, anchor) ->
                tooltipPresenter.show(info(row), popupPosition, anchor));
        view.setOnSortChange(this::refresh);
    }

    // A row's info: what it counts
    private static SafeHtml info(final ProcessorTaskSummary row) {
        final TableBuilder tb = new TableBuilder();
        tb.row(TableCell.header("Key Data", 2));
        final DocRef pipeline = row.getPipeline();
        if (pipeline != null) {
            tb.row("Pipeline", DocRefUtil.createSimpleDocRefString(pipeline));
        }

        tb
                .row("Feed", row.getFeed())
                .row("Priority", String.valueOf(row.getPriority()))
                .row("Status", String.valueOf(row.getStatus()));

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    /// @return The selection, one row at most.
    public MultiSelectionModel<ProcessorTaskSummary> getSelectionModel() {
        return view.getSelectionModel();
    }

    private void setPipeline(final DocRef pipeline) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createPipelineExpression(pipeline));
    }

    private void setFeed(final DocRef feed) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createFeedExpression(feed));
    }

    private void setFolder(final DocRef folder) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createFolderExpression(folder));
    }

    private void setNullCriteria() {
        criteria.setExpression(null);
    }

    @Override
    public void read(final DocRef docRef, final Object document) {
        if (document instanceof PipelineDoc) {
            setPipeline(docRef);
        } else if (document instanceof FeedDoc) {
            setFeed(docRef);
        } else if (docRef != null) {
            setFolder(docRef);
        } else {
            setNullCriteria();
        }

        refresh();
    }

    /// Counts the tasks again; the first time, starts the list.
    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<ProcessorTaskSummary, ResultPage<ProcessorTaskSummary>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ProcessorTaskSummary>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    CriteriaUtil.setRange(criteria, range);
                    CriteriaUtil.setSortList(criteria, view.getSortList());
                    restFactory
                            .create(PROCESSOR_TASK_RESOURCE)
                            .method(res -> res.findSummary(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .owner(ProcessorTaskSummaryListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }

                @Override
                protected void changeData(final ResultPage<ProcessorTaskSummary> data) {
                    final MultiSelectionModel<ProcessorTaskSummary> selectionModel = view.getSelectionModel();
                    final ProcessorTaskSummary selected = selectionModel.getSelected();
                    if (selected != null) {
                        // Reselect the task set: the row of the same group (its key), whose count
                        // may have changed, so it isn't equal to the one selected
                        selectionModel.clear();
                        final Object key = view.getKeyProvider().getKey(selected);
                        if (data != null) {
                            data.getValues().stream()
                                    .filter(row -> key.equals(view.getKeyProvider().getKey(row)))
                                    .findFirst()
                                    .ifPresent(selectionModel::setSelected);
                        }
                    }
                    super.changeData(data);
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// Counts the tasks matching an expression.
    ///
    /// @param expressionOperator The expression.
    public void setExpression(final ExpressionOperator expressionOperator) {
        criteria.setExpression(expressionOperator);
        refresh();
    }
}
