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

package stroom.pathways.client;

import stroom.content.client.ContentPlugin;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.event.CloseContentEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.pathways.client.presenter.ShowTracesEvent;
import stroom.pathways.client.presenter.TracesPresenter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class TracesPlugin extends ContentPlugin<TracesPresenter> {

    private final ClientSecurityContext securityContext;
    private final Provider<TracesPresenter> tracesPresenterProvider;
    private final ContentManager contentManager;

    @Inject
    public TracesPlugin(final EventBus eventBus,
                        final Provider<TracesPresenter> tracesPresenterProvider,
                        final ContentManager contentManager,
                        final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, tracesPresenterProvider);
        this.securityContext = securityContext;
        this.tracesPresenterProvider = tracesPresenterProvider;
        this.contentManager = contentManager;


        registerHandler(getEventBus().addHandler(
                ShowTracesEvent.getType(), showTracesEvent -> {

                    final TracesPresenter tracesPresenter = tracesPresenterProvider.get();
                    tracesPresenter.setDataSourceRef(showTracesEvent.getDataSourceRef());
                    tracesPresenter.setPathway(showTracesEvent.getPathway());
                    tracesPresenter.setFilter(showTracesEvent.getFilter());
                    tracesPresenter.refresh();

                    final CloseContentEvent.Handler closeHandler = (event) -> {
                        event.getCallback().closeTab(true);
                    };

                    // Tell the content manager to open the tab.
                    contentManager.open(closeHandler, tracesPresenter, tracesPresenter);
                }));

//        // TODO : TEMPORARY
//        registerHandler(getEventBus().addHandler(CurrentUserChangedEvent.getType(), e -> {
//            new Timer() {
//                @Override
//                public void run() {
//                    final TracesPresenter tracesPresenter = tracesPresenterProvider.get();
//                    tracesPresenter.setDataSourceRef(DocRef.builder().type(PathwaysDoc.TYPE).uuid(
//                            "ba8df4b8-d03b-484c-bb65-273b35ca56ff").build());
////        tracesPresenter.setPathway();
////        tracesPresenter.setFilter();
//                    tracesPresenter.refresh();
//
//                    final CloseContentEvent.Handler closeHandler = (event) -> {
//                        event.getCallback().closeTab(true);
//                    };
//
//                    // Tell the content manager to open the tab.
//                    contentManager.open(closeHandler, tracesPresenter, tracesPresenter);
//                }
//            }.schedule(1000);
//        }));


    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);
        addChildItems(event);
    }

    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (securityContext.hasAppPermission(AppPermission.ADMINISTRATOR)) {
            MenuKeys.addAdministrationMenu(event.getMenuItems());
            event.getMenuItems().addMenuItem(MenuKeys.ADMINISTRATION_MENU,
                    new IconMenuItem.Builder()
                            .priority(3)
                            .icon(SvgImage.DOCUMENT_TRACES)
                            .text("Traces")
                            .command(() -> {
                                final TracesPresenter tracesPresenter = tracesPresenterProvider.get();
//                                tracesPresenter.setDataSourceRef(showTracesEvent.getDataSourceRef());
//                                tracesPresenter.setPathway(showTracesEvent.getPathway());
//                                tracesPresenter.setFilter(showTracesEvent.getFilter());
                                tracesPresenter.refresh();

                                final CloseContentEvent.Handler closeHandler = (e) -> {
                                    e.getCallback().closeTab(true);
                                };

                                // Tell the content manager to open the tab.
                                contentManager.open(closeHandler, tracesPresenter, tracesPresenter);
                            }).build());
        }
    }
}

