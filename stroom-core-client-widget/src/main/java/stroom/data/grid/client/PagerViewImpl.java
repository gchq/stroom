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

package stroom.data.grid.client;

import stroom.data.pager.client.Pager;
import stroom.data.pager.client.RefreshButton;
import stroom.svg.client.Preset;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.ToggleButtonView;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

import javax.inject.Inject;

public class PagerViewImpl extends ViewImpl implements PagerView {

    @UiField
    FlowPanel pagerContainer;
    @UiField
    Pager pager;
    @UiField
    FlowPanel toolbarWidgets;
    @UiField
    FlowPanel listContainer;

    private int taskCount;
    private ButtonPanel buttonPanel;

    private final Widget widget;
    private final MessagePanelImpl messagePanel = new MessagePanelImpl();

    @Inject
    public PagerViewImpl(final Binder binder) {
        // Create the UiBinder.
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setDataWidget(final AbstractHasData<?> widget) {
        pager.setDisplay(widget);
        listContainer.add(widget);
        listContainer.add(messagePanel);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public ButtonView addButton(final Preset preset) {
        return getButtonPanel().addButton(preset);
    }

    @Override
    public void addButton(final ButtonView buttonView) {
        getButtonPanel().addButton(buttonView);
    }

    @Override
    public ToggleButtonView addToggleButton(final Preset primaryPreset,
                                            final Preset secondaryPreset) {
        return getButtonPanel().addToggleButton(primaryPreset, secondaryPreset);
    }

    private ButtonPanel getButtonPanel() {
        if (buttonPanel == null) {
            buttonPanel = new ButtonPanel();
            toolbarWidgets.add(buttonPanel);
        }
        return buttonPanel;
    }

    @Override
    public RefreshButton getRefreshButton() {
        return pager.getRefreshButton();
    }

    @Override
    public void setPagerVisible(final boolean visible) {
        pagerContainer.setVisible(visible);
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                taskCount++;
                pager.getRefreshButton().setRefreshing(taskCount > 0);
            }

            @Override
            public void onEnd(final Task task) {
                taskCount--;

                if (taskCount < 0) {
                    GWT.log("Negative task count");
                }

                pager.getRefreshButton().setRefreshing(taskCount > 0);
            }
        };
    }

    @Override
    public MessagePanel getMessagePanel() {
        return messagePanel;
    }

    public interface Binder extends UiBinder<Widget, PagerViewImpl> {

    }
}
