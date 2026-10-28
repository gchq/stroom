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

import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows a summary of a document's processor tasks, and the tasks of the selected row.
public class ProcessorTasksTabController extends Controller {

    private final ProcessorTasksTab view;

    private final ProcessorTaskSummaryListController processorTaskSummary;
    private final ProcessorTasksListController processorTaskList;

    /// Made by GIN, with its screen and its parts.
    @Inject
    public ProcessorTasksTabController(final EventBus eventBus,
                                       final ProcessorTasksTab view,
                                       final ProcessorTaskSummaryListController processorTaskSummary,
                                       final ProcessorTasksListController processorTaskList) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(processorTaskSummary);
        own(processorTaskList);
        this.processorTaskSummary = processorTaskSummary;
        this.processorTaskList = processorTaskList;

        view.setTaskSummaryView(processorTaskSummary);
        view.setTaskListView(processorTaskList);

        own(processorTaskSummary.getSelectionModel().addSelectionHandler(event -> {
            // Clear the task list.
            processorTaskList.clear();

            final ProcessorTaskSummary row = processorTaskSummary.getSelectionModel().getSelected();

            if (row != null) {
                final ExpressionOperator.Builder root = ExpressionOperator.builder();
                if (row.getPipeline() != null) {
                    root.addDocRefTerm(ProcessorTaskFields.PIPELINE, Condition.IS_DOC_REF, row.getPipeline());
                }
                if (row.getFeed() != null) {
                    root.addTextTerm(ProcessorTaskFields.FEED, Condition.EQUALS, row.getFeed());
                }
                if (row.getStatus() != null) {
                    root.addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, row.getStatus().getDisplayValue());
                }

                processorTaskList.setExpression(root.build());
            }
        }));
    }

    /// Shows what belongs to a document, e.g. a feed's or pipeline's.
    ///
    /// @param docRef   The document's reference.
    /// @param document The document, which may be null.
    public void read(final DocRef docRef, final Object document) {
        processorTaskSummary.read(docRef, document);
//        processorTaskList.read(docRef, entity);
    }
}
