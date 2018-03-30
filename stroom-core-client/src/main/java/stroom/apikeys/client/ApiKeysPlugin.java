package stroom.apikeys.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.client.NodeToolsPlugin;
import stroom.node.shared.ClientProperties;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.ApplicationPermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class ApiKeysPlugin extends NodeToolsPlugin {

    private final Provider<IFrameContentPresenter> presenterProvider;
    private final ContentManager contentManager;
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public ApiKeysPlugin(final EventBus eventBus,
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
        if (getSecurityContext().hasAppPermission(ApplicationPermissionNames.MANAGE_USERS_PERMISSION)) {
            clientPropertyCache.get()
                    .onSuccess(result -> {
                        final IconMenuItem apiKeysMenuItem;
                        final SvgPreset icon = SvgPresets.PASSWORD;
                        final String apiKeysUi = result.get(ClientProperties.API_KEYS_UI_URL);
                        if (apiKeysUi != null && apiKeysUi.trim().length() > 0) {
                            apiKeysMenuItem = new IconMenuItem(5, icon, null, "API Keys", null, true, () -> {
                                final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                                        .title("API Keys")
                                        .href(apiKeysUi)
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
                            apiKeysMenuItem = new IconMenuItem(5, icon, icon, "API Keys is not configured!", null, false, null);
                        }
                        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, apiKeysMenuItem);
                    })
                    .onFailure(caught -> AlertEvent.fireError(ApiKeysPlugin.this, caught.getMessage(), null));
        }
    }
}
