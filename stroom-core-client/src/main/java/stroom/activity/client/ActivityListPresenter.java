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

package stroom.activity.client;

import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.Prop;
import stroom.activity.shared.ActivityResource;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.Preset;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ActivityListPresenter
        extends MyPresenterWidget<PagerView>
        implements Refreshable {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);

    private final MyDataGrid<Activity> dataGrid;
    private final MultiSelectionModelImpl<Activity> selectionModel;
    protected RestDataProvider<Activity, ResultPage<Activity>> dataProvider;

    private String name;

    @Inject
    public ActivityListPresenter(final EventBus eventBus,
                                 final PagerView view,
                                 final RestFactory restFactory) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setMultiLine(true);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

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
        dataGrid.addEndColumn(new EndColumn<Activity>());

        dataProvider = new RestDataProvider<Activity, ResultPage<Activity>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Activity>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                restFactory
                        .create(ACTIVITY_RESOURCE)
                        .method(res -> res.list(name))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    public ButtonView addButton(final Preset preset) {
        return getView().addButton(preset);
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    MultiSelectionModel<Activity> getSelectionModel() {
        return selectionModel;
    }

    void setCriteria(final String name) {
        this.name = name;
    }

    String getCriteria() {
        return name;
    }
}
