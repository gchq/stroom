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

package stroom.explorer.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.KeyedParentMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class NavigationPlugin extends Plugin {

    @Inject
    public NavigationPlugin(final EventBus eventBus) {
        super(eventBus);
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        event.getMenuItems().addMenuItem(
                MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem.Builder()
                        .priority(6)
                        .text("Navigation")
                        .menuItems(event.getMenuItems())
                        .menuKey(MenuKeys.NAVIGATION_MENU)
                        .build());

        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(201)
                        .icon(SvgImage.FIND)
                        .text("Find Content")
                        .action(Action.FIND)
                        .command(() -> ShowFindEvent.fire(NavigationPlugin.this))
                        .build());
    }
}
