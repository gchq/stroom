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

import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiConfig.DockLocation;
import stroom.ai.shared.AskStroomAiConfig.DockType;
import stroom.ai.shared.AskStroomAiResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.preferences.client.UserPreferencesManager;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.UserPreferences;

import com.google.gwt.junit.GWTMockUtilities;
import org.fusesource.restygwt.client.DirectRestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// Ask Stroom AI's default settings reaching users (asked for by the user: a model set as the default didn't reach
/// another user in a test environment). Each test replays, through [AskStroomAiClient], the calls the UI makes:
/// moving the chat dock reads the user's settings and saves them back with the new dock (as
/// `AskStroomAiPresenter.saveDockBehaviourToPrefs` does), and pressing OK in the settings dialog saves the settings
/// it showed. Two clients stand for two users' browsers, sharing one server that holds the defaults.
class TestAskStroomAiClientDefaults {

    private static final DocRef MODEL_A = new DocRef("OpenAIModel", "model-a-uuid", "Model A");
    private static final DocRef MODEL_B = new DocRef("OpenAIModel", "model-b-uuid", "Model B");
    private static final DocRef OWN_MODEL = new DocRef("OpenAIModel", "own-model-uuid", "Own model");

    // The server's default settings, as getDefaultConfig gives them.
    private final AtomicReference<AskStroomAiConfig> serverDefaults = new AtomicReference<>();
    private AskStroomAiResource resource;

