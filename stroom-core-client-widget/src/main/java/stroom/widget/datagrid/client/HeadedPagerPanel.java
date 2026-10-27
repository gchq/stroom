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
import stroom.widget.form.client.FormGroup;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasRows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/// A [PagerPanel] with a heading: the bar with the list's toolbar and a pager above the list, all
/// in a form group labelled with the heading. Its own layout (the toolbar fills the bar, and there
/// is no message), as putting a form group in every list's panel broke them. Its refresh button
/// spins while the list's requests run, when it is their task monitor.
///
/// A template can declare its children, as a [PagerPanel]'s: the list, and the buttons in its
/// toolbar.
public class HeadedPagerPanel extends Component implements TaskMonitorFactory, HasWidgets {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup pagerFormGroup;
    @UiField
    FlowPanel pagerContainer;
    @UiField
    Pager pager;
    @UiField
    Toolbar toolbar;
    @UiField
    SimplePanel listContainer;

    private final List<Widget> children = new ArrayList<>();
    private int taskCount;

    /// Makes an empty panel with no heading, e.g. one a template declares.
    public HeadedPagerPanel() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows a data widget, e.g. a grid, in the panel, with the pager paging it.
    ///
    /// @param widget The data widget.
    /// @param <W>    The widget's type.
    public <W extends Widget & HasRows> void setDataWidget(final W widget) {
        pager.setDisplay(widget);
        listContainer.setWidget(widget);
    }

    /// Sets the heading. With none, it takes up no space.
    ///
    /// @param heading The heading.
    public void setHeading(final String heading) {
        pagerFormGroup.setLabel(heading);
    }

    /// The toolbar beside the pager, for the list's own buttons, e.g.
    /// `getToolbar().button(SvgImage.ADD, "Add", this::add)`. Name it after the list with
    /// [Toolbar#setName(String)].
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return toolbar;
    }

    /// @param name The toolbar's name, after the list, e.g. "Job node actions".
    public void setToolbarName(final String name) {
        toolbar.setName(name);
    }

    /// Adds a child a template declares: a widget with rows (e.g. a `DataGrid`) is the list, shown
    /// and paged; anything else (a toolbar button, a toggle, a separator) goes in the toolbar.
    ///
    /// @param widget The child.
    @Override
    public void add(final Widget widget) {
        if (widget instanceof HasRows) {
            showList(widget);
        } else {
            toolbar.add(widget);
        }
        children.add(widget);
    }

    /// Removes every child a template declared.
    @Override
    public void clear() {
        for (final Widget widget : new ArrayList<>(children)) {
            remove(widget);
        }
    }

    /// @return The children a template declared, in order.
    @Override
    public Iterator<Widget> iterator() {
        return Collections.unmodifiableList(children).iterator();
    }

    /// @param widget A child a template declared.
    /// @return Whether it was one, and is removed.
    @Override
    public boolean remove(final Widget widget) {
        if (!children.remove(widget)) {
            return false;
        }
        widget.removeFromParent();
        return true;
    }

    private void showList(final Widget widget) {
        setDataWidget((Widget & HasRows) widget);
    }

    /// @return The pager's refresh button.
    public RefreshButton getRefreshButton() {
        return pager.getRefreshButton();
    }

    /// @param visible Whether the bar with the toolbar and pager shows.
    public void setPagerVisible(final boolean visible) {
        pagerContainer.setVisible(visible);
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


    interface Binder extends UiBinder<Widget, HeadedPagerPanel> {

    }
}
