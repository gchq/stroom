/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.security.client;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.ui.config.client.UiConfigCache;

public class ManageUserPlugin extends NodeToolsPlugin {
    private final UiConfigCache clientPropertyCache;

    @Inject
    public ManageUserPlugin(final EventBus eventBus,
                            final ClientSecurityContext securityContext,
                            final UiConfigCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        // TODO Add a menu item to present the authorisation manager from the new UI in an iFrame
    }
}
