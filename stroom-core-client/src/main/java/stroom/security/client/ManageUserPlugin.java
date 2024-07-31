/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.security.client;

import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentPermissionsPresenter;
import stroom.security.shared.AppPermission;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ManageUserPlugin extends NodeToolsPlugin {


    @Inject
    public ManageUserPlugin(final EventBus eventBus, final ClientSecurityContext securityContext,
                            final AsyncProvider<DocumentPermissionsPresenter> documentPermissionsPresenterProvider) {
        super(eventBus, securityContext);
//        this.usersAndGroupsPresenterProvider = usersAndGroupsPresenterProvider;
//
//        // Add handler for showing the document permissions dialog in the explorer tree context menu
//        eventBus.addHandler(ShowPermissionsDialogEvent.getType(),
//                event -> documentPermissionsPresenterProvider.get(new AsyncCallback<DocumentPermissionsPresenter>() {
//                    @Override
//                    public void onSuccess(final DocumentPermissionsPresenter presenter) {
//                        presenter.show(event.getExplorerNode());
//                    }
//
//                    @Override
//                    public void onFailure(final Throwable caught) {
//                    }
//                }));
//
//        final Action openAction = getOpenAction();
//        if (openAction != null) {
//            final String requiredAppPermission = getRequiredAppPermission();
//            final Command command;
//            if (requiredAppPermission != null) {
//                command = () -> {
//                    if (getSecurityContext().hasAppPermission(requiredAppPermission)) {
//                        open();
//                    }
//                };
//            } else {
//                command = this::open;
//            }
//            KeyBinding.addCommand(openAction, command);
//        }
    }

    private AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_USERS_PERMISSION;
    }

    private Action getOpenAction() {
        return Action.GOTO_APP_PERMS;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
//        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
//            // Menu item for the user/group permissions dialog
//            MenuKeys.addSecurityMenu(event.getMenuItems());
//            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU,
//                    new IconMenuItem.Builder()
//                            .priority(1)
//                            .icon(SvgImage.USER)
//                            .text("Application Permissions")
//                            .action(getOpenAction())
//                            .command(this::open)
//                            .build());
//        }
    }

//    private void open() {
//        usersAndGroupsPresenterProvider.get(new AsyncCallback<UsersAndGroupsPresenter>() {
//            @Override
//            public void onSuccess(final UsersAndGroupsPresenter presenter) {
//                final PopupSize popupSize = PopupSize.resizable(1_100, 800);
//                ShowPopupEvent.builder(presenter)
//                        .popupType(PopupType.CLOSE_DIALOG)
//                        .popupSize(popupSize)
//                        .caption("Application Permissions")
//                        .onShow(e -> presenter.focus())
//                        .fire();
//            }
//
//            @Override
//            public void onFailure(final Throwable caught) {
//            }
//        });
//    }
}
