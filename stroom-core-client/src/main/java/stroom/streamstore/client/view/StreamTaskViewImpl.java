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

package stroom.streamstore.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.streamstore.client.presenter.StreamTaskPresenter;
import stroom.streamstore.client.presenter.StreamTaskPresenter.StreamTaskView;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class StreamTaskViewImpl extends ViewImpl implements StreamTaskView {
    public interface Binder extends UiBinder<Widget, StreamTaskViewImpl> {
    }

    private final Widget widget;

    @UiField
    ResizeSimplePanel streamTaskSummary;
    @UiField
    ResizeSimplePanel streamTaskList;

    @Inject
    public StreamTaskViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (StreamTaskPresenter.STREAM_TASK_LIST.equals(slot)) {
            streamTaskList.setWidget(content);
        } else if (StreamTaskPresenter.STREAM_TASK_SUMMARY.equals(slot)) {
            streamTaskSummary.setWidget(content);
        }
    }
}
