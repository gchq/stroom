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

package stroom.job.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.job.client.component.JobListController;
import stroom.job.client.component.JobNodeListController;
import stroom.job.client.component.JobWrapper;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class JobPresenter extends ContentTabPresenter<JobPresenter.JobView> implements Refreshable {

    public static final String TAB_TYPE = "Jobs";
    private final JobListController jobList;
    private final JobNodeListController jobNodeListPresenter;

    @Inject
    public JobPresenter(final EventBus eventBus,
                        final JobView view,
                        final JobListController jobList,
                        final JobNodeListController jobNodeListPresenter) {
        super(eventBus, view);
        own(jobList);
        own(jobNodeListPresenter);
        this.jobList = jobList;
        this.jobNodeListPresenter = jobNodeListPresenter;

        getView().setJobListView(jobList);
        getView().setJobNodeListView(jobNodeListPresenter);

        own(jobList.getSelectionModel().addSelectionHandler(event -> {
            final Job row = NullSafe.get(
                    jobList,
                    JobListController::getSelectionModel,
                    MultiSelectionModel::getSelected,
                    JobWrapper::getJob);
            jobNodeListPresenter.read(row);
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

    public void setSelected(final Job job) {
        jobList.setSelected(job);
        jobNodeListPresenter.setSelected(null);
    }

    public void setSelected(final JobNode jobNode) {
        jobList.setSelected(NullSafe.get(jobNode, JobNode::getJob));
        jobNodeListPresenter.setSelected(jobNode);
    }

    @Override
    public void refresh() {
        jobNodeListPresenter.refresh();
    }


    // --------------------------------------------------------------------------------


    public interface JobView extends View {

        /// Shows the list of jobs.
        ///
        /// @param view Its view.
        void setJobListView(IsWidget view);

        /// Shows the selected job on each node.
        ///
        /// @param view Its view.
        void setJobNodeListView(IsWidget view);
    }
}
