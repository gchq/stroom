package stroom.apikeys.client;

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
import stroom.widget.menu.client.presenter.IconMenuItem;

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
                        final SvgPreset icon = SvgPresets.PASSWORD;
                        final String apiKeysUi = result.getUrlConfig().getApiKeys();
                        if (apiKeysUi != null && apiKeysUi.trim().length() > 0) {
                            apiKeysMenuItem = new IconMenuItem(5, icon, null, "API Keys", null, true, () -> {
                                final Hyperlink hyperlink = new Builder()
                                        .text("API Keys")
                                        .href(apiKeysUi)
                                        .type(HyperlinkType.TAB + "|API Keys")
                                        .icon(icon)
                                        .build();
                                HyperlinkEvent.fire(this, hyperlink);
                            });
                        } else {
                            apiKeysMenuItem = new IconMenuItem(5, icon, icon, "API Keys is not configured!", null, false, null);
                        }
                        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, apiKeysMenuItem);
                    })
                    .onFailure(caught -> AlertEvent.fireError(ApiKeysPlugin.this, caught.getMessage(), null));
        }
    }
}
