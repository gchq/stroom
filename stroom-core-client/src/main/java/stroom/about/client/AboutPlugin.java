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

package stroom.about.client;

import stroom.about.client.presenter.AboutPresenter;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.KeyedParentMenuItem;
import stroom.widget.menu.client.presenter.Separator;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class AboutPlugin extends Plugin {

    private final Provider<AboutPresenter> provider;

    @Inject
    public AboutPlugin(final EventBus eventBus, final Provider<AboutPresenter> provider) {
        super(eventBus);
        this.provider = provider;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(5)
                        .text("Help")
                        .menuItems(event.getMenuItems())
                        .menuKey(MenuKeys.HELP_MENU)
                        .build());
        event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU, new Separator(2));
        event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU,
                new IconMenuItem.Builder()
                        .priority(3)
                        .icon(SvgPresets.ABOUT)
                        .text("About")
                        .command(() -> provider.get().forceReveal())
                        .build());
    }
}
