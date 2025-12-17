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

package stroom.node.client.view;

import stroom.node.client.presenter.NodePresenter;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class NodeViewImpl extends ViewImpl implements NodePresenter.NodeView {

    private final Widget widget;
    @UiField
    SimplePanel nodeList;
    @UiField
    FormGroup nodeJobListFormGroup;
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
    public void setInSlot(final Object slot, final Widget content) {
        if (NodePresenter.NODE_LIST.equals(slot)) {
            nodeList.setWidget(content);
        } else if (NodePresenter.NODE_JOB_LIST.equals(slot)) {
            nodeJobList.setWidget(content);
        }
    }

//    @Override
//    public void setHeading(final String heading) {
//        nodeJobListFormGroup.setLabel(heading);
//    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, NodeViewImpl> {

    }
}
