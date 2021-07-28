package stroom.changepassword.client;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.client.Preset;
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
                    final Preset icon = SvgPresets.PASSWORD;
                    changePasswordMenuItem = new IconMenuItem(
                            5,
                            icon,
                            null,
                            "Change password",
                            null,
                            true,
                            () ->
                                    postMessage("changePassword"));

                    event.getMenuItems().addMenuItem(MenuKeys.USER_MENU, changePasswordMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(ChangePasswordPlugin.this, caught.getMessage(), null));
    }
}
