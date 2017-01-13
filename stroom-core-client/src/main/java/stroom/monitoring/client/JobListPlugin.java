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

package stroom.monitoring.client;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.jobsystem.client.presenter.JobPresenter;
import stroom.jobsystem.shared.Job;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class JobListPlugin extends MonitoringPlugin<JobPresenter> {
    @Inject
    public JobListPlugin(final EventBus eventBus, final ContentManager eventManager,
                         final Provider<JobPresenter> presenterProvider, final ClientSecurityContext securityContext) {
        super(eventBus, eventManager, presenterProvider, securityContext);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(Job.MANAGE_JOBS_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.MONITORING_MENU,
                    new IconMenuItem(9, GlyphIcons.JOBS, GlyphIcons.JOBS, "Jobs", null, true, new Command() {
                        @Override
                        public void execute() {
                            open();
                        }
                    }));
        }
    }
}
