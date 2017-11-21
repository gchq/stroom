package stroom.apikeys.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.client.NodeToolsPlugin;
import stroom.node.shared.ClientProperties;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.FindUserCriteria;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFramePresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class ApiKeysPlugin extends NodeToolsPlugin {

    private final Provider<IFramePresenter> iFramePresenterProvider;
    private final ContentManager contentManager;
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public ApiKeysPlugin(final EventBus eventBus,
                         final ClientSecurityContext securityContext,
                         final Provider<IFramePresenter> iFramePresenterProvider,
                         final ContentManager contentManager,
                         final ClientPropertyCache clientPropertyCache) {
        super(eventBus, securityContext);
        this.iFramePresenterProvider = iFramePresenterProvider;
        this.contentManager = contentManager;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(FindUserCriteria.MANAGE_USERS_PERMISSION)) {
            clientPropertyCache.get()
                .onSuccess(result -> {
                    final IconMenuItem apiKeysMenuItem;
                    final SvgPreset icon = SvgPresets.PASSWORD;
                    final String apiKeysUi = result.get(ClientProperties.AUTH_UI_URL) + "/tokens";
                    if (apiKeysUi != null && apiKeysUi.trim().length() > 0) {
                        apiKeysMenuItem = new IconMenuItem(5, icon, null, "API Keys", null, true, () -> {
                            final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                                    .title("API Keys")
                                    .href(apiKeysUi)
                                    .target(HyperlinkTarget.STROOM_TAB)
                                    .build();
                            final IFramePresenter iFramePresenter = iFramePresenterProvider.get();
                            iFramePresenter.setHyperlink(hyperlink);
                            contentManager.open(
                                    callback -> ConfirmEvent.fire(
                                            ApiKeysPlugin.this,
                                            "Are you sure you want to close " + hyperlink.getTitle() + "?",
                                            callback::closeTab),
                                    iFramePresenter, iFramePresenter);
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
