package stroom.users.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.properties.global.client.ClientPropertyCache;
import stroom.node.client.NodeToolsPlugin;
import stroom.properties.shared.ClientProperties;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class UsersPlugin extends NodeToolsPlugin {
    private final Provider<IFrameContentPresenter> presenterProvider;
    private final ContentManager contentManager;
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public UsersPlugin(final EventBus eventBus,
                       final ClientSecurityContext securityContext,
                       final Provider<IFrameContentPresenter> presenterProvider,
                       final ContentManager contentManager,
                       final ClientPropertyCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.presenterProvider = presenterProvider;
        this.contentManager = contentManager;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            clientPropertyCache.get()
                    .onSuccess(result -> {
                        final IconMenuItem usersMenuItem;
                        final SvgPreset icon = SvgPresets.USER_GROUP;
                        final String usersUiUrl = result.get(ClientProperties.USERS_UI_URL);
                        if (usersUiUrl != null && usersUiUrl.trim().length() > 0) {
                            usersMenuItem = new IconMenuItem(5, icon, null, "Users", null, true, () -> {
                                final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                                        .title("Users")
                                        .href(usersUiUrl)
                                        .target(HyperlinkTarget.STROOM_TAB)
                                        .build();
                                final IFrameContentPresenter presenter = presenterProvider.get();
                                presenter.setHyperlink(hyperlink);
                                presenter.setIcon(icon);
                                contentManager.open(
                                        callback -> {
                                            callback.closeTab(true);
                                            presenter.close();
                                        },
                                        presenter, presenter);
                            });
                        } else {
                            usersMenuItem = new IconMenuItem(5, icon, icon, "Users is not configured!", null, false, null);
                        }

                        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, usersMenuItem);

                    })
                    .onFailure(caught -> AlertEvent.fireError(UsersPlugin.this, caught.getMessage(), null));
        }
    }
}
