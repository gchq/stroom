/*
 * Copyright 2017 Crown Copyright
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
import stroom.widget.form.client.FormGroup;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

import javax.inject.Inject;

public class PagerViewImpl extends ViewImpl implements PagerView {

    /**
     * The pager used to change the range of data.
     */
    @UiField
    FormGroup pagerFormGroup;
    @UiField
    FlowPanel pagerContainer;
    @UiField
    Pager pager;
    @UiField
    ButtonPanel buttonPanel;
    @UiField
    SimplePanel listContainer;

    private int taskCount;

    private final Widget widget;

    @Inject
    public PagerViewImpl(final Binder binder) {
        // Create the UiBinder.
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setDataWidget(final AbstractHasData<?> widget) {
        pager.setDisplay(widget);
        listContainer.setWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setHeading(final String string) {
        pagerFormGroup.setLabel(string);
    }

    @Override
    public ButtonView addButton(final Preset preset) {
        return buttonPanel.addButton(preset);
    }

    @Override
    public void addButton(final ButtonView buttonView) {
        buttonPanel.addButton(buttonView);
    }

    @Override
    public ToggleButtonView addToggleButton(final Preset primaryPreset,
                                            final Preset secondaryPreset) {
        return buttonPanel.addToggleButton(primaryPreset, secondaryPreset);
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

    public interface Binder extends UiBinder<Widget, PagerViewImpl> {

    }
}
