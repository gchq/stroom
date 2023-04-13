package stroom.apikeys.client;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;

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
            clientPropertyCache.get()
                    .onSuccess(result -> {
                        final IconMenuItem apiKeysMenuItem;
                        final Preset icon = SvgPresets.KEY;
                        apiKeysMenuItem = new IconMenuItem.Builder()
                                .priority(5)
                                .icon(icon)
                                .text("API Keys")
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
                        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, apiKeysMenuItem);
                    })
                    .onFailure(caught ->
                            AlertEvent.fireError(
                                    ApiKeysPlugin.this,
                                    caught.getMessage(),
                                    null));
        }
    }
}
