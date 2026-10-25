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

package stroom.widget.datagrid.client;

import stroom.data.pager.client.Pager;
import stroom.data.pager.client.RefreshButton;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasRows;

/// A list's frame: a bar with the list's toolbar and a pager, above the list (e.g. a data grid)
/// and a message over it. Its refresh button spins while the list's requests run, when it is
/// their task monitor.
///
/// A presenter's view as well as a component, until the list presenters become controllers.
public class PagerPanel extends Component implements View, TaskMonitorFactory {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel pagerContainer;
    @UiField
    Toolbar toolbar;
    @UiField
    FlowPanel toolbarWidgets;
    @UiField
    Pager pager;
    @UiField
    FlowPanel listContainer;
    @UiField
    MessagePanel messagePanel;

    private int taskCount;

    /// Makes an empty panel, e.g. one a template declares, or one GIN makes for a list presenter.
    public PagerPanel() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows a data widget, e.g. a grid, in the panel, with the pager paging it.
    ///
    /// @param widget The data widget.
    /// @param <W>    The widget's type.
    public <W extends Widget & HasRows> void setDataWidget(final W widget) {
        pager.setDisplay(widget);
        // Under the message, which shows over it
        listContainer.insert(widget, 0);
    }

    /// The toolbar beside the pager, for the list's own buttons, e.g.
    /// `getToolbar().button(SvgImage.ADD, "Add", this::add)`. Name it after the list with
    /// [Toolbar#setName(String)]. It shows once asked for, so a list with no buttons has none.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        toolbar.setVisible(true);
        return toolbar;
    }

    /// Adds a widget after the toolbar, e.g. a label saying how many rows are selected.
    ///
    /// @param widget The widget.
    public void addToolbarWidget(final Widget widget) {
        toolbarWidgets.add(widget);
    }

    /// @return The pager's refresh button.
    public RefreshButton getRefreshButton() {
        return pager.getRefreshButton();
    }

    /// @param visible Whether the bar with the toolbar and pager shows.
    public void setPagerVisible(final boolean visible) {
        pagerContainer.setVisible(visible);
    }

    /// @return The message shown over the list, e.g. for its errors.
    public MessagePanel getMessagePanel() {
        return messagePanel;
    }

    /// A task monitor that spins the refresh button while any of its tasks run.
    ///
    /// @return The task monitor.
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


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PagerPanel> {

    }
}
