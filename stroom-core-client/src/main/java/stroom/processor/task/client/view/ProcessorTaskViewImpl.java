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

package stroom.processor.task.client.view;

import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter.ProcessorTaskView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class ProcessorTaskViewImpl extends ViewImpl implements ProcessorTaskView {

    private final Widget widget;
    @UiField
    SimplePanel streamTaskSummary;
    @UiField
    SimplePanel streamTaskList;

    @Inject
    public ProcessorTaskViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (ProcessorTaskPresenter.STREAM_TASK_LIST.equals(slot)) {
            streamTaskList.setWidget(content);
        } else if (ProcessorTaskPresenter.STREAM_TASK_SUMMARY.equals(slot)) {
            streamTaskSummary.setWidget(content);
        }
    }

    public interface Binder extends UiBinder<Widget, ProcessorTaskViewImpl> {

    }
}
