package stroom.contentstore.client;

import stroom.content.client.ContentPlugin;
import stroom.contentstore.client.presenter.ContentStorePresenter;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

/**
 * Shows the App Store content pane when the menu is selected.
 * Ensures that the menu item is present and correctly configured.
 */
@Singleton
public class ContentStorePlugin extends ContentPlugin<ContentStorePresenter> {

    /** Used to check whether user can access the App Store */
    private final ClientSecurityContext securityContext;

    /**
     * Injected constructor.
     * @param eventBus GWT event bus
     * @param contentManager Not sure yet...
     * @param presenterProvider The presenter that will be opened when the
     *                          menu button is clicked to show the app store.
     * @param securityContext Permissions object.
     */
    @SuppressWarnings("unused")
    @Inject
    public ContentStorePlugin(final EventBus eventBus,
                              final ContentManager contentManager,
                              final Provider<ContentStorePresenter> presenterProvider,
                              final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider);
        this.securityContext = securityContext;
    }

    /**
     * Called when the menu is revealed. Adds the menu items,
     * @param event Ignored
     */
    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        if (securityContext.hasAppPermission(AppPermission.IMPORT_DATA_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                    new IconMenuItem.Builder()
                            .priority(10)
                            .icon(SvgImage.CONTENT_STORE)
                            .text("Content Store")
                            .command(this::open)
                            .build());
        }
    }

}
