package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserPermissionReportPresenter;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem.Builder;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class UserPermissionsReportPlugin extends MonitoringPlugin<UserPermissionReportPresenter> {

    private final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider;

    @Inject
    public UserPermissionsReportPlugin(final EventBus eventBus,
                                       final ContentManager contentManager,
                                       final Provider<UserPermissionReportPresenter>
                                               userPermissionReportPresenterAsyncProvider,
                                       final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider,
                                       final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, userPermissionReportPresenterAsyncProvider, securityContext);
        this.userRefPopupPresenterProvider = userRefPopupPresenterProvider;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
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

    @Override
    public void open(final Consumer<UserPermissionReportPresenter> consumer) {
        userRefPopupPresenterProvider.get().show(userRef -> {
            super.open(presenter -> {
                presenter.setUserRef(userRef);
                consumer.accept(presenter);
            });
        });
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_USERS_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_USER_PERMISSION_REPORT;
    }
}
