package stroom.security.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUsersAndGroupsScreenEvent;
import stroom.security.client.presenter.UserAndGroupsPresenter;
import stroom.security.shared.AppPermission;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem.Builder;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class UsersAndGroupsPlugin extends MonitoringPlugin<UserAndGroupsPresenter> {

    public static final String SCREEN_NAME = "Users and Groups";
    public static final Preset ICON = SvgPresets.USER_GROUP;

    @Inject
    public UsersAndGroupsPlugin(final EventBus eventBus,
                                final ContentManager contentManager,
                                final Provider<UserAndGroupsPresenter> presenterProvider,
                                final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider, securityContext);

        registerHandler(getEventBus().addHandler(OpenUsersAndGroupsScreenEvent.getType(), event -> {
            open(userAndGroupsPresenter ->
                    userAndGroupsPresenter.showUser(event.getUserRef()));
        }));
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU,
                    new Builder()
                            .priority(30)
                            .icon(ICON)
                            .text(SCREEN_NAME)
                            .action(getOpenAction())
                            .command(this::open)
                            .build());
        }
    }

    @Override
    public void open(final Consumer<UserAndGroupsPresenter> consumer) {
        super.open(presenter -> {
            presenter.refresh();
            consumer.accept(presenter);
        });
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_USERS_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_USER_GROUPS;
    }
}
