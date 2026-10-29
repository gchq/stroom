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

import stroom.data.client.CriteriaUtil;
import stroom.data.client.OpenLinkUtil;
import stroom.data.client.OpenLinkUtil.LinkType;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.preferences.client.DateTimeFormatter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.tooltip.client.component.InfoTooltipController;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/// Processor tasks ([ProcessorTasksList]) of a pipeline, feed or folder, or matching an
/// expression, a page at a time; a task's info (with its stream's) shown in a tooltip.
public class ProcessorTasksListController extends Controller {

    private static final ProcessorTaskResource PROCESSOR_TASK_RESOURCE = GWT.create(ProcessorTaskResource.class);
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final RestFactory restFactory;
    private final InfoTooltipController infoTooltip;
    private final DateTimeFormatter dateTimeFormatter;
    private final ExpressionCriteria criteria;
    private final RefCellsController refCells;
    private ProcessorTasksList view;
    private RestDataProvider<ProcessorTask, ResultPage<ProcessorTask>> dataProvider;
    // Counts the Info clicks, so that only the latest click's tooltip is shown
    private int infoRequestCount;

    /// Made by GIN; [#attach(ProcessorTasksList)] gives it its list.
    ///
    /// @param eventBus          The application's event bus.
    /// @param restFactory       Finds the tasks, and a task's stream for its info.
    /// @param infoTooltip       Shows a task's info.
    /// @param dateTimeFormatter Formats times in the user's preferences.
    /// @param refCells          Lets the feed and pipeline cells open them.
    @Inject
    public ProcessorTasksListController(final EventBus eventBus,
                                        final RestFactory restFactory,
                                        final InfoTooltipController infoTooltip,
                                        final DateTimeFormatter dateTimeFormatter,
                                        final RefCellsController refCells) {
        super(eventBus);
        this.refCells = own(refCells);
        own(infoTooltip);
        this.restFactory = restFactory;
        this.infoTooltip = infoTooltip;
        this.dateTimeFormatter = dateTimeFormatter;
        criteria = new ExpressionCriteria();
        own(OpenLinkUtil.addClickHandler(this, infoTooltip.asWidget()));
    }

