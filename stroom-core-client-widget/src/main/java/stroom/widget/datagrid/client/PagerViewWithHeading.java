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

import stroom.data.pager.client.RefreshButton;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Toolbar;

import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasRows;
import com.gwtplatform.mvp.client.View;

public interface PagerViewWithHeading extends View, TaskMonitorFactory {

    /**
     * Set a form group label for the pager view. If not set it won't take up any
     * space
     */
    void setHeading(final String string);

    /// The toolbar beside the pager, for the list's own buttons, e.g.
    /// `getToolbar().button(SvgImage.ADD, "Add", this::add)`. Name it after the list with
    /// [Toolbar#setName(String)].
    ///
    /// @return The toolbar.
    Toolbar getToolbar();

    RefreshButton getRefreshButton();

    /// Show a data widget, e.g. a grid, in the view and attach the pager to it.
    ///
    /// @param widget The data widget.
    /// @param <W>    The widget's type.
    <W extends Widget & HasRows> void setDataWidget(W widget);

    void setPagerVisible(boolean visible);
}
