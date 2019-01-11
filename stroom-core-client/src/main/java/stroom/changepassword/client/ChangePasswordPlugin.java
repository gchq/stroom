package stroom.changepassword.client;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.Hyperlink.Builder;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.HyperlinkType;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class ChangePasswordPlugin extends Plugin {
    private final UiConfigCache clientPropertyCache;

    @Inject
    public ChangePasswordPlugin(final EventBus eventBus,
                                final UiConfigCache clientPropertyCache) {
        super(eventBus);
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
                    final String changePasswordUiUrl = result.getUrlConfig().getChangepassword();
                    if (changePasswordUiUrl != null && changePasswordUiUrl.trim().length() > 0) {
                        changePasswordMenuItem = new IconMenuItem(5, icon, null, "Change password", null, true, () -> {
                            final Hyperlink hyperlink = new Builder()
                                    .text("Change password")
                                    .href(changePasswordUiUrl)
                                    .type(HyperlinkType.TAB + "|Change password")
                                    .icon(icon)
                                    .build();
                            HyperlinkEvent.fire(this, hyperlink);
                        });
                    } else {
                        changePasswordMenuItem = new IconMenuItem(5, icon, icon, "'Change Password' is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.USER_MENU, changePasswordMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(ChangePasswordPlugin.this, caught.getMessage(), null));
    }
}
