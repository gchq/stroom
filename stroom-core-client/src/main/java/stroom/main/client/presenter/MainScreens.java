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

package stroom.main.client.presenter;

import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.MoveContentTabEvent;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.core.client.presenter.CorePresenter;
import stroom.main.client.event.ShowMainEvent;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The main screen and its content tabs, made when first needed. Hears the app-wide events that
/// open them, for the life of the app.
public class MainScreens {

    /// Adds the handlers of the events that open the screens.
    ///
    /// @param eventBus The application's event bus.
    /// @param contentTabPanePresenterProvider Makes the ContentTabPanePresenter.
    /// @param corePresenterProvider Makes the CorePresenter.
    /// @param mainPresenterProvider Makes the MainPresenter.
    @Inject
    public MainScreens(final EventBus eventBus,
                       final Provider<ContentTabPanePresenter> contentTabPanePresenterProvider,
                       final Provider<CorePresenter> corePresenterProvider,
                       final Provider<MainPresenter> mainPresenterProvider) {
        // The main screen first, so that it is shown before a tab opens in it
        eventBus.addHandler(ShowMainEvent.getType(), event -> {
            corePresenterProvider.get().setContent(mainPresenterProvider.get());
            mainPresenterProvider.get().onShowMain(event);
        });
        eventBus.addHandler(OpenContentTabEvent.getType(), event ->
                corePresenterProvider.get().setContent(mainPresenterProvider.get()));
        // The content tabs
        eventBus.addHandler(OpenContentTabEvent.getType(), event ->
                contentTabPanePresenterProvider.get().onOpen(event));
        eventBus.addHandler(CloseContentTabEvent.getType(), event ->
                contentTabPanePresenterProvider.get().onClose(event));
        eventBus.addHandler(SelectContentTabEvent.getType(), event ->
                contentTabPanePresenterProvider.get().onSelect(event));
        eventBus.addHandler(RefreshContentTabEvent.getType(), event ->
                contentTabPanePresenterProvider.get().onRefresh(event));
        eventBus.addHandler(MoveContentTabEvent.getType(), event ->
                contentTabPanePresenterProvider.get().onMove(event));
    }
}
