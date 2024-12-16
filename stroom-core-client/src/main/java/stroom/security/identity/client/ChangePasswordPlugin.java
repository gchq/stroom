package stroom.security.identity.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.identity.client.presenter.CurrentPasswordPresenter;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ChangePasswordPlugin extends Plugin {

    private final UiConfigCache clientPropertyCache;
    private final Provider<CurrentPasswordPresenter> currentPasswordPresenterProvider;

    @Inject
    public ChangePasswordPlugin(final EventBus eventBus,
                                final UiConfigCache clientPropertyCache,
                                final Provider<CurrentPasswordPresenter> currentPasswordPresenterProvider) {
        super(eventBus);
        this.clientPropertyCache = clientPropertyCache;
        this.currentPasswordPresenterProvider = currentPasswordPresenterProvider;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        clientPropertyCache.get(result -> {
            if (result != null) {
                final SvgImage icon = SvgImage.PASSWORD;
                final IconMenuItem changePasswordMenuItem = new IconMenuItem.Builder()
                        .priority(50)
                        .icon(icon)
                        .iconColour(IconColour.GREY)
                        .text("Change Password")
                        .command(() -> {
                            currentPasswordPresenterProvider.get().show();
                        })
                        .build();

                event.getMenuItems()
                        .addMenuItem(MenuKeys.USER_MENU, changePasswordMenuItem);
            }
        }, new DefaultTaskMonitorFactory(this));
    }
}
