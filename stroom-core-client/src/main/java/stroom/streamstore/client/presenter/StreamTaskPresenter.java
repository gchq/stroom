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

package stroom.streamstore.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.BaseEntity;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamtask.shared.ProcessTaskDataSource;
import stroom.streamtask.shared.StreamTaskSummary;

public class StreamTaskPresenter extends MyPresenterWidget<StreamTaskPresenter.StreamTaskView>
        implements HasDocumentRead<BaseEntity> {
    public static final String STREAM_TASK_LIST = "STREAM_TASK_LIST";
    public static final String STREAM_TASK_SUMMARY = "STREAM_TASK_SUMMARY";
    private final StreamTaskSummaryPresenter streamTaskSummaryPresenter;
    private final StreamTaskListPresenter streamTaskListPresenter;
    @Inject
    public StreamTaskPresenter(final EventBus eventBus, final StreamTaskView view,
                               final StreamTaskSummaryPresenter streamTaskSummaryPresenter,
                               final StreamTaskListPresenter streamTaskListPresenter) {
        super(eventBus, view);
        this.streamTaskSummaryPresenter = streamTaskSummaryPresenter;
        this.streamTaskListPresenter = streamTaskListPresenter;

        setInSlot(STREAM_TASK_SUMMARY, streamTaskSummaryPresenter);
        setInSlot(STREAM_TASK_LIST, streamTaskListPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(streamTaskSummaryPresenter.getSelectionModel().addSelectionHandler(event -> {
            // Clear the task list.
            streamTaskListPresenter.clear();

            final StreamTaskSummary row = streamTaskSummaryPresenter.getSelectionModel().getSelected();

            if (row != null) {
                final ExpressionOperator.Builder root = new ExpressionOperator.Builder(Op.AND);
                if (row.getPipeline() != null) {
                    root.addDocRefTerm(ProcessTaskDataSource.PIPELINE_UUID, Condition.IS_DOC_REF, row.getPipeline());
                }
                if (row.getFeed() != null) {
                    root.addDocRefTerm(ProcessTaskDataSource.FEED_UUID, Condition.IS_DOC_REF, row.getFeed());
                }
                if (row.getStatus() != null) {
                    root.addTerm(ProcessTaskDataSource.TASK_STATUS, Condition.EQUALS, row.getStatus().getDisplayValue());
                }

                streamTaskListPresenter.setExpression(root.build());
            }
        }));
    }

    @Override
    public void read(final DocRef docRef, final BaseEntity entity) {
        streamTaskSummaryPresenter.read(docRef, entity);
        streamTaskListPresenter.read(docRef, entity);
    }

    public interface StreamTaskView extends View {
    }
}
