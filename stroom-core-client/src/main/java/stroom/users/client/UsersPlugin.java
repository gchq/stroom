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
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.UiConfig;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class UsersPlugin extends NodeToolsPlugin {
    private final UiConfigCache clientPropertyCache;

    @Inject
    public UsersPlugin(final EventBus eventBus,
                       final ClientSecurityContext securityContext,
                       final UiConfigCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            clientPropertyCache.get()
                    .onSuccess(uiConfig -> {
                        addManageUsers(event, uiConfig);
                        addManageUserAuthorisations(event, uiConfig);
                        addManageGroupAuthorisations(event, uiConfig);
                    })
                    .onFailure(caught -> AlertEvent.fireError(UsersPlugin.this, caught.getMessage(), null));
        }
    }
    private void addManageUsers(final BeforeRevealMenubarEvent event,
                                final UiConfig uiConfig) {
        final IconMenuItem usersMenuItem;
        final SvgPreset icon = SvgPresets.USER_GROUP;
        final String usersUiUrl = uiConfig.getUrlConfig().getUsers();
        if (usersUiUrl != null && usersUiUrl.trim().length() > 0) {
            usersMenuItem = new IconMenuItem(5, icon, null, "Users", null, true, () -> {
                final Hyperlink hyperlink = new Builder()
                        .text("Users")
                        .href(usersUiUrl)
                        .type(HyperlinkType.TAB + "|Users")
                        .icon(icon)
                        .build();
                HyperlinkEvent.fire(this, hyperlink);
            });
        } else {
            usersMenuItem = new IconMenuItem(5, icon, icon, "Users is not configured!", null, false, null);
        }
        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, usersMenuItem);
    }
    private void addManageUserAuthorisations(final BeforeRevealMenubarEvent event,
                                             final UiConfig uiConfig) {
        final IconMenuItem usersMenuItem;
        final SvgPreset icon = SvgPresets.USER_GROUP;
        final String url = uiConfig.getUrlConfig().getUserAuthorisation();
        if (url != null && url.trim().length() > 0) {
            usersMenuItem = new IconMenuItem(5, icon, null, "User Authorisation", null, true, () -> {
                final Hyperlink hyperlink = new Builder()
                        .text("User Authorisation")
                        .href(url)
                        .type(HyperlinkType.TAB + "|Users Authorisation")
                        .icon(icon)
                        .build();
                HyperlinkEvent.fire(this, hyperlink);
            });
        } else {
            usersMenuItem = new IconMenuItem(5, icon, icon, "Users is not configured!", null, false, null);
        }
        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, usersMenuItem);
    }
    private void addManageGroupAuthorisations(final BeforeRevealMenubarEvent event,
                                             final UiConfig uiConfig) {
        final IconMenuItem usersMenuItem;
        final SvgPreset icon = SvgPresets.USER_GROUP;
        final String url = uiConfig.getUrlConfig().getUserAuthorisation();
        if (url != null && url.trim().length() > 0) {
            usersMenuItem = new IconMenuItem(5, icon, null, "Group Authorisation", null, true, () -> {
                final Hyperlink hyperlink = new Builder()
                        .text("Group Authorisation")
                        .href(url)
                        .type(HyperlinkType.TAB + "|Group Authorisation")
                        .icon(icon)
                        .build();
                HyperlinkEvent.fire(this, hyperlink);
            });
        } else {
            usersMenuItem = new IconMenuItem(5, icon, icon, "Users is not configured!", null, false, null);
        }
        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, usersMenuItem);
    }
}
