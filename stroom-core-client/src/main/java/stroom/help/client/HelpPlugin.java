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

package stroom.help.client;

import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.app.client.MenuKeys;
import stroom.app.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.widget.button.client.SvgIcons;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class HelpPlugin extends Plugin {
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public HelpPlugin(final EventBus eventBus, final ClientPropertyCache clientPropertyCache) {
        super(eventBus);
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        clientPropertyCache.get()
                .onSuccess(result -> {
                    IconMenuItem helpMenuItem;
                    final String helpUrl = result.get(ClientProperties.HELP_URL);
                    if (helpUrl != null && helpUrl.trim().length() > 0) {
                        helpMenuItem = new IconMenuItem(1, SvgIcons.HELP, SvgIcons.HELP, "Help", null, true, () -> Window.open(helpUrl, "_blank", ""));
                    } else {
                        helpMenuItem = new IconMenuItem(1, SvgIcons.HELP, SvgIcons.HELP, "Help is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.HELP_MENU, helpMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(HelpPlugin.this, caught.getMessage(), null));
    }
}
