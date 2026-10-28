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

import stroom.ai.client.component.AskStroomAiController;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.client.event.ShowAskStroomAiEvent;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The Ask Stroom AI screen, made when first asked for. Hears the app-wide events that open them,
/// for the life of the app.
public class AskStroomAiScreens {

    /// Adds the handlers of the events that open the screens.
    ///
    /// @param eventBus The application's event bus.
    /// @param askStroomAiControllerProvider Makes the AskStroomAiController.
    @Inject
    public AskStroomAiScreens(final EventBus eventBus,
                              final Provider<AskStroomAiController> askStroomAiControllerProvider) {
        eventBus.addHandler(AskStroomAiEvent.getType(), event -> askStroomAiControllerProvider.get().onAsk(event));
        eventBus.addHandler(ShowAskStroomAiEvent.getType(), event -> askStroomAiControllerProvider.get().onShow(event));
    }
}
