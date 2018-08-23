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

package stroom.jobsystem.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.info.client.InfoHelpLinkColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntitySaveTask;
import stroom.entity.client.SaveQueue;
import stroom.entity.client.presenter.EntityServiceFindActionDataProvider;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityRow;
import stroom.entity.shared.ResultList;
import stroom.jobsystem.shared.FindJobCriteria;
import stroom.jobsystem.shared.Job;
import stroom.properties.global.client.ClientPropertyCache;
import stroom.properties.shared.ClientProperties;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class JobListPresenter extends MyPresenterWidget<DataGridView<Job>> {
    private final SaveQueue<Job> jobSaver;
    private EntityServiceFindActionDataProvider<FindJobCriteria, Job> dataProvider;

    @Inject
    public JobListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                            final ClientPropertyCache clientPropertyCache) {
        super(eventBus, new DataGridViewImpl<>(true));

        jobSaver = new SaveQueue<>(dispatcher);

        getView().addColumn(new InfoHelpLinkColumn<Job>() {
            @Override
            public SvgPreset getValue(final Job row) {
                if (!row.isPersistent()) {
                    return null;
                }
                return SvgPresets.HELP;
            }

            @Override
            protected void showHelp(final Job row) {
                clientPropertyCache.get()
                        .onSuccess(result -> {
                            final String helpUrl = result.get(ClientProperties.HELP_URL);
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
                if (!row.isPersistent()) {
                    return "";
                }
                return row.getName();
            }
        }, "Job");

        // Enabled.
        final Column<Job, TickBoxState> enabledColumn = new Column<Job, TickBoxState>(TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final Job row) {
                if (!row.isPersistent()) {
                    return null;
                }
                return TickBoxState.fromBoolean(row.isEnabled());
            }
        };
        enabledColumn.setFieldUpdater((index, row, value) -> {
            final boolean newValue = value.toBoolean();
            jobSaver.save(new EntitySaveTask<Job>(new EntityRow<>(row)) {
                @Override
                protected void setValue(final Job entity) {
                    entity.setEnabled(newValue);
                }
            });
        });
        getView().addColumn(enabledColumn, "Enabled", 80);

        getView().addColumn(new Column<Job, String>(new TextCell()) {
            @Override
            public String getValue(final Job row) {
                if (!row.isPersistent()) {
                    return "";
                }
                return row.getDescription();
            }
        }, "Description", 800);

        getView().addEndColumn(new EndColumn<>());

        this.dataProvider = new EntityServiceFindActionDataProvider<FindJobCriteria, Job>(dispatcher, getView()) {
            // Add in extra blank item
            @Override
            protected ResultList<Job> processData(final ResultList<Job> data) {
                final List<Job> rtnList = new ArrayList<>();

                boolean done = false;
                for (int i = 0; i < data.size(); i++) {
                    rtnList.add(data.get(i));
                    if (data.get(i).isAdvanced() && !done) {
                        rtnList.add(i, new Job());
                        done = true;
                    }
                }

                return new BaseResultList<>(rtnList, 0L, (long) rtnList.size(), true);
            }
        };
        final FindJobCriteria findJobCriteria = new FindJobCriteria();
        findJobCriteria.setSort(FindJobCriteria.FIELD_ADVANCED);
        findJobCriteria.addSort(FindJobCriteria.FIELD_NAME);
        this.dataProvider.setCriteria(findJobCriteria);
    }

    public MultiSelectionModel<Job> getSelectionModel() {
        return getView().getSelectionModel();
    }
}
