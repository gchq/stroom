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

package stroom.importexport.client;

import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.MonitoringPlugin;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent.DependencyType;
import stroom.importexport.client.presenter.DependenciesTabPresenter;
import stroom.importexport.shared.DependencyCriteria;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class DependenciesPlugin extends MonitoringPlugin<DependenciesTabPresenter> {

    @Inject
    public DependenciesPlugin(final EventBus eventBus,
                              final ContentManager eventManager,
                              final Provider<DependenciesTabPresenter> presenterProvider,
                              final ClientSecurityContext securityContext) {
        super(eventBus, eventManager, presenterProvider, securityContext);
    }

    @Override
    protected void onBind() {
        super.onBind();

        // Open the Dependencies view, filtered by a particular DocRef
        registerHandler(getEventBus().addHandler(ShowDocRefDependenciesEvent.getType(), event -> {
            open(presenter -> {
                final String field = DependencyType.DEPENDANT.equals(event.getDependencyType())
                        ? DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier()
                        : DependencyCriteria.FIELD_DEF_FROM_UUID.getFilterQualifier();
                presenter.setQuickFilterText(field + ":" + event.getDocRef().getUuid());
            });
        }));
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                new IconMenuItem.Builder()
                        .priority(150)
                        .icon(SvgImage.DEPENDENCIES)
                        .action(getOpenAction())
                        .text("Dependencies")
                        .command(this::open)
                        .build());
    }

    @Override
    protected AppPermission getRequiredAppPermission() {
        return null;
    }

    @Override
    protected Action getOpenAction() {
        return Action.GOTO_DEPENDENCIES;
    }
}
