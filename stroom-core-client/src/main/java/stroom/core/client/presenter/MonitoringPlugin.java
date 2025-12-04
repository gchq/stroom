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

package stroom.core.client.presenter;

import stroom.content.client.ContentPlugin;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.widget.menu.client.presenter.KeyedParentMenuItem;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

public abstract class MonitoringPlugin<P extends MyPresenterWidget<?>> extends ContentPlugin<P> {

    private final ClientSecurityContext securityContext;

    @Inject
    public MonitoringPlugin(final EventBus eventBus,
                            final ContentManager contentManager,
                            final Provider<P> presenterProvider,
                            final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider);
        this.securityContext = securityContext;

        final Action openAction = getOpenAction();
        if (openAction != null) {
            final AppPermission requiredAppPermission = getRequiredAppPermission();
            final Command command;
            if (requiredAppPermission != null) {
                command = () -> {
                    if (getSecurityContext().hasAppPermission(requiredAppPermission)) {
                        open();
                    }
                };
            } else {
                command = this::open;
            }
            KeyBinding.addCommand(openAction, command);
        }
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(3)
                        .text("Monitoring")
                        .menuItems(event.getMenuItems())
                        .menuKey(MenuKeys.MONITORING_MENU)
                        .build());

        addChildItems(event);
    }

    protected abstract void addChildItems(BeforeRevealMenubarEvent event);

    protected abstract AppPermission getRequiredAppPermission();

    protected abstract Action getOpenAction();

    protected ClientSecurityContext getSecurityContext() {
        return securityContext;
    }
}
