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

package stroom.annotation.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class AnnotationBrowsePlugin extends Plugin {

    private final ClientSecurityContext securityContext;
    private final ContentManager contentManager;
    private final Provider<BrowseAnnotationPresenter> presenterProvider;
    private BrowseAnnotationPresenter presenter;

    @Inject
    public AnnotationBrowsePlugin(final EventBus eventBus,
                                  final ClientSecurityContext securityContext,
                                  final ContentManager contentManager,
                                  final Provider<BrowseAnnotationPresenter> presenterProvider) {
        super(eventBus);
        this.securityContext = securityContext;
        this.contentManager = contentManager;
        this.presenterProvider = presenterProvider;

        final Action openAction = getOpenAction();
        if (openAction != null) {
            final AppPermission requiredAppPermission = AppPermission.ANNOTATIONS;
            final Command  command = () -> {
                if (securityContext.hasAppPermission(requiredAppPermission)) {
                    open();
                }
            };
            KeyBinding.addCommand(openAction, command);
        }
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        if (securityContext.hasAppPermission(AppPermission.ANNOTATIONS)) {
            MenuKeys.addAnnotationMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.ANNOTATION_MENU,
                    new IconMenuItem.Builder()
                            .priority(0)
                            .icon(SvgImage.EYE)
                            .iconColour(IconColour.GREY)
                            .text("Browse Annotations")
                            .action(getOpenAction())
                            .command(this::open)
                            .build());
        }
    }

    private void open() {
        if (presenter == null) {
            // If the presenter is null then we haven't got this tab open.
            // Create a new presenter.
            presenter = presenterProvider.get();
        }

        final CloseContentEvent.Handler closeHandler = event -> {
            // Give the content manager the ok to close the tab.
            event.getCallback().closeTab(true);

            // After we close the tab set the presenter back to null so
            // that we can open it again.
            presenter = null;
        };

        // Tell the content manager to open the tab.
        final TabData tabData = presenter;
        presenter.refresh();
        contentManager.open(closeHandler, tabData, presenter);
    }

    protected Action getOpenAction() {
        return Action.GOTO_ANNOTATIONS;
    }
}
