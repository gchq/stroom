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

package stroom.config.global.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.FindGlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.SvgPreset;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;

import java.util.function.Consumer;

public class ManageGlobalPropertyListPresenter
        extends MyPresenterWidget<DataGridView<ConfigProperty>> implements Refreshable {
    private static final GlobalConfigResource CONFIG_RESOURCE = GWT.create(GlobalConfigResource.class);
    private final RestDataProvider<ConfigProperty, ResultPage<ConfigProperty>> dataProvider;
    private final FindGlobalConfigCriteria criteria = new FindGlobalConfigCriteria();

    @Inject
    public ManageGlobalPropertyListPresenter(final EventBus eventBus, final RestFactory restFactory) {
        super(eventBus, new DataGridViewImpl<>(true));

        // Name.
        getView().addResizableColumn(new Column<ConfigProperty, String>(new TextCell()) {
            @Override
            public String getValue(final ConfigProperty row) {
                if (row == null) {
                    return null;
                }
                return row.getName().toString();
            }
        }, "Name", 350);
        getView().addResizableColumn(new Column<ConfigProperty, String>(new TextCell()) {
            @Override
            public String getValue(final ConfigProperty row) {
                if (row == null) {
                    return null;
                }
                return row.getEffectiveValueMasked().orElse(null);
            }
        }, "Effective Value", 150);

        getView().addResizableColumn(new Column<ConfigProperty, String>(new TextCell()) {
            @Override
            public String getValue(final ConfigProperty row) {
                if (row == null) {
                    return null;
                }
                return row.getSource().getName();
            }
        }, "Source", 75);

        getView().addResizableColumn(new Column<ConfigProperty, String>(new TextCell()) {
            @Override
            public String getValue(final ConfigProperty row) {
                if (row == null) {
                    return null;
                }
                return row.getDescription();
            }
        }, "Description", 400);
        getView().addEndColumn(new EndColumn<>());

        this.dataProvider = new RestDataProvider<ConfigProperty, ResultPage<ConfigProperty>>(eventBus) {
            @Override
            protected void exec(final Consumer<ResultPage<ConfigProperty>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<ResultPage<ConfigProperty>> rest = restFactory.create();
                rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(CONFIG_RESOURCE).find(criteria);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
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
//        dispatcher.exec(new FetchGlobalConfigAction(criteria)).onSuccess(result -> {
//            getView().setRowData(0, result);
//            getView().setRowCount(result.size(), true);
//        });
    }

    public ConfigProperty getSelectedItem() {
        return getView().getSelectionModel().getSelected();
    }

    public void setSelectedItem(final ConfigProperty row) {
        getView().getSelectionModel().setSelected(row);
    }

    //    public void setCriteria(final FindGlobalConfigCriteria criteria) {
//        this.criteria = criteria;
//        refresh();
//    }
//
    FindGlobalConfigCriteria getFindGlobalPropertyCriteria() {
        return criteria;
    }
}
