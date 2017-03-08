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
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.content.client.ContentPlugin;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.menu.client.presenter.KeyedParentMenuItem;

public abstract class MonitoringPlugin<P extends MyPresenterWidget<?>> extends ContentPlugin<P> {
    private final ClientSecurityContext securityContext;

    @Inject
    public MonitoringPlugin(final EventBus eventBus, final ContentManager eventManager,
                            final Provider<P> presenterProvider, final ClientSecurityContext securityContext) {
        super(eventBus, eventManager, presenterProvider);
        this.securityContext = securityContext;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem(3, "Monitoring", event.getMenuItems(), MenuKeys.MONITORING_MENU));

        addChildItems(event);
    }

    protected abstract void addChildItems(BeforeRevealMenubarEvent event);

    protected ClientSecurityContext getSecurityContext() {
        return securityContext;
    }
}
