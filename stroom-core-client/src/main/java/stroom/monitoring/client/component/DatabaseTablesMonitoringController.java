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

package stroom.monitoring.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.DbStatusResource;
import stroom.node.shared.FindDBTableCriteria;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Shows the sizes of Stroom's database tables ([DatabaseTables]).
public class DatabaseTablesMonitoringController extends ContentTabController {

    public static final String TAB_TYPE = "DatabaseTables";

    private static final DbStatusResource DB_STATUS_RESOURCE = GWT.create(DbStatusResource.class);

    private final DatabaseTables view;

    private final RestFactory restFactory;
    private final FindDBTableCriteria criteria;
    private RestDataProvider<DBTableStatus, ResultPage<DBTableStatus>> dataProvider;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        Its component.
    /// @param restFactory Makes REST requests.
    @Inject
    public DatabaseTablesMonitoringController(final EventBus eventBus,
                                              final DatabaseTables view,
                                              final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.restFactory = restFactory;

        criteria = new FindDBTableCriteria();
        refresh();

        view.setOnSortChange(this::refresh);
    }

    private void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<DBTableStatus, ResultPage<DBTableStatus>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<DBTableStatus>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    CriteriaUtil.setRange(criteria, range);
                    CriteriaUtil.setSortList(criteria, view.getColumnSortList());
                    restFactory
                            .create(DB_STATUS_RESOURCE)
                            .method(res -> res.findSystemTableStatus(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .owner(DatabaseTablesMonitoringController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DATABASE;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Database Tables";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
