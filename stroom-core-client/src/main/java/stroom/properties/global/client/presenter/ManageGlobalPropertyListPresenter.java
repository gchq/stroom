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

package stroom.properties.global.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.properties.global.api.FetchGlobalPropertiesAction;
import stroom.properties.global.api.FindGlobalPropertyCriteria;
import stroom.properties.global.api.GlobalProperty;
import stroom.svg.client.SvgPreset;
import stroom.widget.button.client.ButtonView;

public class ManageGlobalPropertyListPresenter
        extends MyPresenterWidget<DataGridView<GlobalProperty>> implements Refreshable {
    private final ClientDispatchAsync dispatcher;
    private FindGlobalPropertyCriteria criteria;

    @Inject
    public ManageGlobalPropertyListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(true));

        criteria = new FindGlobalPropertyCriteria();
        this.dispatcher = dispatcher;

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
        getView().addEndColumn(new EndColumn<>());


//        dataProvider = new EntityServiceFindActionDataProvider<>(dispatcher,
//                getView());
//        dataProvider.setCriteria(criteria);
        refresh();
    }

//    public ImageButtonView addButton(final String title, final ImageResource enabledImage,
//                                     final ImageResource disabledImage, final boolean enabled) {
//        return getView().addButton(title, enabledImage, disabledImage, enabled);
//    }


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
        dispatcher.exec(new FetchGlobalPropertiesAction(criteria)).onSuccess(result -> {
            getView().setRowData(0, result);
            getView().setRowCount(result.size(), true);
        });
    }

    public GlobalProperty getSelectedItem() {
        return getView().getSelectionModel().getSelected();
    }

    public void setSelectedItem(final GlobalProperty row) {
        getView().getSelectionModel().setSelected(row);
    }

    public void setCriteria(final FindGlobalPropertyCriteria criteria) {
        this.criteria = criteria;
        refresh();
    }

    FindGlobalPropertyCriteria getFindGlobalPropertyCriteria() {
        return criteria;
    }
}
