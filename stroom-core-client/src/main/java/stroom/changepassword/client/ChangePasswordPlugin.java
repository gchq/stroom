package stroom.changepassword.client;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

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
                    final String changePasswordUiUrl = result.getUrl().getChangepassword();
                    if (changePasswordUiUrl != null && changePasswordUiUrl.trim().length() > 0) {
                        changePasswordMenuItem = new IconMenuItem(5, icon, null, "Change password", null, true, () -> {
//                            final Hyperlink hyperlink = new Builder()
//                                    .text("Change password")
//                                    .href(changePasswordUiUrl)
//                                    .type(HyperlinkType.TAB + "|Change password")
//                                    .icon(icon)
//                                    .build();
//                            HyperlinkEvent.fire(this, hyperlink);
                            postMessage();

                        });
                    } else {
                        changePasswordMenuItem = new IconMenuItem(5, icon, icon, "'Change Password' is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.USER_MENU, changePasswordMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(ChangePasswordPlugin.this, caught.getMessage(), null));
    }

    /**
     * Sort a native integer array numerically.
     *
     * @param array the array to sort
     */
    private static native void postMessage() /*-{
        window.top.postMessage(
            JSON.stringify({
                message: "changePassword"
             }
        ), '*');
    }-*/;
}
