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
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.Callback;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.document.client.event.ShowDocumentPermissionsEvent;
import stroom.explorer.shared.ExplorerConstants;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.BatchDocumentPermissionsPresenter;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermissionFields;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem.Builder;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class DocumentPermissionsPlugin extends MonitoringPlugin<BatchDocumentPermissionsPresenter> {

    @Inject
    public DocumentPermissionsPlugin(final EventBus eventBus,
                                     final ContentManager contentManager,
                                     final Provider<BatchDocumentPermissionsPresenter> presenterProvider,
                                     final AsyncProvider<DocumentUserPermissionsPresenter>
                                             documentPermissionsPresenterProvider,
                                     final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider, securityContext);

        // Add handler for showing the document permissions dialog in the explorer tree context menu
        eventBus.addHandler(ShowDocumentPermissionsEvent.getType(), event -> {
            documentPermissionsPresenterProvider.get(new AsyncCallback<DocumentUserPermissionsPresenter>() {
                @Override
                public void onSuccess(final DocumentUserPermissionsPresenter presenter) {
                    presenter.setDocRef(event.getDocRef());
                    final CloseContentEvent.Handler closeHandler = (event) -> {
                        if (presenter instanceof CloseContentEvent.Handler) {
                            final Callback callback = ok -> {
                                event.getCallback().closeTab(ok);
                            };

                            ((CloseContentEvent.Handler) presenter)
                                    .onCloseRequest(new CloseContentEvent(event.getDirtyMode(), callback));
                        } else {
                            // Give the content manager the ok to close the tab.
                            event.getCallback().closeTab(true);
                        }
                    };

                    // Tell the content manager to open the tab.
                    contentManager.open(closeHandler, presenter, presenter);
                }

                @Override
                public void onFailure(final Throwable caught) {
                }
            });
        });
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU,
                    new Builder()
                            .priority(50)
                            .icon(SvgImage.LOCKED)
                            .text("Document Permissions")
                            .action(getOpenAction())
                            .command(this::open)
                            .build());
        }
    }

    @Override
    public void open(final Consumer<BatchDocumentPermissionsPresenter> consumer) {
        super.open(presenter -> {
            final ExpressionTerm term = ExpressionTerm
                    .builder()
                    .field(DocumentPermissionFields.DESCENDANTS.getFldName())
                    .condition(Condition.OF_DOC_REF)
                    .docRef(ExplorerConstants.SYSTEM_DOC_REF)
                    .build();
            final ExpressionOperator operator = ExpressionOperator
                    .builder()
                    .addTerm(term)
                    .build();
            presenter.setExpression(operator);
            consumer.accept(presenter);
        });
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_USERS_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_DOC_PERMS;
    }
}
