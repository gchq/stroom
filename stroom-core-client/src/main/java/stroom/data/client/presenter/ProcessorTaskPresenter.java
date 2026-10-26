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

import stroom.data.client.component.ProcessorTasksListController;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class ProcessorTaskPresenter extends MyPresenterWidget<ProcessorTaskPresenter.StreamTaskView>
        implements HasDocumentRead<Object> {

    private final ProcessorTaskSummaryPresenter processorTaskSummaryPresenter;
    private final ProcessorTasksListController processorTaskList;

    @Inject
    public ProcessorTaskPresenter(final EventBus eventBus, final StreamTaskView view,
                                  final ProcessorTaskSummaryPresenter processorTaskSummaryPresenter,
                                  final ProcessorTasksListController processorTaskList) {
        super(eventBus, view);
        own(processorTaskSummaryPresenter);
        own(processorTaskList);
        this.processorTaskSummaryPresenter = processorTaskSummaryPresenter;
        this.processorTaskList = processorTaskList;

        getView().setTaskSummaryView(processorTaskSummaryPresenter.getView());
        getView().setTaskListView(processorTaskList);

        own(processorTaskSummaryPresenter.getSelectionModel().addSelectionHandler(event -> {
            // Clear the task list.
            processorTaskList.clear();

            final ProcessorTaskSummary row = processorTaskSummaryPresenter.getSelectionModel().getSelected();

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

    @Override
    public void read(final DocRef docRef, final Object document) {
        processorTaskSummaryPresenter.read(docRef, document);
//        processorTaskList.read(docRef, entity);
    }

    public interface StreamTaskView extends View {

        /// Shows the summary of tasks.
        ///
        /// @param view Its view.
        void setTaskSummaryView(View view);

        /// Shows the tasks.
        ///
        /// @param view Its view.
        void setTaskListView(IsWidget view);
    }
}
