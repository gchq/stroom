/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

/// Adds Security > MCP Tool Policies, for those with the Manage MCP Tool Policies permission.
@Singleton
public class AiToolPoliciesPlugin extends MonitoringPlugin<AiToolPoliciesPresenter> {

    /// @param eventBus               The event bus.
    /// @param contentManager         Opens the screen in a tab.
    /// @param presenterProvider      Provides the screen.
    /// @param securityContext        For checking the user's permissions.
    /// @param documentPluginRegistry The document plugins.
    @Inject
    public AiToolPoliciesPlugin(final EventBus eventBus,
                                final ContentManager contentManager,
                                final Provider<AiToolPoliciesPresenter> presenterProvider,
                                final ClientSecurityContext securityContext,
                                final DocumentPluginRegistry documentPluginRegistry) {
        super(eventBus, contentManager, presenterProvider, securityContext, documentPluginRegistry);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, createMenuItem());
        }
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_MCP_TOOL_POLICIES;
    }

    @Override
    protected Action getOpenAction() {
        // The screen has no key binding.
        return null;
    }

    private MenuItem createMenuItem() {
        return new IconMenuItem.Builder()
                .priority(75)
                .icon(SvgPresets.AI)
                .text(AiToolPoliciesPresenter.SCREEN_NAME)
                .command(this::open)
                .tooltip("Manage which of Stroom's tools users and groups may use, in the 'Ask Stroom AI' chat "
                         + "and over MCP.")
                .build();
    }

    @Override
    public String getType() {
        return AiToolPoliciesPresenter.TAB_TYPE;
    }
}
