/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
public class AccountsPlugin extends MonitoringPlugin<AccountsPresenter> {

    public static final String SCREEN_NAME = "Manage Accounts";
    public static final Preset ICON = SvgPresets.USER;

    final Provider<UiConfigCache> uiConfigCacheProvider;

    @Inject
    public AccountsPlugin(final EventBus eventBus,
                          final ContentManager eventManager,
                          final ClientSecurityContext securityContext,
                          final Provider<AccountsPresenter> accountsPresenterProvider,
                          final Provider<UiConfigCache> uiConfigCacheProvider) {
        super(eventBus, eventManager, accountsPresenterProvider, securityContext);
        this.uiConfigCacheProvider = uiConfigCacheProvider;

        registerHandler(getEventBus().addHandler(OpenAccountEvent.getType(), event -> {
            open(accountsPresenter ->
                    accountsPresenter.setFilterInput(buildFilterInput(event.getUserId())));
        }));
    }

    private String buildFilterInput(final String userId) {
        return AccountFields.FIELD_NAME_USER_ID + ":" + userId;
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        uiConfigCacheProvider.get().get(extendedUiConfig -> {
            // We don't show accounts if using an external IDP as all accounts
            // are managed on the IDP
            if (getSecurityContext().hasAppPermission(getRequiredAppPermission())
                && !extendedUiConfig.isExternalIdentityProvider()) {

                MenuKeys.addSecurityMenu(event.getMenuItems());
                addMenuItem(event);
            }
        });
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

    Preset getIcon() {
        return SvgPresets.USER;
    }

    private void addMenuItem(final BeforeRevealMenubarEvent event) {
        final IconMenuItem apiKeysMenuItem;
        apiKeysMenuItem = new IconMenuItem.Builder()
                .priority(10)
                .icon(ICON)
                .text(SCREEN_NAME)
                .action(getOpenAction())
                .command(this::open)
                .build();
        event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, apiKeysMenuItem);
    }
}
