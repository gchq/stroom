/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.client;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.activity.shared.Activity;
import stroom.activity.shared.FindActivityCriteria;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.EntityServiceFindActionDataProvider;
import stroom.svg.client.SvgPreset;
import stroom.widget.button.client.ButtonView;

public class ActivityListPresenter
        extends MyPresenterWidget<DataGridView<Activity>>
        implements Refreshable {
    protected EntityServiceFindActionDataProvider<FindActivityCriteria, Activity> dataProvider;

    @Inject
    public ActivityListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<Activity>(true));

        // Name.
        getView().addResizableColumn(new Column<Activity, String>(new TextCell()) {
            @Override
            public String getValue(final Activity activity) {
                if (activity == null) {
                    return null;
                }
                return activity.toString();
            }
        }, "Activity", 600);
        getView().addEndColumn(new EndColumn<Activity>());

        dataProvider = new EntityServiceFindActionDataProvider<FindActivityCriteria, Activity>(dispatcher, getView());
    }

    public ButtonView addButton(final SvgPreset preset) {
        return getView().addButton(preset);
    }

    @Override
    protected void onReveal() {
        super.onReveal();
        refresh();
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    Activity getSelectedItem() {
        return getView().getSelectionModel().getSelected();
    }

    void setSelectedItem(final Activity row) {
        getView().getSelectionModel().setSelected(row);
    }

    void setCriteria(final FindActivityCriteria criteria) {
        dataProvider.setCriteria(criteria);
    }
}
