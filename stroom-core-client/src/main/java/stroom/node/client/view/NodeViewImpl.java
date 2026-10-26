/*
 * Copyright 2024 Crown Copyright
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

package stroom.node.client.view;

import stroom.node.client.presenter.NodePresenter;
import stroom.widget.presenter.client.ViewImpl;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class NodeViewImpl extends ViewImpl implements NodePresenter.NodeView {

    private final Widget widget;
    @UiField
    SimplePanel nodeList;
    @UiField
    SimplePanel nodeJobList;

    @Inject
    public NodeViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setNodeListView(final IsWidget view) {
        nodeList.setWidget(view.asWidget());
    }

    @Override
    public void setNodeJobListView(final IsWidget view) {
        nodeJobList.setWidget(view.asWidget());
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, NodeViewImpl> {

    }
}
