package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.ApiKeysPresenter;
import stroom.security.shared.PermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ApiKeysPlugin extends MonitoringPlugin<ApiKeysPresenter> {

    @Inject
    public ApiKeysPlugin(final EventBus eventBus,
                         final ContentManager eventManager,
                         final ClientSecurityContext securityContext,
                         final Provider<ApiKeysPresenter> apiKeysPresenterAsyncProvider) {
        super(eventBus, eventManager, apiKeysPresenterAsyncProvider, securityContext);
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            addMenuItem(event);
        }
    }

    @Override
    protected String getRequiredAppPermission() {
        return PermissionNames.MANAGE_API_KEYS;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_API_KEYS;
    }

    private void addMenuItem(final BeforeRevealMenubarEvent event) {
        final IconMenuItem apiKeysMenuItem;
        final SvgImage icon = SvgImage.KEY;
        apiKeysMenuItem = new IconMenuItem.Builder()
                .priority(3)
                .icon(icon)
                .text("Manage API Keys")
                .action(getOpenAction())
                .command(this::open)
                .build();
        event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, apiKeysMenuItem);
    }
}
