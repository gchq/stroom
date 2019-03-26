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
import stroom.entity.client.ActionQueue;
import stroom.entity.client.presenter.FindActionDataProvider;
import stroom.job.shared.FindJobAction;
import stroom.job.shared.FindJobCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.UpdateJobAction;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class JobListPresenter extends MyPresenterWidget<DataGridView<Job>> {
    private final ActionQueue<Job> actionQueue;
    private FindActionDataProvider<FindJobCriteria, Job> dataProvider;

    @Inject
    public JobListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                            final UiConfigCache clientPropertyCache) {
        super(eventBus, new DataGridViewImpl<>(true));

        actionQueue = new ActionQueue<>(dispatcher);

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
            final boolean newValue = value.toBoolean();
            row.setEnabled(newValue);
            actionQueue.dispatch(row, new UpdateJobAction(row));
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

        this.dataProvider = new FindActionDataProvider<FindJobCriteria, Job>(dispatcher, getView()) {
            // Add in extra blank item
            @Override
            protected ResultList<Job> processData(final ResultList<Job> data) {
                final List<Job> rtnList = new ArrayList<>();

                boolean done = false;
                for (int i = 0; i < data.size(); i++) {
                    rtnList.add(data.get(i));
                    if (data.get(i).isAdvanced() && !done) {
                        rtnList.add(i, null);
                        done = true;
                    }
                }

                return new BaseResultList<>(rtnList, 0L, (long) rtnList.size(), true);
            }
        };
        final FindJobCriteria findJobCriteria = new FindJobCriteria();
        findJobCriteria.setSort(FindJobCriteria.FIELD_ADVANCED);
        findJobCriteria.addSort(FindJobCriteria.FIELD_NAME);
        final FindJobAction action = new FindJobAction(findJobCriteria);
        this.dataProvider.setAction(action);
    }

    public MultiSelectionModel<Job> getSelectionModel() {
        return getView().getSelectionModel();
    }
}
