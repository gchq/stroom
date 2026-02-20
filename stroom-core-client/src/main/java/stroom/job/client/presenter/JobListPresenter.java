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
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Top pane of JobPresenter (Jobs tab). Lists jobs (i.e. the parent job)
 */
public class JobListPresenter extends MyPresenterWidget<PagerView> {

    private static final JobResource JOB_RESOURCE = GWT.create(JobResource.class);

    private final MultiSelectionModelImpl<JobWrapper> selectionModel;

    @Inject
    public JobListPresenter(final EventBus eventBus,
                            final PagerView view,
                            final RestFactory restFactory,
                            final UiConfigCache uiConfigCache) {
        super(eventBus, view);

        final MyDataGrid<JobWrapper> dataGrid = new MyDataGrid<>(this);
        dataGrid.setMultiLine(true);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        final RestDataProvider<JobWrapper, ResultPage<JobWrapper>> dataProvider = createDataProvider(
                eventBus,
                view,
                restFactory);

        initColumns(restFactory, uiConfigCache, dataGrid);

        dataProvider.addDataDisplay(dataGrid);
    }

    private static RestDataProvider<JobWrapper, ResultPage<JobWrapper>> createDataProvider(final EventBus eventBus,
                                                                                           final PagerView view,
                                                                                           final RestFactory restFactory) {
        return new RestDataProvider<JobWrapper, ResultPage<JobWrapper>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<JobWrapper>> dataConsumer,
                                final RestErrorHandler restErrorHandler) {
                restFactory
                        .create(JOB_RESOURCE)
                        .method(JobResource::list)
                        .onSuccess(result -> {
                            final List<JobWrapper> values = result
                                    .getValues()
                                    .stream()
                                    .map(JobWrapper::new)
                                    .collect(Collectors.toList());
                            dataConsumer.accept(new ResultPage<JobWrapper>(values, result.getPageResponse()));
                        })
                        .onFailure(restErrorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }

            @Override
            protected void changeData(final ResultPage<JobWrapper> data) {
                final List<JobWrapper> rtnList = new ArrayList<>();
                boolean addedGap = false;
                for (int i = 0; i < data.size(); i++) {
                    rtnList.add(data.getValues().get(i));
                    if (data.getValues().get(i).getJob().isAdvanced() && !addedGap) {
                        // Add a gap between the non-advanced and advanced jobs
                        rtnList.add(i, null);
                        addedGap = true;
                    }
                }

                final ResultPage<JobWrapper> modifiedData = new ResultPage<>(rtnList);
                super.changeData(modifiedData);
            }
        };
    }

    private void initColumns(final RestFactory restFactory,
                             final UiConfigCache uiConfigCache,
                             final MyDataGrid<JobWrapper> dataGrid) {
        final Function<JobWrapper, Boolean> function = ref -> ref.getJob().isEnabled();
        final Function<JobWrapper, TickBoxState> stateFunction = TickBoxState.createTickBoxFunc(function);

        dataGrid.addResizableColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(stateFunction)
                        .withFieldUpdater(
                                (rowIndex, ref, tickBoxState) -> {
                                    final Job updated = ref.getJob().copy().enabled(tickBoxState.toBoolean()).build();
                                    ref.setJob(updated);
                                    restFactory
                                            .create(JOB_RESOURCE)
                                            .call(res -> {
                                                res.setEnabled(updated.getId(), tickBoxState.toBoolean());
                                            })
                                            .onSuccess(aVoid -> {
                                                dataGrid.redrawRow(rowIndex);
                                                JobChangeEvent.fire(JobListPresenter.this, updated);
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
                DataGridUtil.textColumnBuilder((JobWrapper job) ->
                                NullSafe.get(job, JobWrapper::getJob, Job::getName))
                        .enabledWhen(job ->
                                NullSafe.getOrElse(job, JobWrapper::getJob, Job::isEnabled, false))
                        .build(),
                DataGridUtil.headingBuilder("Job")
                        .withToolTip("The name of the job")
                        .build(),
                350);

        // Help
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(true, (JobWrapper job) ->
                                SvgPresets.HELP)
                        .enabledWhen(job ->
                                NullSafe.getOrElse(job, JobWrapper::getJob, Job::isEnabled, false))
                        .withBrowserEventHandler((context, elem, row, event) -> {
                            showHelp(uiConfigCache, row);
                        })
                        .build(),
                "<br/>", ColumnSizeConstants.ICON_COL);

        // Description col, allow for null rows
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder((JobWrapper job) ->
                                NullSafe.get(job, JobWrapper::getJob, Job::getDescription))
                        .enabledWhen(job ->
                                NullSafe.getOrElse(job, JobWrapper::getJob, Job::isEnabled, false))
                        .build(),
                DataGridUtil.headingBuilder("Description")
                        .withToolTip("The description of the job")
                        .build(),
                300);

        DataGridUtil.addEndColumn(dataGrid);
    }

    public MultiSelectionModel<JobWrapper> getSelectionModel() {
        return selectionModel;
    }

    /**
     * @param name The name of the job
     * @return The name formatted as a markdown anchor, i.e. "My Job" => "#my-job"
     */
    private String formatAnchor(final String name) {
        return "#" + name.replace(' ', '-')
                .toLowerCase();
    }

    private void showHelp(final UiConfigCache uiConfigCache, final JobWrapper row) {
        uiConfigCache.get(result -> {
            if (result != null) {
                final String helpUrl = result.getHelpUrlJobs();
                if (!NullSafe.isBlankString(helpUrl)) {
                    // This is a bit fragile as if the headings change in the docs then the anchors
                    // won't work
                    final String url = helpUrl + formatAnchor(row.getJob().getName());
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
            selectionModel.setSelected(new JobWrapper(job));
        } else {
            selectionModel.clear();
        }
    }

    public static class JobWrapper {

        private Job job;

        public JobWrapper(final Job job) {
            this.job = job;
        }

        public Job getJob() {
            return job;
        }

        public void setJob(final Job job) {
            this.job = job;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final JobWrapper that = (JobWrapper) o;
            return Objects.equals(job.getId(), that.job.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(job.getId());
        }
    }
}
