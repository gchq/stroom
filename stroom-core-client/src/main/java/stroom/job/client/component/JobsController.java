/*
 * Copyright 2020 Crown Copyright
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

import stroom.content.client.component.ContentTabController;
import stroom.data.table.client.Refreshable;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the jobs, and the selected job on each node.
public class JobsController extends ContentTabController implements Refreshable {

    public static final String TAB_TYPE = "Jobs";

    private final JobListController jobList;
    private final JobNodeListController jobNodeList;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        Its component.
    /// @param jobList     The job list.
    /// @param jobNodeList The job node list.
    @Inject
    public JobsController(final EventBus eventBus,
                          final Jobs view,
                          final JobListController jobList,
                          final JobNodeListController jobNodeList) {
        super(eventBus);
        initWidget(view);
        own(jobList);
        own(jobNodeList);
        this.jobList = jobList;
        this.jobNodeList = jobNodeList;

        view.setJobListView(jobList);
        view.setJobNodeListView(jobNodeList);

        own(jobList.getSelectionModel().addSelectionHandler(event -> {
            final Job row = NullSafe.get(
                    jobList,
                    JobListController::getSelectionModel,
                    MultiSelectionModel::getSelected,
                    JobWrapper::getJob);
            jobNodeList.read(row);
        }));
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.JOBS;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Jobs";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    /// Selects a job.
    ///
    /// @param job The job.
    public void setSelected(final Job job) {
        jobList.setSelected(job);
        jobNodeList.setSelected(null);
    }

    /// Selects a job on a node.
    ///
    /// @param jobNode The job on its node.
    public void setSelected(final JobNode jobNode) {
        jobList.setSelected(NullSafe.get(jobNode, JobNode::getJob));
        jobNodeList.setSelected(jobNode);
    }

    @Override
    public void refresh() {
        jobNodeList.refresh();
    }
}
