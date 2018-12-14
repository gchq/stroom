package stroom.users.client;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.Hyperlink.Builder;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.HyperlinkType;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.client.NodeToolsPlugin;
import stroom.node.shared.ClientProperties;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.FindUserCriteria;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class UsersPlugin extends NodeToolsPlugin {
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public UsersPlugin(final EventBus eventBus,
                       final ClientSecurityContext securityContext,
                       final ClientPropertyCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(FindUserCriteria.MANAGE_USERS_PERMISSION)) {
            clientPropertyCache.get()
                    .onSuccess(result -> {
                        final IconMenuItem usersMenuItem;
                        final SvgPreset icon = SvgPresets.USER_GROUP;
                        final String usersUiUrl = result.get(ClientProperties.USERS_UI_URL);
                        if (usersUiUrl != null && usersUiUrl.trim().length() > 0) {
                            usersMenuItem = new IconMenuItem(5, icon, null, "Users", null, true, () -> {
                                final Hyperlink hyperlink = new Builder()
                                        .text("Users")
                                        .href(usersUiUrl)
                                        .type(HyperlinkType.TAB)
                                        .icon(icon)
                                        .build();
                                HyperlinkEvent.fire(this, hyperlink);
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
