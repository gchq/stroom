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

package stroom.help.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class HelpPlugin extends Plugin {

    private final UiConfigCache clientPropertyCache;

    @Inject
    public HelpPlugin(final EventBus eventBus, final UiConfigCache clientPropertyCache) {
        super(eventBus);
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        clientPropertyCache.get(result -> {
            if (result != null) {
                final IconMenuItem helpMenuItem;
                final String helpUrl = result.getHelpUrl();
                if (NullSafe.isNonBlankString(helpUrl)) {
                    helpMenuItem = new IconMenuItem.Builder()
                            .priority(1)
                            .icon(SvgImage.HELP)
                            .text("Help")
                            .command(() -> Window.open(helpUrl, "_blank", ""))
                            .build();
                } else {
                    helpMenuItem = new IconMenuItem.Builder()
                            .priority(1)
                            .icon(SvgImage.HELP)
                            .text("Help is not configured!")
                            .build();
                }

                event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU, helpMenuItem);
            }
        }, new DefaultTaskMonitorFactory(this));

        final IconMenuItem apiMenuItem = new IconMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.OPERATOR)
                .text("API Specification")
                .command(() ->
                        Window.open("/swagger-ui", "_blank", ""))
                .build();
        event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU, apiMenuItem);
    }
}
