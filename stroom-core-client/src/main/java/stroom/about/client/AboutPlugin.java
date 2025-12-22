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

package stroom.about.client;

import stroom.about.client.event.ShowAboutEvent;
import stroom.about.client.event.ShowAboutEvent.ShowAboutHandler;
import stroom.about.client.presenter.AboutPresenter;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Separator;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class AboutPlugin extends Plugin implements ShowAboutHandler {

    private final Provider<AboutPresenter> provider;

    @Inject
    public AboutPlugin(final EventBus eventBus, final Provider<AboutPresenter> provider) {
        super(eventBus);
        this.provider = provider;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(ShowAboutEvent.getType(), this));
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
                        .command(() -> provider.get().forceReveal())
                        .build());
    }

    @Override
    public void onShow(final ShowAboutEvent event) {
        provider.get().show();
    }
}
