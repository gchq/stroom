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

package stroom.query.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.query.client.presenter.ResultStorePresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.CurrentUserChangedEvent.CurrentUserChangedHandler;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ResultStorePlugin extends Plugin implements CurrentUserChangedHandler, HasHandlers {

    private final ClientSecurityContext securityContext;

    private final ResultStorePresenter resultStorePresenter;
    private final ResultStoreModel resultStoreModel;

    @Inject
    public ResultStorePlugin(final EventBus eventBus,
                             final ClientSecurityContext securityContext,
                             final ResultStorePresenter resultStorePresenter,
                             final ResultStoreModel resultStoreModel) {
        super(eventBus);
        this.securityContext = securityContext;
        this.resultStorePresenter = resultStorePresenter;
        this.resultStoreModel = resultStoreModel;

        registerHandler(getEventBus().addHandler(CurrentUserChangedEvent.getType(), this));
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        // Add items to the tools menu.
//        if (securityContext.hasAppPermission("Import Configuration")) {
        event.getMenuItems().addMenuItem(MenuKeys.MONITORING_MENU,
                new IconMenuItem.Builder()
                        .priority(201)
                        .icon(SvgPresets.DATABASE)
                        .text("Search Results")
                        .command(resultStorePresenter::show)
                        .build());
//        }
    }

    @Override
    public void onCurrentUserChanged(final CurrentUserChangedEvent event) {
        // TODO : Decide if we want to know about search result stores that we own being presented at login.
        // This is related to general session restoration rather than search results specifically.

//        resultStoreModel.fetch(new Range(0, 1),
//                resultStoreInfoResultPage -> {
//                    if (resultStoreInfoResultPage.getValues().size() > 0) {
//                        resultStorePresenter.show();
//                    }
//                },
//                throwable ->
//                        AlertEvent.fireError(this, "Error fetching result stores", throwable.getMessage(), null));
    }
}
