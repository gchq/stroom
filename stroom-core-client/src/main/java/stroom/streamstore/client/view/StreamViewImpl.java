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

import stroom.streamstore.client.presenter.StreamPresenter;
import stroom.streamstore.client.presenter.StreamPresenter.StreamView;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class StreamViewImpl extends ViewImpl implements StreamView {
    public interface Binder extends UiBinder<Widget, StreamViewImpl> {
    }

    private final Widget widget;

    @UiField
    ResizeSimplePanel streamList;
    @UiField
    ResizeSimplePanel streamRelationList;
    @UiField
    ResizeSimplePanel data;

    @Inject
    public StreamViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (StreamPresenter.STREAM_LIST.equals(slot)) {
            streamList.setWidget(content);
        } else if (StreamPresenter.STREAM_RELATION_LIST.equals(slot)) {
            streamRelationList.setWidget(content);
        } else if (StreamPresenter.DATA.equals(slot)) {
            data.setWidget(content);
        }
    }
}
