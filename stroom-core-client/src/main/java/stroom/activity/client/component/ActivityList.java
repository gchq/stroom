/*
 * Copyright 2026 Crown Copyright
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

package stroom.activity.client.component;

import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.Prop;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

/// The user's activities, a row each (the details shown in the list, a line each); several can be
/// selected.
public class ActivityList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<Activity> dataGrid;

    private final MultiSelectionModelImpl<Activity> selectionModel;

    /// Makes an empty list.
    public ActivityList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id: each refresh fetches new rows
        dataGrid.setKeyProvider(Activity::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(true);

        dataGrid.addResizableColumn(new Column<Activity, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Activity activity) {
                if (activity == null
                    || activity.getDetails() == null
                    || activity.getDetails().getProperties() == null) {
                    return null;
                }

                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                boolean doneOne = false;
                for (final Prop prop : activity.getDetails().getProperties()) {
                    if (prop.isShowInList()) {
                        if (doneOne) {
                            builder.appendHtmlConstant("<br/>");
                        }
                        doneOne = true;

                        builder.appendHtmlConstant("<b>");
                        builder.appendEscaped(prop.getName());
                        builder.appendEscaped(":");
                        builder.appendHtmlConstant("</b>");
                        builder.appendEscaped(" ");
                        builder.appendEscaped(prop.getValue());
                    }
                }

                return builder.toSafeHtml();
            }
        }, "Activity", 600);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<Activity> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModel<Activity> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ActivityList> {

    }
}
