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

package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserPermissionReportPresenter;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem.Builder;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class UserPermissionsReportPlugin extends Plugin {

    private final ContentManager contentManager;
    private final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider;
    private final Provider<UserPermissionReportPresenter> userPermissionReportPresenterAsyncProvider;
    private final ClientSecurityContext securityContext;

    @Inject
    public UserPermissionsReportPlugin(final EventBus eventBus,
                                       final ContentManager contentManager,
                                       final Provider<UserPermissionReportPresenter>
                                               userPermissionReportPresenterAsyncProvider,
                                       final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider,
                                       final ClientSecurityContext securityContext) {
        super(eventBus);
        this.contentManager = contentManager;
        this.userRefPopupPresenterProvider = userRefPopupPresenterProvider;
        this.userPermissionReportPresenterAsyncProvider = userPermissionReportPresenterAsyncProvider;
        this.securityContext = securityContext;

        final Action openAction = getOpenAction();
        if (openAction != null) {
            final AppPermission requiredAppPermission = getRequiredAppPermission();
            final Command command;
            if (requiredAppPermission != null) {
                command = () -> {
                    if (securityContext.hasAppPermission(requiredAppPermission)) {
                        open();
                    }
                };
            } else {
                command = this::open;
            }
            KeyBinding.addCommand(openAction, command);
        }
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
//        event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU,
//                new KeyedParentMenuItem.Builder()
//                        .priority(3)
//                        .text("Monitoring")
//                        .menuItems(event.getMenuItems())
//                        .menuKey(MenuKeys.MONITORING_MENU)
//                        .build());
//
//        addChildItems(event);
    }

    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (securityContext.hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU,
                    new Builder()
                            .priority(70)
                            .icon(SvgImage.FILE_RAW)
                            .text("User Permissions Report")
                            .action(getOpenAction())
                            .command(this::open)
                            .build());
        }
    }

    public void open() {
//        userRefPopupPresenterProvider.get().show(userRef -> {
//            final UserPermissionReportPresenter presenter = userPermissionReportPresenterAsyncProvider.get();
//            presenter.setUserRef(userRef);
//            final CloseContentEvent.Handler closeHandler = (event) -> {
//                if (presenter instanceof CloseContentEvent.Handler) {
//                    final Callback callback = ok -> {
//                        event.getCallback().closeTab(ok);
//                    };
//
//                    ((CloseContentEvent.Handler) presenter)
//                            .onCloseRequest(new CloseContentEvent(event.getDirtyMode(), callback));
//                } else {
//                    // Give the content manager the ok to close the tab.
//                    event.getCallback().closeTab(true);
//                }
//            };
//
//            // Tell the content manager to open the tab.
//            contentManager.open(closeHandler, presenter, presenter);
//        });
    }

    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_USERS_PERMISSION;
    }

    protected Action getOpenAction() {
        return Action.GOTO_USER_PERMISSION_REPORT;
    }
}