    /// Attaches this to a list, e.g. one a template declares, which it fills from the server. The
    /// list is then this controller's widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public ProcessorTasksListController attach(final ProcessorTasksList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        view.addColumns(dateTimeFormatter::format,
                refCells.configure(new FeedRefCell.Builder<ProcessorTask>()),
                refCells.configure(new DocRefCell.Builder<ProcessorTask>()));
        view.setOnShowInfo(this::showInfo);
        view.setOnSortChange(this::refresh);
        return this;
    }

    private void showInfo(final ProcessorTask row, final PopupPosition popupPosition, final Element anchor) {
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
        findMetaCriteria.setExpression(MetaExpressionUtil.createDataIdExpression(row.getMetaId()));
        final int requestCount = ++infoRequestCount;

        restFactory
                .create(META_RESOURCE)
                .method(res -> res.findMetaRow(findMetaCriteria))
                .onSuccess(metaRows -> {
                    // Ignore the reply to an earlier click. Showing the tooltip again while
                    // it is showing hides it, so the reply to the latest click would hide
                    // the earlier click's tooltip rather than show its own
                    if (requestCount != infoRequestCount) {
                        return;
                    }
                    // Should only get one back
                    final Meta meta = Optional.ofNullable(metaRows)
                            .map(ResultPage::getFirst)
                            .map(MetaRow::getMeta)
                            .orElse(null);
                    showTooltip(popupPosition, anchor, row, meta);
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void showTooltip(final PopupPosition popupPosition,
                             final Element anchor,
                             final ProcessorTask processorTask,
                             final Meta meta) {

        final TableBuilder tb = new TableBuilder();
        tb
                .row(TableCell.header("Stream Task", 2))
                .row("Stream Task Id", String.valueOf(processorTask.getId()))
                .row("Status", processorTask.getStatus().getDisplayValue());

        if (processorTask.getProcessorFilter() != null) {
            tb.row("Priority", String.valueOf(processorTask.getProcessorFilter().getPriority()));
        }

        tb
                .row("Status Time", toDateString(processorTask.getStatusTimeMs()))
                .row("Start Time", toDateString(processorTask.getStartTimeMs()))
                .row("End Time", toDateString(processorTask.getEndTimeMs()))
                .row("Node", processorTask.getNodeName())
                .row(SafeHtmlUtils.fromString("Feed"),
                        OpenLinkUtil.render(processorTask.getFeedName(), LinkType.FEED))
                .row();

        if (meta != null) {
            tb
                    .row(TableCell.header("Stream", 2))
                    .row(SafeHtmlUtils.fromString("Stream Id"),
                            OpenLinkUtil.render(String.valueOf(meta.getId()), LinkType.STREAM))
                    .row("Status", meta.getStatus().getDisplayValue());

            if (meta.getParentMetaId() != null) {
                tb.row(SafeHtmlUtils.fromString("Parent Stream Id"),
                        OpenLinkUtil.render(String.valueOf(meta.getParentMetaId()), LinkType.STREAM));
            }

            tb
                    .row("Created", toDateString(meta.getCreateMs()))
                    .row("Effective", toDateString(meta.getEffectiveMs()))
                    .row("Stream Type", meta.getTypeName());
        } else {
            tb
                    .row(TableCell.data("[Physically deleted]", 2));
        }

        if (processorTask.getProcessorFilter() != null) {
            if (processorTask.getProcessorFilter().getProcessor() != null) {
                if (processorTask.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                    tb
                            .row()
                            .row(TableCell.header("Stream Processor", 2))
                            .row("Stream Processor Id",
                                    String.valueOf(processorTask.getProcessorFilter().getProcessor().getId()))
                            .row("Stream Processor Filter Id",
                                    String.valueOf(processorTask.getProcessorFilter().getId()));
                    if (processorTask.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                        tb.row("Stream Processor Pipeline",
                                DocRefUtil.createSimpleDocRefString(
                                        processorTask.getProcessorFilter().getPipeline()));
                    }
                }
            }
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("info-table"));
        infoTooltip.show(htmlBuilder.toSafeHtml(), popupPosition, anchor);
    }

    private String toDateString(final Long ms) {
        if (ms != null) {
            return dateTimeFormatter.formatWithDuration(ms);
        } else {
            return "";
        }
    }

    /// Shows what belongs to a document, e.g. a feed's or pipeline's.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document, which may be null.
    public void read(final DocRef docRef, final Object document) {
        if (docRef == null) {
            setExpression(null);
        } else if (PipelineDoc.TYPE.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createPipelineExpression(docRef));
        } else if (FeedDoc.TYPE.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createFeedExpression(docRef));
        } else if (ExplorerConstants.FOLDER_TYPE.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createFolderExpression(docRef));
        }
    }

    /// Shows the tasks matching an expression.
    ///
    /// @param expression The expression, or `null` for none.
    public void setExpression(final ExpressionOperator expression) {
        criteria.setExpression(expression);
        refresh();
    }

    /// Empties the list.
    public void clear() {
        view.clear();
    }

    /// Fetches the tasks again; the first time, starts the list.
    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<ProcessorTask, ResultPage<ProcessorTask>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ProcessorTask>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (criteria.getExpression() != null) {
                        CriteriaUtil.setRange(criteria, range);
                        CriteriaUtil.setSortList(criteria, view.getSortList());
                        restFactory
                                .create(PROCESSOR_TASK_RESOURCE)
                                .method(res -> res.find(criteria))
                                .onSuccess(dataConsumer)
                                .onFailure(errorHandler)
                                .owner(ProcessorTasksListController.this)
                                .taskMonitorFactory(view.getTaskMonitor())
                                .exec();
                    }
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }
}
