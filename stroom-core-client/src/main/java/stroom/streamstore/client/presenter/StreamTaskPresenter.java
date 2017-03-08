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
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.SummaryDataRow;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.TaskStatus;

public class StreamTaskPresenter extends MyPresenterWidget<StreamTaskPresenter.StreamTaskView>
        implements HasRead<BaseEntity> {
    public static final String STREAM_TASK_LIST = "STREAM_TASK_LIST";
    public static final String STREAM_TASK_SUMMARY = "STREAM_TASK_SUMMARY";

    public interface StreamTaskView extends View {
    }

    private final StreamTaskSummaryPresenter streamTaskSummaryPresenter;
    private final StreamTaskListPresenter streamTaskListPresenter;

    @Inject
    public StreamTaskPresenter(final EventBus eventBus, final StreamTaskView view,
                               final StreamTaskSummaryPresenter streamTaskSummaryPresenter,
                               final StreamTaskListPresenter streamTaskListPresenter, final ClientDispatchAsync dispatcher) {
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

            final SummaryDataRow row = streamTaskSummaryPresenter.getSelectionModel().getSelected();

            if (row != null) {
                final FindStreamTaskCriteria findStreamTaskCriteria = streamTaskListPresenter.getDataProvider()
                        .getCriteria();
                final FindStreamCriteria findStreamCriteria = streamTaskListPresenter.getDataProvider()
                        .getCriteria().obtainFindStreamCriteria();

                findStreamTaskCriteria.obtainPipelineIdSet().clear();
                findStreamTaskCriteria.obtainPipelineIdSet()
                        .add(row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_PIPELINE));

                findStreamCriteria.obtainFeeds().clear();
                findStreamCriteria.obtainFeeds().obtainInclude()
                        .add(row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_FEED));

                findStreamTaskCriteria.obtainStreamTaskStatusSet().clear();
                findStreamTaskCriteria.obtainStreamTaskStatusSet()
                        .add(TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                                row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_STATUS).byteValue()));

                final int priority = row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_PRIORITY).intValue();

                streamTaskListPresenter.getDataProvider().refresh();
            }
        }));
    }

    @Override
    public void read(final BaseEntity entity) {
        streamTaskSummaryPresenter.read(entity);
        streamTaskListPresenter.read(entity);
    }
}
