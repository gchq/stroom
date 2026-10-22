/*
 * Copyright 2021 Crown Copyright
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

package stroom.pathways.client.view;

import stroom.pathways.client.presenter.PathwayTreePresenter.PathwayTreeView;
import stroom.widget.button.client.Toolbar;
import stroom.widget.presenter.client.ViewImpl;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import javax.inject.Inject;

public class PathwayTreeViewImpl extends ViewImpl implements PathwayTreeView {

    @UiField
    FlowPanel toolbarWidgets;
    @UiField
    FlowPanel listContainer;

    private Toolbar toolbar;
    private final Widget widget;

    @Inject
    public PathwayTreeViewImpl(final Binder binder) {
        // Create the UiBinder.
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setDataWidget(final Widget widget) {
        listContainer.add(widget);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public Toolbar getToolbar() {
        if (toolbar == null) {
            toolbar = new Toolbar("Pathway actions");
            toolbarWidgets.add(toolbar);
        }
        return toolbar;
    }

    public interface Binder extends UiBinder<Widget, PathwayTreeViewImpl> {

    }
}
