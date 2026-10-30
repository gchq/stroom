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

package stroom.gwt.workbench.client.app.ai;

import stroom.ai.client.component.AskStroomAiController;
import stroom.ai.client.event.ShowAskStroomAiEvent;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.widget.datagrid.client.AskStroomAiEvent;

import java.util.Objects;
import java.util.function.Supplier;

/// Shows Stroom's real 'Ask Stroom AI' chat ([AskStroomAiController]) for the app events that open it,
/// `AskStroomAiEvent` (a table's 'Ask Stroom AI' button) and `ShowAskStroomAiEvent` (the toolbar's
/// AI toggle), as `AskStroomAiScreens` does in Stroom.
///
/// Like that, it creates the controller only when the first of those events
/// is fired, i.e. after start-up, so the chat starts as it does in Stroom. The controller reads its
/// configuration from the user's preferences as it is created (`AskStroomAiClient.getConfig`), and
/// when they have no `askStroomAiConfig` (Stroom's default preferences have none) it fetches the
/// default config and stores it in a copy of the current preferences. Created after start-up, the
/// preferences are loaded and the default is stored in them, as in Stroom. (A controller created
/// before the preferences had loaded once failed copying null preferences; `AskStroomAiClient` now
/// waits for the user's preferences (`UserPreferencesManager.whenLoaded`), so the lazy creation is
/// only for fidelity.)
///
/// Add the chat's routes with [AiFixtures#chatRoutes(stroom.gwt.workbench.client.app.rest.RestFixtures.Builder)].
public final class AskStroomAiChat {

    private final ScreenHarness harness;
    private final Supplier<AskStroomAiController> factory;
    private AskStroomAiController controller;

    private AskStroomAiChat(final ScreenHarness harness, final Supplier<AskStroomAiController> factory) {
        this.harness = harness;
        this.factory = factory;
    }

    /// Registers the chat as the handler of `AskStroomAiEvent` and `ShowAskStroomAiEvent` on the
    /// harness's event bus. The controller is created (with `factory`, e.g. the story's injector's
    /// getter) when the first event is fired, and is closed and unbound when the story renders again.
    ///
    /// @param harness The story's harness.
    /// @param factory Creates the controller, e.g. `injector::getAskStroomAiController`.
    /// @return The registered chat, e.g. to get its controller once shown.
    public static AskStroomAiChat register(final ScreenHarness harness,
                                           final Supplier<AskStroomAiController> factory) {
        final AskStroomAiChat chat = new AskStroomAiChat(Objects.requireNonNull(harness),
                Objects.requireNonNull(factory));
        harness.addRegistration(harness.getEventBus().addHandler(AskStroomAiEvent.getType(),
                event -> chat.getController().onAsk(event)));
        harness.addRegistration(harness.getEventBus().addHandler(ShowAskStroomAiEvent.getType(),
                event -> chat.getController().onShow(event)));
        return chat;
    }

    /// Gets the chat's controller, creating it if no event has created it yet.
    ///
    /// @return The controller.
    public AskStroomAiController getController() {
        if (controller == null) {
            controller = factory.get();
            harness.ownPopup(controller);
        }
        return controller;
    }
}
