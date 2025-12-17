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

package stroom.credentials.client;

import stroom.content.client.ContentPlugin;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.credentials.client.presenter.CredentialsPresenter;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

/**
 * Shows the Credentials UI when the menu item is selected.
 */
@Singleton
public class CredentialsPlugin extends ContentPlugin<CredentialsPresenter> {

    /** Used to check whether user can access the App Store */
    private final ClientSecurityContext securityContext;

    /**
     * Injected constructor.
     * @param eventBus GWT event bus
     * @param contentManager Not sure yet...
     * @param presenterProvider The presenter that will be opened when the
     *                          menu button is clicked to show the app store.
     * @param securityContext Permissions object.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsPlugin(final EventBus eventBus,
                             final ContentManager contentManager,
                             final Provider<CredentialsPresenter> presenterProvider,
                             final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider);
        this.securityContext = securityContext;
    }

    /**
     * Called when the menu is revealed. Adds the menu items,
     * @param event Ignored
     */
    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        if (securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
            // Ensure the security menu is visible
            MenuKeys.addSecurityMenu(event.getMenuItems());

            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU,
                    new IconMenuItem.Builder()
                            .priority(100)
                            .icon(SvgImage.KEY_BLUE)
                            .text("Credentials Manager")
                            .command(this::open)
                            .build());
        }
    }
}
