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
                        if (!result.isExternalIdentityProvider()) {
                            final IconMenuItem apiKeysMenuItem;
                            final Preset icon = SvgPresets.KEY;
                            apiKeysMenuItem = new IconMenuItem(
                                    5,
                                    icon,
                                    null,
                                    "API Keys",
                                    null,
                                    true,
                                    () ->
                                            postMessage("manageTokens"));
                            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, apiKeysMenuItem);
                        }
                    })
                    .onFailure(caught ->
                            AlertEvent.fireError(
                                    ApiKeysPlugin.this,
                                    caught.getMessage(),
                                    null));
        }
    }
}
