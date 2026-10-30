/*
 * Copyright 2025 Crown Copyright
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

package stroom.processor.task.client.component;

import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Processor tasks ([ProcessorTasks]): their summary, and the tasks of the summary row selected.
/// Either a document's ([#read(DocRef, Object)], e.g. a feed's, pipeline's or folder's), or a
/// processor filter's ([#setProcessorFilter(ProcessorFilter)]).
public class ProcessorTasksController extends Controller {

    // A filter's summary pane is shorter than a document's (400px, as the template has it)
    private static final String FILTER_SUMMARY_SIZE = "200px";

    private final ProcessorTasks view;
    private final ProcessorTaskSummaryListController processorTaskSummary;
    private final ProcessorTaskListController processorTaskList;

    private ProcessorFilter processorFilter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param processorTaskSummary The summary of the tasks.
    /// @param processorTaskList    The tasks.
    @Inject
    public ProcessorTasksController(final EventBus eventBus,
                                    final ProcessorTasks view,
                                    final ProcessorTaskSummaryListController processorTaskSummary,
                                    final ProcessorTaskListController processorTaskList) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.processorTaskSummary = own(processorTaskSummary).attach(view.getSummary());
        this.processorTaskList = own(processorTaskList).attach(view.getTasks());

        own(processorTaskSummary.getSelectionModel().addSelectionHandler(event -> {
            // Clear the task list.
            processorTaskList.clear();

            final ProcessorTaskSummary row = processorTaskSummary.getSelectionModel().getSelected();

            if (row != null) {
                processorTaskList.setExpression(getExpression(row));
            }
        }));
    }

    // The tasks of a summary row (or all of them, if null), and of the filter, if one was given
    private ExpressionOperator getExpression(final ProcessorTaskSummary processorTaskSummary) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        if (processorTaskSummary != null) {
            if (processorTaskSummary.getPipeline() != null) {
                builder.addDocRefTerm(ProcessorTaskFields.PIPELINE, Condition.IS_DOC_REF,
                        processorTaskSummary.getPipeline());
            }
            if (processorTaskSummary.getFeed() != null) {
                builder.addTextTerm(ProcessorTaskFields.FEED, Condition.EQUALS, processorTaskSummary.getFeed());
            }
            if (processorTaskSummary.getStatus() != null) {
                builder.addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS,
                        processorTaskSummary.getStatus().getDisplayValue());
            }
        }

        if (processorFilter != null) {
            builder.addIntegerTerm(ProcessorTaskFields.PROCESSOR_FILTER_ID, Condition.EQUALS, processorFilter.getId());
        }

        return builder.build();
    }

    /// Shows the tasks of a document, e.g. a feed's or pipeline's.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document, which may be null.
    public void read(final DocRef docRef, final Object document) {
        processorTaskSummary.read(docRef, document);
    }

    /// Fetches the filter's tasks again.
    public void refresh() {
        final ExpressionOperator expressionOperator = getExpression(null);
        processorTaskSummary.setExpression(expressionOperator);
        processorTaskList.setExpression(expressionOperator);

        processorTaskSummary.refresh();
        processorTaskList.refresh();
    }

    /// Shows a processor filter's tasks.
    ///
    /// @param processorFilter The filter.
    public void setProcessorFilter(final ProcessorFilter processorFilter) {
        if (this.processorFilter == null) {
            view.setSummarySize(FILTER_SUMMARY_SIZE);
        }
        this.processorFilter = processorFilter;
        refresh();
    }

    /// @return The label of the tab showing a filter's tasks, e.g. `Tasks: Filter 12`.
    public String getLabel() {
        return "Tasks: Filter " + (processorFilter == null ? "" : processorFilter.getId().toString());
    }
}
