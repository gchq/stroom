package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.AppPermissionsPresenter;
import stroom.security.client.presenter.UserAndGroupsPresenter;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem.Builder;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class AppPermissionsPlugin extends MonitoringPlugin<AppPermissionsPresenter> {

    @Inject
    public AppPermissionsPlugin(final EventBus eventBus,
                                final ContentManager contentManager,
                                final Provider<AppPermissionsPresenter> presenterProvider,
                                final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider, securityContext);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU,
                    new Builder()
                            .priority(20)
                            .icon(SvgImage.SHIELD)
                            .text("Application Permissions")
                            .action(getOpenAction())
                            .command(this::open)
                            .build());
        }
    }

    @Override
    public AppPermissionsPresenter open() {
        final AppPermissionsPresenter presenter = super.open();
        presenter.refresh();
        return presenter;
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_USERS_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_APP_PERMS;
    }
}
