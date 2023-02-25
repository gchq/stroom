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

package stroom.job.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.cell.info.client.InfoHelpLinkColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.job.shared.Job;
import stroom.job.shared.JobResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Window;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class JobListPresenter extends MyPresenterWidget<PagerView> {

    private static final JobResource JOB_RESOURCE = GWT.create(JobResource.class);

    private final MultiSelectionModelImpl<Job> selectionModel;

    @Inject
    public JobListPresenter(final EventBus eventBus,
                            final PagerView view,
                            final RestFactory restFactory,
                            final UiConfigCache clientPropertyCache) {
        super(eventBus, view);

        final MyDataGrid<Job> dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        // Enabled.
        final Column<Job, TickBoxState> enabledColumn = new Column<Job, TickBoxState>(
                TickBoxCell.create(false, false)) {

            @Override
            public TickBoxState getValue(final Job row) {
                if (row != null) {
                    return TickBoxState.fromBoolean(row.isEnabled());
                }
                return null;
            }
        };
        enabledColumn.setFieldUpdater((index, row, value) -> {
            row.setEnabled(value.toBoolean());
            final Rest<Job> rest = restFactory.create();
            rest.call(JOB_RESOURCE).setEnabled(row.getId(), value.toBoolean());
        });
        dataGrid.addColumn(enabledColumn, "Enabled", 80);

        // Job name.
        dataGrid.addResizableColumn(new Column<Job, String>(new TextCell()) {
            @Override
            public String getValue(final Job row) {
                if (row != null) {
                    return row.getName();
                }
                return null;
            }
        }, "Job", 200);

        // Help
        dataGrid.addColumn(new InfoHelpLinkColumn<Job>() {
            @Override
            public Preset getValue(final Job row) {
                if (row != null) {
                    return SvgPresets.HELP;
                }
                return null;
            }

            @Override
            protected void showHelp(final Job row) {

                clientPropertyCache.get()
                        .onSuccess(result -> {
                            final String helpUrl = result.getHelpUrlJobs();
                            if (helpUrl != null && helpUrl.trim().length() > 0) {
                                // This is a bit fragile as if the headings change in the docs then the anchors
                                // wont work
                                final String url = helpUrl + formatAnchor(row.getName());
                                Window.open(url, "_blank", "");
                            } else {
                                AlertEvent.fireError(
                                        JobListPresenter.this,
                                        "Help is not configured!",
                                        null);
                            }
                        })
                        .onFailure(caught ->
                                AlertEvent.fireError(
                                        JobListPresenter.this,
                                        caught.getMessage(),
                                        null));
            }

        }, "<br/>", 20);

        dataGrid.addResizableColumn(new Column<Job, String>(new TextCell()) {
            @Override
            public String getValue(final Job row) {
                if (row != null) {
                    return row.getDescription();
                }
                return null;
            }
        }, "Description", 800);

        dataGrid.addEndColumn(new EndColumn<>());

        final RestDataProvider<Job, ResultPage<Job>> dataProvider =
                new RestDataProvider<Job, ResultPage<Job>>(eventBus) {

                    @Override
                    protected void exec(final Range range,
                                        final Consumer<ResultPage<Job>> dataConsumer,
                                        final Consumer<Throwable> throwableConsumer) {
                        final Rest<ResultPage<Job>> rest = restFactory.create();
                        rest
                                .onSuccess(dataConsumer)
                                .onFailure(throwableConsumer)
                                .call(JOB_RESOURCE).list();
                    }

                    @Override
                    protected void changeData(final ResultPage<Job> data) {
                        final List<Job> rtnList = new ArrayList<>();

                        boolean addedGap = false;
                        for (int i = 0; i < data.size(); i++) {
                            rtnList.add(data.getValues().get(i));
                            if (data.getValues().get(i).isAdvanced() && !addedGap) {
                                // Add a gap between the non-advanced and advanced jobs
                                rtnList.add(i, null);
                                addedGap = true;
                            }
                        }

                        final ResultPage<Job> modifiedData = new ResultPage<>(rtnList);
                        super.changeData(modifiedData);
                    }
                };
        dataProvider.addDataDisplay(dataGrid);
    }

    public MultiSelectionModel<Job> getSelectionModel() {
        return selectionModel;
    }
}
