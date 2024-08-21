package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.document.client.event.ShowPermissionsDialogEvent;
import stroom.explorer.shared.ExplorerConstants;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
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
        eventBus.addHandler(ShowPermissionsDialogEvent.getType(),
                event -> documentPermissionsPresenterProvider.get(new AsyncCallback<DocumentUserPermissionsPresenter>() {
                    @Override
                    public void onSuccess(final DocumentUserPermissionsPresenter presenter) {
                        presenter.show(event.getDocRef());
                    }

                    @Override
                    public void onFailure(final Throwable caught) {
                    }
                }));
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU,
                    new Builder()
                            .priority(30)
                            .icon(SvgImage.LOCKED)
                            .text("Document Permissions")
                            .action(getOpenAction())
                            .command(this::open)
                            .build());
        }
    }

    @Override
    public BatchDocumentPermissionsPresenter open() {
        final BatchDocumentPermissionsPresenter presenter = super.open();
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
        presenter.setExpression(operator);
        return presenter;
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
