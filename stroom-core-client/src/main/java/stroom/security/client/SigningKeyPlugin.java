/*
 * Copyright 2023 Crown Copyright
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

package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.SigningKeyPresenter;
import stroom.security.shared.AppPermission;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

@Singleton
public class SigningKeyPlugin extends MonitoringPlugin<SigningKeyPresenter> {

    public static final String SCREEN_NAME = "Signing Keys";
    public static final Preset ICON = SvgPresets.KEY;

    @Inject
    public SigningKeyPlugin(final EventBus eventBus,
                            final ContentManager eventManager,
                            final ClientSecurityContext securityContext,
                            final Provider<SigningKeyPresenter> signingKeyPresenterProvider,
                            final DocumentPluginRegistry documentPluginRegistry) {
        super(eventBus, eventManager, signingKeyPresenterProvider, securityContext, documentPluginRegistry);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            addMenuItem(event);
        }
    }

    /**
     * The same gate the server enforces. Revoking a signing key signs out everyone using the internal
     * identity provider, including the processing user that nodes authenticate to each other with, so this
     * is an application-wide act rather than a user-management one.
     */
    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.ADMINISTRATOR;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_SIGNING_KEYS;
    }

    private void addMenuItem(final BeforeRevealMenubarEvent event) {
        final IconMenuItem signingKeyMenuItem = new IconMenuItem.Builder()
                .priority(66)
                .icon(ICON)
                .text(SCREEN_NAME)
                .action(getOpenAction())
                .command(this::open)
                .build();
        event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, signingKeyMenuItem);
    }

    @Override
    public String getType() {
        return SigningKeyPresenter.TAB_TYPE;
    }
}
