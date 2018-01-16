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

package stroom.importexport.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.importexport.client.presenter.DependenciesPresenter;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.monitoring.client.MonitoringPlugin;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class DependenciesPlugin extends MonitoringPlugin<DependenciesPresenter> {
    @Inject
    public DependenciesPlugin(final EventBus eventBus, final ContentManager eventManager,
                              final Provider<DependenciesPresenter> presenterProvider,
                              final ClientSecurityContext securityContext) {
        super(eventBus, eventManager, presenterProvider, securityContext);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
//        if (getSecurityContext().hasAppPermission(DBTableStatus.MANAGE_DB_PERMISSION)) {
        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                new IconMenuItem(150, SvgPresets.DEPENDENCIES, SvgPresets.DEPENDENCIES, "Dependencies", null, true, this::open));
//        }
    }
}
