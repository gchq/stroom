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

package stroom.receive.rules.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.receive.rules.client.presenter.RuleSetPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ReceiveDataRuleSetPlugin extends MonitoringPlugin<RuleSetPresenter> {

    @Inject
    public ReceiveDataRuleSetPlugin(final EventBus eventBus,
                                    final Provider<RuleSetPresenter> editorProvider,
                                    final ContentManager contentManager,
                                    final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, editorProvider, securityContext);
    }

    @Override
    protected void onBind() {
        super.onBind();
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addAdministrationMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.ADMINISTRATION_MENU, createMenuItem());
        }
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_DATA_RECEIPT_RULES_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return null;
    }

    private MenuItem createMenuItem() {
        return new IconMenuItem.Builder()
                .priority(53)
                .icon(SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET)
                .text("Data Receipt Rules")
                .action(getOpenAction())
                .command(this::open)
                .build();
    }
}
