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
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
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
import stroom.widget.button.client.SvgIcon;
import stroom.widget.button.client.SvgIcons;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class JobListPresenter extends MyPresenterWidget<DataGridView<Job>> {
    private EntityServiceFindActionDataProvider<FindJobCriteria, Job> dataProvider;

    private final SaveQueue<Job> jobSaver;

    @Inject
    public JobListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                            final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<Job>(true));

        jobSaver = new SaveQueue<Job>(dispatcher);

        getView().addColumn(new InfoHelpLinkColumn<Job>() {
            @Override
            public SvgIcon getValue(final Job row) {
                if (!row.isPersistent()) {
                    return null;
                }
                return SvgIcons.HELP;
            }

            @Override
            protected String getHelpLink(final Job row) {
                return GWT.getHostPageBaseURL() + "doc/user-guide/tasks/tasks.html" + formatAnchor(row.getName());
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
            jobSaver.save(new EntitySaveTask<Job>(new EntityRow<Job>(row)) {
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

        getView().addEndColumn(new EndColumn<Job>());

        this.dataProvider = new EntityServiceFindActionDataProvider<FindJobCriteria, Job>(dispatcher, getView()) {
            // Add in extra blank item
            @Override
            protected ResultList<Job> processData(final ResultList<Job> data) {
                final List<Job> rtnList = new ArrayList<Job>();

                boolean done = false;
                for (int i = 0; i < data.size(); i++) {
                    rtnList.add(data.get(i));
                    if (data.get(i).isAdvanced() && !done) {
                        rtnList.add(i, new Job());
                        done = true;
                    }
                }

                return new BaseResultList<Job>(rtnList, 0L, (long) rtnList.size(), false);

            }
        };
        final FindJobCriteria findJobCriteria = new FindJobCriteria();
        findJobCriteria.setOrderBy(FindJobCriteria.ORDER_BY_ADVANCED_AND_NAME);
        this.dataProvider.setCriteria(findJobCriteria);

    }

    public MultiSelectionModel<Job> getSelectionModel() {
        return getView().getSelectionModel();
    }
}
