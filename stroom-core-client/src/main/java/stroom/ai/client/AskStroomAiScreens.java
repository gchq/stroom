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
import stroom.widget.component.client.AppRoot;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The Ask Stroom AI chat, made when first asked for and kept for the life of the app (it is docked
/// beside the main screen, or shown as a dialog, and remembers its conversation), owned by the app
/// root. Hears the app-wide events that open it.
public class AskStroomAiScreens {

    private final AppRoot appRoot;
    private final Provider<AskStroomAiController> askStroomAiControllerProvider;
    private AskStroomAiController askStroomAiController;

    /// Adds the handlers of the events that open the chat.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param appRoot                       Owns the chat once made.
    /// @param askStroomAiControllerProvider Makes the chat.
    @Inject
    public AskStroomAiScreens(final EventBus eventBus,
                              final AppRoot appRoot,
                              final Provider<AskStroomAiController> askStroomAiControllerProvider) {
        this.appRoot = appRoot;
        this.askStroomAiControllerProvider = askStroomAiControllerProvider;
        eventBus.addHandler(AskStroomAiEvent.getType(), event -> getChat().onAsk(event));
        eventBus.addHandler(ShowAskStroomAiEvent.getType(), event -> getChat().onShow(event));
    }

    // The chat, made the first time it is asked for
    private AskStroomAiController getChat() {
        if (askStroomAiController == null) {
            askStroomAiController = appRoot.make(askStroomAiControllerProvider);
        }
        return askStroomAiController;
    }
}
