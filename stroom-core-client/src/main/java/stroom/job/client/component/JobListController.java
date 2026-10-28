/*
 * Copyright 2019 Crown Copyright
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

package stroom.job.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.job.client.event.JobChangeEvent;
import stroom.job.shared.Job;
import stroom.job.shared.JobResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The top pane of the Jobs tab ([JobList]): every job, the advanced ones after a gap; a job is
/// enabled or disabled with its tick box, and its help opens in a new tab.
public class JobListController extends Controller {

    private static final JobResource JOB_RESOURCE = GWT.create(JobResource.class);

    private final JobList view;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus      The application's event bus.
    /// @param view          The content.
    /// @param restFactory   Lists the jobs and saves whether they are enabled.
    /// @param uiConfigCache Gives the jobs' help link.
    @Inject
    public JobListController(final EventBus eventBus,
                             final JobList view,
                             final RestFactory restFactory,
                             final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        initWidget(view);
        view.setOnEnabledChange(this::onEnabledChange);
        view.setOnHelp(this::showHelp);
        view.setDataProvider(createDataProvider(eventBus));
    }

    /// @return The selection, one job at most.
    public MultiSelectionModel<JobWrapper> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// @param job The job to select, or `null` for none.
    public void setSelected(final Job job) {
        if (job != null) {
            view.getSelectionModel().setSelected(new JobWrapper(job));
        } else {
            view.getSelectionModel().clear();
        }
    }

    private RestDataProvider<JobWrapper, ResultPage<JobWrapper>> createDataProvider(final EventBus eventBus) {
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
                        .owner(JobListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
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

    private void onEnabledChange(final int rowIndex, final JobWrapper ref, final boolean enabled) {
        final Job updated = ref.getJob().copy().enabled(enabled).build();
        ref.setJob(updated);
        restFactory
                .create(JOB_RESOURCE)
                .call(res -> {
                    res.setEnabled(updated.getId(), enabled);
                })
                .onSuccess(aVoid -> {
                    view.redrawRow(rowIndex);
                    JobChangeEvent.fire(JobListController.this, updated);
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    /// @param name The name of the job
    /// @return The name formatted as a markdown anchor, i.e. "My Job" => "#my-job"
    private String formatAnchor(final String name) {
        return "#" + name.replace(' ', '-')
                .toLowerCase();
    }

    private void showHelp(final JobWrapper row) {
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
                            JobListController.this,
                            "Help is not configured!",
                            null);
                }
            }
        }, view.getTaskMonitor());
    }
}
