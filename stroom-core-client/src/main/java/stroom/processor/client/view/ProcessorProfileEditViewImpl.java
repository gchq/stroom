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

package stroom.processor.client.view;

import stroom.item.client.SelectionBox;
import stroom.node.shared.NodeGroup;
import stroom.planb.client.view.TimeZoneWidget;
import stroom.processor.client.presenter.ProcessorProfileEditPresenter.ProcessorProfileEditView;
import stroom.query.api.UserTimeZone;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class ProcessorProfileEditViewImpl extends ViewImpl implements ProcessorProfileEditView {

    private final Widget widget;

    private final TimeZoneWidget timeZoneWidget;

    @UiField
    TextBox name;
    @UiField
    SelectionBox<NodeGroup> nodeGroup;
    @UiField
    SimplePanel list;
    @UiField
    SimplePanel timeZone;

    @Inject
    public ProcessorProfileEditViewImpl(final Binder binder,
                                        final TimeZoneWidget timeZoneWidget) {
        this.timeZoneWidget = timeZoneWidget;
        widget = binder.createAndBindUi(this);
        name.setEnabled(false);
        timeZone.setWidget(timeZoneWidget.asWidget());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public String getName() {
        return this.name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public SelectionBox<NodeGroup> getNodeGroup() {
        return nodeGroup;
    }

    @Override
    public void setListView(final View listView) {
        list.setWidget(listView.asWidget());
    }

    @Override
    public UserTimeZone getUserTimeZone() {
        return timeZoneWidget.getUserTimeZone();
    }

    @Override
    public void setUserTimeZone(final UserTimeZone userTimeZone) {
        timeZoneWidget.setUserTimeZone(userTimeZone);
    }

    public interface Binder extends UiBinder<Widget, ProcessorProfileEditViewImpl> {

    }
}
