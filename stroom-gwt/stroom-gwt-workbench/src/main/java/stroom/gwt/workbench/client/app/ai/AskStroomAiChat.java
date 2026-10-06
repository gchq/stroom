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

import stroom.ai.client.AskStroomAiPresenter;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.client.event.ShowAskStroomAiEvent;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;

import java.util.Objects;
import java.util.function.Supplier;

/// Shows Stroom's real 'Ask Stroom AI' chat ([AskStroomAiPresenter]) for the app events that open it,
/// `AskStroomAiEvent` (a table's 'Ask Stroom AI' button) and `ShowAskStroomAiEvent` (the toolbar's
/// AI toggle), as the presenter's GWTP proxy does in Stroom.
///
/// Like the proxy (`@ProxyCodeSplit`), it creates the presenter only when the first of those events
/// is fired, i.e. after start-up. That matters: the presenter reads its configuration from the user's
/// preferences as it is created (`AskStroomAiClient.getConfig`), and when they have no
/// `askStroomAiConfig` (Stroom's default preferences have none) it fetches the default config and
/// copies the current preferences to store it in them. A presenter created before the harness has
/// loaded the preferences (e.g. in a story's set up, before `ScreenHarness.afterStartUp`) copies
/// null preferences and fails with "Cannot read properties of undefined (reading 'copy')".
///
/// Add the chat's routes with [AiFixtures#chatRoutes(stroom.gwt.workbench.client.app.rest.RestFixtures.Builder)].
public final class AskStroomAiChat {

    private final ScreenHarness harness;
    private final Supplier<AskStroomAiPresenter> factory;
    private AskStroomAiPresenter presenter;

    private AskStroomAiChat(final ScreenHarness harness, final Supplier<AskStroomAiPresenter> factory) {
        this.harness = harness;
        this.factory = factory;
    }

    /// Registers the chat as the handler of `AskStroomAiEvent` and `ShowAskStroomAiEvent` on the
    /// harness's event bus. The presenter is created (with `factory`, e.g. the story's injector's
    /// getter) when the first event is fired, and is closed and unbound when the story renders again.
    ///
    /// @param harness The story's harness.
    /// @param factory Creates the presenter, e.g. `injector::getAskStroomAiPresenter`.
    /// @return The registered chat, e.g. to get its presenter once shown.
    public static AskStroomAiChat register(final ScreenHarness harness,
                                           final Supplier<AskStroomAiPresenter> factory) {
        final AskStroomAiChat chat = new AskStroomAiChat(Objects.requireNonNull(harness),
                Objects.requireNonNull(factory));
        harness.addRegistration(harness.getEventBus().addHandler(AskStroomAiEvent.getType(),
                event -> chat.getPresenter().onAsk(event)));
        harness.addRegistration(harness.getEventBus().addHandler(ShowAskStroomAiEvent.getType(),
                event -> chat.getPresenter().onShow(event)));
        return chat;
    }

    /// @return Whether the presenter has been created, i.e. whether either event has been fired.
    public boolean isCreated() {
        return presenter != null;
    }

    /// Gets the chat's presenter, creating it if no event has created it yet.
    ///
    /// @return The presenter.
    public AskStroomAiPresenter getPresenter() {
        if (presenter == null) {
            presenter = factory.get();
            harness.unbindOnCleanUp(presenter);
            harness.closeOnCleanUp(presenter);
        }
        return presenter;
    }
}
