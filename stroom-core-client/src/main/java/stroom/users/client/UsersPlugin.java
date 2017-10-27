package stroom.users.client;

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

public class UsersPlugin extends Plugin {

    private final Provider<IFramePresenter> iFramePresenterProvider;
    private final ContentManager contentManager;
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public UsersPlugin(final EventBus eventBus,
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
                    final IconMenuItem usersMenuItem;
                    final String usersUiUrl = result.get(ClientProperties.USERS_UI_URL);
                    if (usersUiUrl != null && usersUiUrl.trim().length() > 0) {
                        usersMenuItem = new IconMenuItem(5, SvgPresets.EXPLORER, null, "Users", null, true, () -> {
                            final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                                    .title("Users")
                                    .href(usersUiUrl)
                                    .target(HyperlinkTarget.STROOM_TAB)
                                    .build();
                            final IFramePresenter iFramePresenter = iFramePresenterProvider.get();
                            iFramePresenter.setHyperlink(hyperlink);
                            contentManager.open(callback ->
                                            ConfirmEvent.fire(UsersPlugin.this,
                                                    "Are you sure you want to close " + hyperlink.getTitle() + "?",
                                                    callback::closeTab)
                                    , iFramePresenter, iFramePresenter);
                        });
                    } else {
                        usersMenuItem = new IconMenuItem(5, SvgPresets.EXPLORER, SvgPresets.EXPLORER, "Users is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, usersMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(UsersPlugin.this, caught.getMessage(), null));


    }
}
