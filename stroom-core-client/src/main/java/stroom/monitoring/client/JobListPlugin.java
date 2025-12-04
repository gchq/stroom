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

package stroom.monitoring.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.job.client.event.OpenJobNodeEvent;
import stroom.job.client.presenter.JobPresenter;
import stroom.job.shared.JobNode;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class JobListPlugin extends MonitoringPlugin<JobPresenter> {

    @Inject
    public JobListPlugin(final EventBus eventBus,
                         final ContentManager eventManager,
                         final Provider<JobPresenter> presenterProvider,
                         final ClientSecurityContext securityContext) {
        super(eventBus, eventManager, presenterProvider, securityContext);

        registerHandler(getEventBus().addHandler(
                OpenJobNodeEvent.getType(), openJobNodeEvent -> {
                    final JobNode jobNode = openJobNodeEvent.getJobNode();
                    open(jobPresenter -> {
                        jobPresenter.setSelected(jobNode);
                    });
                }));
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            event.getMenuItems().addMenuItem(MenuKeys.MONITORING_MENU,
                    new IconMenuItem.Builder()
                            .priority(9)
                            .icon(SvgImage.JOBS)
                            .iconColour(IconColour.GREY)
                            .text("Jobs")
                            .action(getOpenAction())
                            .command(this::open)
                            .build());
        }
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_JOBS_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_JOBS;
    }
}
