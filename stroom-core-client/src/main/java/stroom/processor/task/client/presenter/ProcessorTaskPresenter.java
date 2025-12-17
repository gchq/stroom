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

package stroom.processor.task.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.presenter.ProcessorTaskListPresenter;
import stroom.data.client.presenter.ProcessorTaskSummaryPresenter;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter.ProcessorTaskView;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class ProcessorTaskPresenter extends ContentTabPresenter<ProcessorTaskView> {

    public static final String STREAM_TASK_LIST = "STREAM_TASK_LIST";
    public static final String STREAM_TASK_SUMMARY = "STREAM_TASK_SUMMARY";
    private final ProcessorTaskSummaryPresenter processorTaskSummaryPresenter;
    private final ProcessorTaskListPresenter processorTaskListPresenter;

    private ProcessorFilter processorFilter;

    @Inject
    public ProcessorTaskPresenter(final EventBus eventBus,
                                  final ProcessorTaskView view,
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
                processorTaskListPresenter.setExpression(getExpression(row));
            }
        }));
    }

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

    public void refresh() {
        final ExpressionOperator expressionOperator = getExpression(null);
        processorTaskSummaryPresenter.setExpression(expressionOperator);
        processorTaskListPresenter.setExpression(expressionOperator);

        processorTaskSummaryPresenter.refresh();
        processorTaskListPresenter.refresh();
    }

    public void setProcessorFilter(final ProcessorFilter processorFilter) {
        this.processorFilter = processorFilter;
        refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.JOBS;
    }

    @Override
    public String getLabel() {
        return "Tasks: Filter " + (processorFilter == null ? "" : processorFilter.getId().toString());
    }

    @Override
    public String getType() {
        return "Tasks";
    }

    public interface ProcessorTaskView extends View { }
}
