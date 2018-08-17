package stroom.changepassword.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.properties.global.client.ClientPropertyCache;
import stroom.properties.shared.ClientProperties;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class ChangePasswordPlugin extends Plugin {

    private final Provider<IFrameContentPresenter> presenterProvider;
    private final ContentManager contentManager;
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public ChangePasswordPlugin(final EventBus eventBus,
                                final Provider<IFrameContentPresenter> presenterProvider,
                                final ContentManager contentManager,
                                final ClientPropertyCache clientPropertyCache) {
        super(eventBus);
        this.presenterProvider = presenterProvider;
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
                    final IconMenuItem changePasswordMenuItem;
                    final SvgPreset icon = SvgPresets.PASSWORD;
                    final String changePasswordUiUrl = result.get(ClientProperties.CHANGE_PASSWORD_UI_URL);
                    if (changePasswordUiUrl != null && changePasswordUiUrl.trim().length() > 0) {
                        changePasswordMenuItem = new IconMenuItem(5, icon, null, "Change password", null, true, () -> {
                            final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                                    .title("Change password")
                                    .href(changePasswordUiUrl)
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
                        changePasswordMenuItem = new IconMenuItem(5, icon, icon, "'Change Password' is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.USER_MENU, changePasswordMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(ChangePasswordPlugin.this, caught.getMessage(), null));
    }
}
