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

package stroom.node.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.EntityServiceFindActionDataProvider;
import stroom.entity.client.presenter.ManageEntityListPresenter;
import stroom.node.shared.FindGlobalPropertyCriteria;
import stroom.node.shared.GlobalProperty;

public class ManageGlobalPropertyListPresenter
        extends ManageEntityListPresenter<FindGlobalPropertyCriteria, GlobalProperty> {
    private FindGlobalPropertyCriteria criteria;

    @Inject
    public ManageGlobalPropertyListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, dispatcher);

        // Name.
        getView().addResizableColumn(new Column<GlobalProperty, String>(new TextCell()) {
            @Override
            public String getValue(final GlobalProperty row) {
                if (row == null) {
                    return null;
                }
                return row.getName();
            }
        }, "Name", 200);
        getView().addResizableColumn(new Column<GlobalProperty, String>(new TextCell()) {
            @Override
            public String getValue(final GlobalProperty row) {
                if (row == null) {
                    return null;
                }
                return row.getValue();
            }
        }, "Value", 150);

        getView().addResizableColumn(new Column<GlobalProperty, String>(new TextCell()) {
            @Override
            public String getValue(final GlobalProperty row) {
                if (row == null) {
                    return null;
                }
                return row.getSource();
            }
        }, "Source", 150);

        getView().addResizableColumn(new Column<GlobalProperty, String>(new TextCell()) {
            @Override
            public String getValue(final GlobalProperty row) {
                if (row == null) {
                    return null;
                }
                return row.getDescription();
            }
        }, "Description", 300);
        getView().addEndColumn(new EndColumn<GlobalProperty>());

        criteria = new FindGlobalPropertyCriteria();
        dataProvider = new EntityServiceFindActionDataProvider<FindGlobalPropertyCriteria, GlobalProperty>(dispatcher,
                getView());
        dataProvider.setCriteria(criteria);
    }

    FindGlobalPropertyCriteria getFindGlobalPropertyCriteria() {
        return criteria;
    }
}
