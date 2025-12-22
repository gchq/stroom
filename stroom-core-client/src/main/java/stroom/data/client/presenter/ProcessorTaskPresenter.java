/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ProcessorTaskPresenter extends MyPresenterWidget<ProcessorTaskPresenter.StreamTaskView>
        implements HasDocumentRead<Object> {

    public static final String STREAM_TASK_LIST = "STREAM_TASK_LIST";
    public static final String STREAM_TASK_SUMMARY = "STREAM_TASK_SUMMARY";
    private final ProcessorTaskSummaryPresenter processorTaskSummaryPresenter;
    private final ProcessorTaskListPresenter processorTaskListPresenter;

    @Inject
    public ProcessorTaskPresenter(final EventBus eventBus, final StreamTaskView view,
                                  final ProcessorTaskSummaryPresenter processorTaskSummaryPresenter,
                                  final ProcessorTaskListPresenter processorTaskListPresenter) {
        super(eventBus, view);
        this.processorTaskSummaryPresenter = processorTaskSummaryPresenter;
        this.processorTaskListPresenter = processorTaskListPresenter;

        setInSlot(STREAM_TASK_SUMMARY, processorTaskSummaryPresenter);
        setInSlot(STREAM_TASK_LIST, processorTaskListPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(processorTaskSummaryPresenter.getSelectionModel().addSelectionHandler(event -> {
            // Clear the task list.
            processorTaskListPresenter.clear();

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

                processorTaskListPresenter.setExpression(root.build());
            }
        }));
    }

    @Override
    public void read(final DocRef docRef, final Object document, final boolean readOnly) {
        processorTaskSummaryPresenter.read(docRef, document, readOnly);
//        processorTaskListPresenter.read(docRef, entity);
    }

    public interface StreamTaskView extends View {

    }
}
