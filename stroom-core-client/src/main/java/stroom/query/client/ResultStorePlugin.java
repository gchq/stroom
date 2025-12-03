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

package stroom.query.client;

import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.main.client.event.ShowMainEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.query.client.presenter.ResultStorePresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ResultStorePlugin extends Plugin implements ShowMainEvent.Handler, HasHandlers {

    private static final Action ACTION = Action.GOTO_SEARCH_RESULTS;
    private final ClientSecurityContext securityContext;

    private final Provider<ResultStorePresenter> resultStorePresenterProvider;
    private final Provider<ResultStoreModel> resultStoreModelProvider;

    private ResultStorePresenter resultStorePresenter = null;
    private ResultStoreModel resultStoreModel = null;

    @Inject
    public ResultStorePlugin(final EventBus eventBus,
                             final ClientSecurityContext securityContext,
                             final Provider<ResultStorePresenter> resultStorePresenterProvider,
                             final Provider<ResultStoreModel> resultStoreModelProvider) {
        super(eventBus);
        this.securityContext = securityContext;
        this.resultStorePresenterProvider = resultStorePresenterProvider;
        this.resultStoreModelProvider = resultStoreModelProvider;

        registerHandler(getEventBus().addHandler(ShowMainEvent.getType(), this));

        KeyBinding.addCommand(ACTION, resultStorePresenter::show);
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        // Add items to the tools menu.
//        if (securityContext.hasAppPermission("Import Configuration")) {
        event.getMenuItems().addMenuItem(MenuKeys.MONITORING_MENU,
                new IconMenuItem.Builder()
                        .priority(201)
                        .icon(SvgImage.DATABASE)
                        .iconColour(IconColour.GREY)
                        .text("Search Results")
                        .action(ACTION)
                        .command(getResultStorePresenter()::show)
                        .build());
//        }
    }

    @Override
    public void onShowMain(final ShowMainEvent event) {
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

    private ResultStorePresenter getResultStorePresenter() {
        if (resultStorePresenter == null) {
            resultStorePresenter = resultStorePresenterProvider.get();
        }
        return resultStorePresenter;
    }

    public ResultStoreModel getResultStoreModel() {
        if (resultStoreModel == null) {
            resultStoreModel = resultStoreModelProvider.get();
        }
        return resultStoreModel;
    }
}
