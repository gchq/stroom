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

import stroom.about.client.component.AboutController;
import stroom.core.client.MenuKeys;
import stroom.core.client.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.AppRoot;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Separator;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class AboutPlugin extends Plugin {

    private final Provider<AboutController> provider;
    private final AppRoot appRoot;

    @Inject
    public AboutPlugin(final EventBus eventBus,
                       final Provider<AboutController> provider,
                       final AppRoot appRoot) {
        super(eventBus);
        this.provider = provider;
        this.appRoot = appRoot;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        MenuKeys.addHelpMenu(event.getMenuItems());
        event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU, new Separator(10));
        event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU,
                new IconMenuItem.Builder()
                        .priority(11)
                        .icon(SvgImage.OO)
                        .text("About")
                        .command(this::showAbout)
                        .build());
    }

    // The app owns the dialog while it is shown, as a plugin owns nothing
    private void showAbout() {
        provider.get().show(appRoot);
    }
}
