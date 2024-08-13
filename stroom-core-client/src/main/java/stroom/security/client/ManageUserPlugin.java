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

import stroom.core.client.MenuKeys;
import stroom.document.client.event.ShowPermissionsDialogEvent;
import stroom.explorer.shared.ExplorerConstants;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentPermissionsEditPresenter;
import stroom.security.client.presenter.DocumentPermissionsPresenter;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem.Builder;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ManageUserPlugin extends NodeToolsPlugin {


    private final AsyncProvider<DocumentPermissionsEditPresenter> documentPermissionsEditPresenterProvider;

    @Inject
    public ManageUserPlugin(final EventBus eventBus,
                            final ClientSecurityContext securityContext,
                            final AsyncProvider<DocumentPermissionsPresenter> documentPermissionsPresenterProvider,
                            final AsyncProvider<DocumentPermissionsEditPresenter>
                                    documentPermissionsEditPresenterProvider) {
        super(eventBus, securityContext);
        this.documentPermissionsEditPresenterProvider = documentPermissionsEditPresenterProvider;
//        this.usersAndGroupsPresenterProvider = usersAndGroupsPresenterProvider;
//
        // Add handler for showing the document permissions dialog in the explorer tree context menu
        eventBus.addHandler(ShowPermissionsDialogEvent.getType(),
                event -> documentPermissionsPresenterProvider.get(new AsyncCallback<DocumentPermissionsPresenter>() {
                    @Override
                    public void onSuccess(final DocumentPermissionsPresenter presenter) {
                        presenter.show(event.getDocRef());
                    }

                    @Override
                    public void onFailure(final Throwable caught) {
                    }
                }));
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
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            // Menu item for the user/group permissions dialog
            MenuKeys.addSecurityMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU,
                    new Builder()
                            .priority(1)
                            .icon(SvgImage.LOCKED)
                            .text("Document Permissions")
                            .action(getOpenAction())
                            .command(() -> documentPermissionsEditPresenterProvider.get(
                                    new AsyncCallback<DocumentPermissionsEditPresenter>() {
                                        @Override
                                        public void onSuccess(final DocumentPermissionsEditPresenter presenter) {
                                            final ExpressionTerm term = new ExpressionTerm(
                                                    true,
                                                    DocumentPermissionFields.DESCENDANTS.getFldName(),
                                                    Condition.OF_DOC_REF,
                                                    null,
                                                    ExplorerConstants.SYSTEM_DOC_REF);
                                            final ExpressionOperator operator = ExpressionOperator
                                                    .builder()
                                                    .addTerm(term)
                                                    .build();
                                            presenter.show(operator, () -> {
                                            });
                                        }

                                        @Override
                                        public void onFailure(final Throwable caught) {
                                        }
                                    }))
                            .build());
        }
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
