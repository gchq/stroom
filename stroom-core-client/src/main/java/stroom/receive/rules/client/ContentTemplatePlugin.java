/*
 * Copyright 2016 Crown Copyright
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
import stroom.core.client.MonitoringPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.receive.content.client.component.ContentTemplateTabController;
import stroom.receive.content.shared.ContentTemplates;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.MenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ContentTemplatePlugin extends MonitoringPlugin<ContentTemplateTabController> {

    @Inject
    public ContentTemplatePlugin(final EventBus eventBus,
                                 final ContentManager eventManager,
                                 final Provider<ContentTemplateTabController> controllerProvider,
                                 final ClientSecurityContext securityContext,
                                 final DocumentPluginRegistry documentPluginRegistry) {
        super(eventBus, eventManager, controllerProvider, securityContext, documentPluginRegistry);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(getRequiredAppPermission())) {
            MenuKeys.addAdministrationMenu(event.getMenuItems());
            event.getMenuItems()
                    .addMenuItem(MenuKeys.ADMINISTRATION_MENU, createContentTemplateMenuItem());
        }
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return AppPermission.MANAGE_CONTENT_TEMPLATES_PERMISSION;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_CONTENT_TEMPLATES;
    }

    private MenuItem createContentTemplateMenuItem() {
        return new IconMenuItem.Builder()
                .priority(55)
                .icon(ContentTemplates.DOCUMENT_TYPE.getIcon())
                .text("Content Templates")
                .action(getOpenAction())
                .command(this::open)
                .tooltip("Manage the templated content that will be auto-created on data receipt.")
                .build();
    }

    @Override
    public String getType() {
        return ContentTemplateTabController.TAB_TYPE;
    }
}
