package stroom.apikeys.client;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.KeyedParentMenuItem;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ApiKeysPlugin extends NodeToolsPlugin {

    private final UiConfigCache clientPropertyCache;

    @Inject
    public ApiKeysPlugin(final EventBus eventBus,
                         final ClientSecurityContext securityContext,
                         final UiConfigCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            MenuKeys.addSecurityMenu(event.getMenuItems());

            clientPropertyCache.get()
                    .onSuccess(result -> {
                        final IconMenuItem apiKeysMenuItem;
                        final SvgImage icon = SvgImage.KEY;
                        apiKeysMenuItem = new IconMenuItem.Builder()
                                .priority(3)
                                .icon(icon)
                                .text("Manage API Keys")
                                .command(() -> {
                                    postMessage("manageTokens");

//                                final Hyperlink hyperlink = new Builder()
//                                        .text("API Keys")
//                                        .href(apiKeysUi)
//                                        .type(HyperlinkType.TAB + "|API Keys")
//                                        .icon(icon)
//                                        .build();
//                                HyperlinkEvent.fire(this, hyperlink);
                                })
                                .build();
                        event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, apiKeysMenuItem);
                    })
                    .onFailure(caught ->
                            AlertEvent.fireError(
                                    ApiKeysPlugin.this,
                                    caught.getMessage(),
                                    null));
        }
    }
}
