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

package stroom.job.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.event.JobChangeEvent;
import stroom.job.shared.Job;
import stroom.job.shared.JobResource;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Top pane of JobPresenter (Jobs tab). Lists jobs (i.e. the parent job)
 */
public class JobListPresenter extends MyPresenterWidget<PagerView> {

    private static final JobResource JOB_RESOURCE = GWT.create(JobResource.class);

    private final MultiSelectionModelImpl<Job> selectionModel;
    private Consumer<Job> changeHandler = null;

    @Inject
    public JobListPresenter(final EventBus eventBus,
                            final PagerView view,
                            final RestFactory restFactory,
                            final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        final MyDataGrid<Job> dataGrid = new MyDataGrid<>(this);
        dataGrid.setMultiLine(true);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        final RestDataProvider<Job, ResultPage<Job>> dataProvider = createDataProvider(
                eventBus,
                view,
                restFactory);

        initColumns(restFactory, uiConfigCache, dataGrid);

        dataProvider.addDataDisplay(dataGrid);
    }

    private static RestDataProvider<Job, ResultPage<Job>> createDataProvider(final EventBus eventBus,
                                                                             final PagerView view,
                                                                             final RestFactory restFactory) {
        return new RestDataProvider<Job, ResultPage<Job>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Job>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                restFactory
                        .create(JOB_RESOURCE)
                        .method(JobResource::list)
                        .onSuccess(dataConsumer)
                        .onFailure(restErrorHandler)
                        .taskMonitorFactory(view)
                        .exec();
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
    }

    private void initColumns(final RestFactory restFactory,
                             final UiConfigCache uiConfigCache,
                             final MyDataGrid<Job> dataGrid) {
        // Enabled.
        dataGrid.addResizableColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(Job::isEnabled))
                        .withFieldUpdater(
                                (rowIndex, job, tickBoxState) -> {
                                    job.setEnabled(tickBoxState.toBoolean());
                                    restFactory
                                            .create(JOB_RESOURCE)
                                            .call(res -> {
                                                res.setEnabled(job.getId(), tickBoxState.toBoolean());
                                            })
                                            .onSuccess(aVoid -> {
                                                dataGrid.redrawRow(rowIndex);
                                                JobChangeEvent.fire(JobListPresenter.this, job);
//                                                if (changeHandler != null) {
//                                                    changeHandler.accept(job);
//                                                }
                                            })
                                            .taskMonitorFactory(getView())
                                            .exec();
                                }
                        )
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether this job is enabled. " +
                                     "The parent job and the node must both be enabled for the job to execute.")
                        .build(),
                ColumnSizeConstants.ENABLED_COL);

        // Job name, allow for null rows
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((Job job) -> NullSafe.get(job, Job::getName))
                        .enabledWhen(job -> NullSafe.isTrue(job, Job::isEnabled))
                        .build(),
                DataGridUtil.headingBuilder("Job")
                        .withToolTip("The name of the job")
                        .build(),
                350);

        // Help
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(true, (Job job) -> SvgPresets.HELP)
                        .enabledWhen(job -> NullSafe.isTrue(job, Job::isEnabled))
                        .withBrowserEventHandler((context, elem, row, event) -> {
                            showHelp(uiConfigCache, row);
                        })
                        .build(),
                "<br/>", ColumnSizeConstants.ICON_COL);

        // Description col, allow for null rows
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder((Job job) -> NullSafe.get(job, Job::getDescription))
                        .enabledWhen(job -> NullSafe.isTrue(job, Job::isEnabled))
                        .build(),
                DataGridUtil.headingBuilder("Description")
                        .withToolTip("The description of the job")
                        .build(),
                300);

        DataGridUtil.addEndColumn(dataGrid);
    }

    public MultiSelectionModel<Job> getSelectionModel() {
        return selectionModel;
    }

    public void setChangeHandler(final Consumer<Job> changeHandler) {
        this.changeHandler = changeHandler;
    }


    /**
     * @param name The name of the job
     * @return The name formatted as a markdown anchor, i.e. "My Job" => "#my-job"
     */
    private String formatAnchor(final String name) {
        return "#" + name.replace(' ', '-')
                .toLowerCase();
    }

    private void showHelp(final UiConfigCache uiConfigCache, final Job row) {
        uiConfigCache.get(result -> {
            if (result != null) {
                final String helpUrl = result.getHelpUrlJobs();
                if (!NullSafe.isBlankString(helpUrl)) {
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
            }
        }, getView());
    }

    public void setSelected(final Job job) {
        if (job != null) {
            selectionModel.setSelected(job);
        } else {
            selectionModel.clear();
        }
    }

}
