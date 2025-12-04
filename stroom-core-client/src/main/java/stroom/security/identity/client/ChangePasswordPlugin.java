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

package stroom.security.identity.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.identity.client.presenter.CurrentPasswordPresenter;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ChangePasswordPlugin extends Plugin {

    private final UiConfigCache clientPropertyCache;
    private final Provider<CurrentPasswordPresenter> currentPasswordPresenterProvider;

    @Inject
    public ChangePasswordPlugin(final EventBus eventBus,
                                final UiConfigCache clientPropertyCache,
                                final Provider<CurrentPasswordPresenter> currentPasswordPresenterProvider) {
        super(eventBus);
        this.clientPropertyCache = clientPropertyCache;
        this.currentPasswordPresenterProvider = currentPasswordPresenterProvider;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        clientPropertyCache.get(result -> {
            if (result != null) {
                final SvgImage icon = SvgImage.PASSWORD;
                final IconMenuItem changePasswordMenuItem = new IconMenuItem.Builder()
                        .priority(50)
                        .icon(icon)
                        .iconColour(IconColour.GREY)
                        .text("Change Password")
                        .command(() -> {
                            currentPasswordPresenterProvider.get().show();
                        })
                        .build();

                event.getMenuItems()
                        .addMenuItem(MenuKeys.USER_MENU, changePasswordMenuItem);
            }
        }, new DefaultTaskMonitorFactory(this));
    }
}
