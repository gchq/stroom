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

package stroom.core.client;

import stroom.core.client.component.AppPageController;
import stroom.core.client.component.FullScreenController;
import stroom.core.client.event.ShowFullScreenEvent;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The page's core and its full screen view, made when first needed. Hears the app-wide events that
/// open them, for the life of the app.
public class CoreScreens {

    /// Adds the handlers of the events that open the screens.
    ///
    /// @param eventBus The application's event bus.
    /// @param appPageControllerProvider Makes the AppPageController.
    /// @param fullScreenControllerProvider Makes the FullScreenController.
    @Inject
    public CoreScreens(final EventBus eventBus,
                       final Provider<AppPageController> appPageControllerProvider,
                       final Provider<FullScreenController> fullScreenControllerProvider) {
        eventBus.addHandler(TaskStartEvent.getType(), event -> appPageControllerProvider.get().onTaskStart(event));
        eventBus.addHandler(TaskEndEvent.getType(), event -> appPageControllerProvider.get().onTaskEnd(event));
        eventBus.addHandler(CurrentUserChangedEvent.getType(), event ->
                appPageControllerProvider.get().onCurrentUserChanged(event));
        eventBus.addHandler(ShowFullScreenEvent.getType(), event -> {
            fullScreenControllerProvider.get().onShowFullScreen(event);
            appPageControllerProvider.get().setContent(fullScreenControllerProvider.get());
        });
    }
}
