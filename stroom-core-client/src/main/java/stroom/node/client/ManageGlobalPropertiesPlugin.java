package stroom.node.client;

import stroom.config.global.client.presenter.GlobalPropertyTabPresenter;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ManageGlobalPropertiesPlugin extends NodeToolsContentPlugin<GlobalPropertyTabPresenter> {

    @Inject
    ManageGlobalPropertiesPlugin(final EventBus eventBus,
                                 final ContentManager contentManager,
                                 final Provider<GlobalPropertyTabPresenter> presenterProvider,
                                 final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider, securityContext);
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_PROPERTIES_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_PROPERTIES;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addAdministrationMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(
                    MenuKeys.ADMINISTRATION_MENU,
                    new IconMenuItem.Builder()
                            .priority(90)
                            .icon(SvgImage.PROPERTIES)
                            .text("Properties")
                            .action(getOpenAction())
                            .command(super::open)
                            .build());
        }
    }
}
