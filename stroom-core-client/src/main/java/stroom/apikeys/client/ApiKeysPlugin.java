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
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFramePresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class ApiKeysPlugin extends Plugin {

    private final Provider<IFramePresenter> iFramePresenterProvider;
    private final ContentManager contentManager;
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public ApiKeysPlugin(final EventBus eventBus,
                         final Provider<IFramePresenter> iFramePresenterProvider,
                         final ContentManager contentManager,
                         final ClientPropertyCache clientPropertyCache) {
        super(eventBus);
        this.iFramePresenterProvider = iFramePresenterProvider;
        this.contentManager = contentManager;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(BeforeRevealMenubarEvent.getType(), this));
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        clientPropertyCache.get()
                .onSuccess(result -> {
                    final IconMenuItem apiKeysMenuItem;
                    final String apiKeysUi = result.get(ClientProperties.API_KEYS_UI);
                    if (apiKeysUi != null && apiKeysUi.trim().length() > 0) {
                        apiKeysMenuItem = new IconMenuItem(5, SvgPresets.EXPLORER, null, "API Keys", null, true, () -> {
                            final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                                    .title("API Keys")
                                    .href(apiKeysUi)
                                    .target(HyperlinkTarget.STROOM_TAB)
                                    .build();
                            final IFramePresenter iFramePresenter = iFramePresenterProvider.get();
                            iFramePresenter.setHyperlink(hyperlink);
                            contentManager.open(callback ->
                                            ConfirmEvent.fire(ApiKeysPlugin.this,
                                                    "Are you sure you want to close " + hyperlink.getTitle() + "?",
                                                    callback::closeTab)
                                    , iFramePresenter, iFramePresenter);
                        });
                    } else {
                        apiKeysMenuItem = new IconMenuItem(5, SvgPresets.EXPLORER, SvgPresets.EXPLORER, "API Keys is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, apiKeysMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(ApiKeysPlugin.this, caught.getMessage(), null));


    }
}
