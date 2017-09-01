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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.about.client.presenter.AboutPresenter;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.KeyedParentMenuItem;
import stroom.widget.menu.client.presenter.Separator;

public class AboutPlugin extends Plugin {
    private final Provider<AboutPresenter> provider;

    @Inject
    public AboutPlugin(final EventBus eventBus, final Provider<AboutPresenter> provider) {
        super(eventBus);
        this.provider = provider;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(BeforeRevealMenubarEvent.getType(), this));
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem(5, "Help", event.getMenuItems(), MenuKeys.HELP_MENU));
        event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU, new Separator(2));
        event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU,
                new IconMenuItem(3, SvgPresets.ABOUT, null, "About", null, true, () -> provider.get().forceReveal()));
    }
}