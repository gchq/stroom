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

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.info.client.InfoHelpLinkColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.job.shared.Job;
import stroom.job.shared.JobResource;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class JobListPresenter extends MyPresenterWidget<DataGridView<Job>> {
    private static final JobResource JOB_RESOURCE = GWT.create(JobResource.class);

    @Inject
    public JobListPresenter(final EventBus eventBus,
                            final RestFactory restFactory,
                            final UiConfigCache clientPropertyCache) {
        super(eventBus, new DataGridViewImpl<>(true));

        getView().addColumn(new InfoHelpLinkColumn<Job>() {
            @Override
            public SvgPreset getValue(final Job row) {
                if (row != null) {
                    return SvgPresets.HELP;
                }
                return null;
            }

            @Override
            protected void showHelp(final Job row) {
                clientPropertyCache.get()
                        .onSuccess(result -> {
                            final String helpUrl = result.getHelpUrl();
                            if (helpUrl != null && helpUrl.trim().length() > 0) {
                                String url = helpUrl + "/user-guide/tasks.html" + formatAnchor(row.getName());
                                Window.open(url, "_blank", "");
                            } else {
                                AlertEvent.fireError(JobListPresenter.this, "Help is not configured!", null);
                            }
                        })
                        .onFailure(caught -> AlertEvent.fireError(JobListPresenter.this, caught.getMessage(), null));
            }

        }, "<br/>", 20);

        getView().addColumn(new Column<Job, String>(new TextCell()) {
            @Override
            public String getValue(final Job row) {
                if (row != null) {
                    return row.getName();
                }
                return null;
            }
        }, "Job");

        // Enabled.
        final Column<Job, TickBoxState> enabledColumn = new Column<Job, TickBoxState>(TickBoxCell.create(false, false)) {
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
        getView().addColumn(enabledColumn, "Enabled", 80);

        getView().addColumn(new Column<Job, String>(new TextCell()) {
            @Override
            public String getValue(final Job row) {
                if (row != null) {
                    return row.getDescription();
                }
                return null;
            }
        }, "Description", 800);

        getView().addEndColumn(new EndColumn<>());

        final RestDataProvider<Job, ResultPage<Job>> dataProvider = new RestDataProvider<Job, ResultPage<Job>>(eventBus) {
            @Override
            protected void exec(final Consumer<ResultPage<Job>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<ResultPage<Job>> rest = restFactory.create();
                rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(JOB_RESOURCE).list();
            }

            @Override
            protected void changeData(final ResultPage<Job> data) {
                final List<Job> rtnList = new ArrayList<>();

                boolean done = false;
                for (int i = 0; i < data.size(); i++) {
                    rtnList.add(data.getValues().get(i));
                    if (data.getValues().get(i).isAdvanced() && !done) {
                        rtnList.add(i, null);
                        done = true;
                    }
                }

                data.setValues(rtnList);
                data.getPageResponse().setLength(rtnList.size());
                data.getPageResponse().setTotal((long) rtnList.size());
                data.getPageResponse().setExact(true);

                super.changeData(data);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
    }

    public MultiSelectionModel<Job> getSelectionModel() {
        return getView().getSelectionModel();
    }
}