    @BeforeEach
    void setUp() {
        // The client's REST resources are made with GWT.create, which only works in a browser.
        GWTMockUtilities.disarm();
        serverDefaults.set(AskStroomAiConfig.builder().modelRef(MODEL_A).build());
        resource = Mockito.mock(AskStroomAiResource.class);
        Mockito.when(resource.getDefaultConfig()).thenAnswer(invocation -> serverDefaults.get());
        Mockito.when(resource.setDefaultAskStroomAIConfig(Mockito.any())).thenAnswer(invocation -> {
            serverDefaults.set(invocation.getArgument(0));
            return true;
        });
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void aUserWhoOnlyMovedTheDockGetsTheNewDefaultModel() {
        final User user = new User();
        user.movesTheDock();

        new User().setsTheDefaultModel(MODEL_B);

        // The user reloads Stroom, so nothing cached in their browser is in the way.
        assertThat(user.reloaded().model()).as("the model of a user who never chose one").isEqualTo(MODEL_B);
    }

    @Test
    void movingTheDockSavesNoModel() {
        final User user = new User();
        user.movesTheDock();

        assertThat(user.savedModel()).as("the model saved by moving the dock").isNull();
    }

    @Test
    void pressingOkWithNothingChangedSavesNoModel() {
        final User user = new User();
        user.pressesOkInTheSettingsDialog();

        assertThat(user.savedModel()).as("the model saved by pressing OK with the default shown").isNull();
    }

    @Test
    void aUserWhoChoseAModelKeepsItWhenTheDefaultChanges() {
        final User user = new User();
        user.choosesTheModel(OWN_MODEL);

        new User().setsTheDefaultModel(MODEL_B);

        assertThat(user.reloaded().model()).isEqualTo(OWN_MODEL);
    }

    @Test
    void choosingTheDefaultModelFollowsTheDefault() {
        final User user = new User();
        user.choosesTheModel(MODEL_A);

        new User().setsTheDefaultModel(MODEL_B);

        assertThat(user.reloaded().model()).isEqualTo(MODEL_B);
    }

    @Test
    void aBrowserThatCachedTheOldDefaultSendsNoModelSoTheServersDefaultIsUsed() {
        final User user = new User();
        assertThat(user.shownModel()).isEqualTo(MODEL_A);

        // Set from another browser: this one still has Model A cached.
        new User().setsTheDefaultModel(MODEL_B);

        assertThat(user.sentModel()).isNull();
        assertThat(user.model()).isEqualTo(MODEL_B);
    }

    @Test
    void thePickersShowTheDefaultModelToAUserWhoFollowsIt() {
        final User user = new User();
        user.movesTheDock();

        assertThat(user.shownModel()).isEqualTo(MODEL_A);
        assertThat(user.sentModel()).isNull();
    }

    @Test
    void openingThePanelPicksUpANewDefaultWithoutReloading() {
        final User user = new User();
        assertThat(user.shownModel()).isEqualTo(MODEL_A);

        new User().setsTheDefaultModel(MODEL_B);
        user.opensThePanel();

        assertThat(user.shownModel()).isEqualTo(MODEL_B);
    }

    @Test
    void openingThePanelPicksUpAModelChoiceClearedByAnAdministrator() {
        final User user = new User();
        user.choosesTheModel(OWN_MODEL);
        user.startsANewChat();
        assertThat(user.sentModel()).isEqualTo(OWN_MODEL);

        user.hasTheirModelChoiceClearedByAnAdministrator();
        user.opensThePanel();

        assertThat(user.sentModel()).as("so the server uses the chat's model, or the default for a new chat")
                .isNull();
    }

    @Test
    void theDefaultModelPickedInAChatIsSentForThatChat() {
        final User user = new User();
        user.choosesTheModel(OWN_MODEL);
        user.startsANewChat();

        // Picked in a chat that has used Own model: sent, so the chat changes to it.
        user.choosesTheModel(MODEL_A);

        assertThat(user.sentModel()).isEqualTo(MODEL_A);
        assertThat(user.savedModel()).as("saved as following the default").isNull();
        user.startsANewChat();
        assertThat(user.sentModel()).as("a new chat takes the default from the server").isNull();
    }

    @Test
    void thePickerShowsTheModelTheChatsNextQuestionUses() {
        final User user = new User();
        new User().setsTheDefaultModel(MODEL_B);
        user.opensThePanel();

        assertThat(user.pickerShowsForAChatThatUsed(MODEL_A)).as("a chat answered before the default changed")
                .isEqualTo(MODEL_A);
        assertThat(user.pickerShowsForAChatThatUsed(null)).as("a new chat").isEqualTo(MODEL_B);

        // Picked in the chat: the chat changes to it.
        user.choosesTheModel(MODEL_B);
        assertThat(user.pickerShowsForAChatThatUsed(MODEL_A)).isEqualTo(MODEL_B);

        // The user's own model is what every chat's questions name.
        user.startsANewChat();
        user.choosesTheModel(OWN_MODEL);
        user.startsANewChat();
        assertThat(user.pickerShowsForAChatThatUsed(MODEL_A)).isEqualTo(OWN_MODEL);
    }

    @Test
    void movingTheDockInABrowserNotYetRefreshedDoesNotPutBackAClearedModel() {
        final User user = new User();
        user.choosesTheModel(OWN_MODEL);
        user.startsANewChat();

        // Cleared while this user's panel stays open: their browser still holds Own model.
        user.hasTheirModelChoiceClearedByAnAdministrator();
        user.movesTheDock();

        assertThat(user.savedModel()).isNull();
        assertThat(user.savedConfig().getDockLocation()).isEqualTo(DockLocation.LEFT);
        assertThat(user.sentModel()).as("the browser takes the cleared settings it saved").isNull();
    }

    @Test
    void pickingAModelInABrowserNotYetRefreshedKeepsTheServersOtherSettings() {
        final User user = new User();
        final User sameUserElsewhere = user.reloaded();
        sameUserElsewhere.movesTheDock();

        user.choosesTheModel(OWN_MODEL);

        assertThat(user.savedModel()).isEqualTo(OWN_MODEL);
        assertThat(user.savedConfig().getDockLocation()).as("the dock moved in the other browser")
                .isEqualTo(DockLocation.LEFT);
    }

    @Test
    void aUserWhoNeverSavedAnythingGetsTheNewDefaultModel() {
        final User user = new User();
        assertThat(user.model()).isEqualTo(MODEL_A);

        new User().setsTheDefaultModel(MODEL_B);

        assertThat(user.reloaded().model()).isEqualTo(MODEL_B);
    }


    // --------------------------------------------------------------------------------


    // A user's browser: their client, and their preferences as the server stores them.
    private final class User {

        private final AtomicReference<UserPreferences> stored;
        private final AtomicReference<UserPreferences> current;
        private final AskStroomAiClient client;

        User() {
            this(new AtomicReference<>(UserPreferences.builder().build()));
        }

        private User(final AtomicReference<UserPreferences> stored) {
            this.stored = stored;
            this.current = new AtomicReference<>(stored.get());
            final UserPreferencesManager preferences = Mockito.mock(UserPreferencesManager.class);
            Mockito.when(preferences.getCurrentUserPreferences()).thenAnswer(invocation -> current.get());
            Mockito.doAnswer(invocation -> {
                current.set(invocation.getArgument(0));
                return null;
            }).when(preferences).setCurrentPreferences(Mockito.any());
            Mockito.doAnswer(invocation -> {
                stored.set(invocation.getArgument(0));
                return null;
            }).when(preferences).update(Mockito.any(), Mockito.any(), Mockito.any());
            Mockito.doAnswer(invocation -> {
                invocation.<Consumer<UserPreferences>>getArgument(0).accept(stored.get());
                return null;
            }).when(preferences).fetch(Mockito.any(), Mockito.any());
            client = new AskStroomAiClient(new FakeRestFactory(resource), preferences);
        }

        // The same user after reloading Stroom: their stored preferences, nothing cached.
        User reloaded() {
            return new User(stored);
        }

        // The model the user's questions are answered with: the one sent, or the server's default for none.
        DocRef model() {
            return Objects.requireNonNullElse(sentModel(), serverDefaults.get().getModelRef());
        }

        DocRef savedModel() {
            final AskStroomAiConfig saved = stored.get().getAskStroomAiConfig();
            return saved == null
                    ? null
                    : saved.getModelRef();
        }

        // As AskStroomAiPresenter.saveDockBehaviourToPrefs saves a dock change.
        void movesTheDock() {
            client.updateConfig(config -> config.copy()
                    .dockType(DockType.DOCK)
                    .dockLocation(DockLocation.LEFT)
                    .build(), null);
        }

        AskStroomAiConfig savedConfig() {
            return stored.get().getAskStroomAiConfig();
        }

        // As the settings dialog saves the settings it showed when OK is pressed with nothing changed.
        void pressesOkInTheSettingsDialog() {
            client.getDisplayConfig(config -> client.setConfig(config.copy().build(), null), null);
        }

        // As AskStroomAiPresenter.writeModel saves a model picked in the chat panel.
        void choosesTheModel(final DocRef model) {
            client.updateConfig(config -> config.copy().modelRef(model).build(), null);
            client.pickModelForThisChat(model);
        }

        // As AskStroomAiPresenter does when the panel opens.
        void opensThePanel() {
            client.refreshSettings(() -> {
            }, null);
        }

        // An administrator clearing every user's own choice of model, from another browser.
        void hasTheirModelChoiceClearedByAnAdministrator() {
            final AskStroomAiConfig config = stored.get().getAskStroomAiConfig();
            stored.set(stored.get().copy().askStroomAiConfig(config.copy().modelRef(null).build()).build());
        }

        void startsANewChat() {
            client.forgetPickedModel();
        }

        // The model the chat panel's picker shows for a chat answered with the given model (null for a new chat).
        DocRef pickerShowsForAChatThatUsed(final DocRef chatModel) {
            final AtomicReference<DocRef> shown = new AtomicReference<>();
            client.getChatDisplayModel(chatModel, shown::set, null);
            return shown.get();
        }

        // The model a question is sent with, as AskStroomAiPresenter sends it: the server picks the default for none.
        DocRef sentModel() {
            final AtomicReference<AskStroomAiConfig> config = new AtomicReference<>();
            client.getConfig(config::set, null);
            return config.get().getModelRef();
        }

        // The model the chat panel's picker shows.
        DocRef shownModel() {
            final AtomicReference<AskStroomAiConfig> config = new AtomicReference<>();
            client.getDisplayConfig(config::set, null);
            return config.get().getModelRef();
        }

        // As the settings dialog's "Set as defaults" sets them.
        void setsTheDefaultModel(final DocRef model) {
            client.getDisplayConfig(config -> client.setDefaultAskStroomAIConfig(config.copy().modelRef(model).build(),
                    success -> {
                    }, null), null);
        }
    }

    // Runs each call at once against the resource given, as the server would answer it.
    private static final class FakeRestFactory implements RestFactory {

        private final Object resource;

        FakeRestFactory(final Object resource) {
            this.resource = resource;
        }

        @Override
        public <T extends DirectRestService> Resource<T> create(final T service) {
            @SuppressWarnings("unchecked") final T answering = (T) resource;
            return new Resource<>() {
                @Override
                public <R> MethodExecutor<T, R> method(final Function<T, R> function) {
                    return new Executor<>(() -> function.apply(answering));
                }

                @Override
                public MethodExecutor<T, Void> call(final Consumer<T> consumer) {
                    return new Executor<>(() -> {
                        consumer.accept(answering);
                        return null;
                    });
                }
            };
        }
    }

    private static final class Executor<T extends DirectRestService, R>
            implements RestFactory.MethodExecutor<T, R>, RestFactory.TaskExecutor<T, R> {

        private final Supplier<R> call;
        private Consumer<R> onSuccess = result -> {
        };

        Executor(final Supplier<R> call) {
            this.call = call;
        }

        @Override
        public RestFactory.TaskExecutor<T, R> taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
            return this;
        }

        @Override
        public RestFactory.TaskExecutor<T, R> taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory,
                                                                 final String taskMessage) {
            return this;
        }

        @Override
        public RestFactory.MethodExecutor<T, R> onSuccess(final Consumer<R> resultConsumer) {
            this.onSuccess = resultConsumer;
            return this;
        }

        @Override
        public RestFactory.MethodExecutor<T, R> onFailure(final RestErrorHandler errorHandler) {
            return this;
        }

        @Override
        public void exec() {
            onSuccess.accept(call.get());
        }
    }
}
