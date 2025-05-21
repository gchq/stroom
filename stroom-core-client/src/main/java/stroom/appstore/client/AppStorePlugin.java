package stroom.appstore.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Separator;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

/**
 * Shows the App Store content pane when the menu is selected.
 * Ensures that the menu item is present and correctly configured.
 */
@Singleton
public class AppStorePlugin extends Plugin {

    /** Used to check whether user can access the App Store */
    private final ClientSecurityContext securityContext;

    /**
     * Injected constructor.
     * @param eventBus GWT event bus
     * @param securityContext Permissions object.
     */
    @SuppressWarnings("unused")
    @Inject
    public AppStorePlugin(final EventBus eventBus,
                          final ClientSecurityContext securityContext) {
        super(eventBus);
        this.securityContext = securityContext;
    }

    /**
     * Called when the menu is revealed. Adds the menu items,
     * @param event Ignored
     */
    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        if (securityContext.hasAppPermission(AppPermission.IMPORT_DATA_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, new Separator((200)));
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                    new IconMenuItem.Builder()
                            .priority(10)
                            .icon(SvgImage.QUESTION)
                            .text("App Store")
                            .build());
        }
    }

}
