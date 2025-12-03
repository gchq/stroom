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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.ProcessingStatusUiHandlers;
import stroom.analytics.client.presenter.ScheduledProcessEditView;
import stroom.item.client.SelectionBox;
import stroom.schedule.client.ScheduleBox;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class ScheduledProcessEditViewImpl
        extends ViewWithUiHandlers<ProcessingStatusUiHandlers>
        implements ScheduledProcessEditView {

    private final Widget widget;

    @UiField
    TextBox name;
    @UiField
    CustomCheckBox enabled;
    @UiField
    SelectionBox<String> node;
    @UiField
    ScheduleBox schedule;
    @UiField
    DateTimeBox startTime;
    @UiField
    DateTimeBox endTime;
    @UiField
    SimplePanel runAsUser;

    private String selectedNode;

    @Inject
    public ScheduledProcessEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
        name.selectAll();
    }

    @Override
    public String getName() {
        return name.getValue();
    }

    @Override
    public void setName(final String name) {
        this.name.setValue(name);
    }

    @Override
    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public void setNodes(final List<String> nodes) {
        this.node.clear();
        this.node.addItems(nodes);
        if (selectedNode == null) {
            if (nodes.size() > 0) {
                this.node.setValue(nodes.get(0));
            }
        } else {
            this.node.setValue(selectedNode);
            selectedNode = null;
        }
    }

    @Override
    public String getNode() {
        return this.node.getValue();
    }

    @Override
    public void setNode(final String node) {
        if (node != null) {
            selectedNode = node;
            this.node.setValue(node);
        }
    }

    @Override
    public ScheduleBox getScheduleBox() {
        return schedule;
    }

    @Override
    public DateTimeBox getStartTime() {
        return startTime;
    }

    @Override
    public DateTimeBox getEndTime() {
        return endTime;
    }

    @Override
    public void setRunAsUserView(final View view) {
        this.runAsUser.setWidget(view.asWidget());
        view.asWidget().addStyleName("w-100");
    }

    @UiHandler("name")
    public void onName(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("enabled")
    public void onEnabled(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("node")
    public void onNode(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("startTime")
    public void onStartTime(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("endTime")
    public void onEndTime(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    public interface Binder extends UiBinder<Widget, ScheduledProcessEditViewImpl> {

    }
}
