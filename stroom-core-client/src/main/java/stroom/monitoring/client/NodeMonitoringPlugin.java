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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.presenter.NodeMonitoringPresenter;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class NodeMonitoringPlugin extends MonitoringPlugin<NodeMonitoringPresenter> {
    @Inject
    public NodeMonitoringPlugin(final EventBus eventBus, final ContentManager eventManager,
                                final Provider<NodeMonitoringPresenter> presenterProvider, final ClientSecurityContext securityContext) {
        super(eventBus, eventManager, presenterProvider, securityContext);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_NODES_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.MONITORING_MENU,
                    new IconMenuItem(10, SvgPresets.NODES, SvgPresets.NODES, "Nodes", null, true, () -> open()));
        }
    }
}
