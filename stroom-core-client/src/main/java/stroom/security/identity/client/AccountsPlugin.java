package stroom.security.identity.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.identity.client.event.OpenAccountEvent;
import stroom.security.identity.client.presenter.AccountsPresenter;
import stroom.security.identity.shared.AccountFields;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class AccountsPlugin extends MonitoringPlugin<AccountsPresenter> {

    @Inject
    public AccountsPlugin(final EventBus eventBus,
                          final ContentManager eventManager,
                          final ClientSecurityContext securityContext,
                          final Provider<AccountsPresenter> accountsPresenterProvider) {
        super(eventBus, eventManager, accountsPresenterProvider, securityContext);

        registerHandler(getEventBus().addHandler(OpenAccountEvent.getType(), event -> {
            open(accountsPresenter ->
                    accountsPresenter.setFilterInput(buildFilterInput(event.getUserId())));
        }));
    }

    private String buildFilterInput(final String userId) {
        return AccountFields.FIELD_NAME_USER_ID + ":" + userId;
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            addMenuItem(event);
        }
    }

    @Override
    public void open(final Consumer<AccountsPresenter> consumer) {
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
        return Action.GOTO_USER_ACCOUNTS;
    }

    private void addMenuItem(final BeforeRevealMenubarEvent event) {
        final IconMenuItem apiKeysMenuItem;
        final SvgImage icon = SvgImage.USER;
        apiKeysMenuItem = new IconMenuItem.Builder()
                .priority(2)
                .icon(icon)
                .text("Manage Accounts")
                .action(getOpenAction())
                .command(this::open)
                .build();
        event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, apiKeysMenuItem);
    }
}
