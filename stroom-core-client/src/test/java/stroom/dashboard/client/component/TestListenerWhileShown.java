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

package stroom.dashboard.client.component;

import com.google.web.bindery.event.shared.HandlerRegistration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestListenerWhileShown {

    private final List<String> listening = new ArrayList<>();

    @Test
    void showingAgainWhileShownAddsNoSecondListener() {
        final ListenerWhileShown listener = new ListenerWhileShown();

        assertThat(listener.start(this::listen)).isTrue();
        // The second click hides the popup (the popup manager's toggle). Before, it added a second
        // listener that nothing removed, which kept refreshing the hidden popup
        assertThat(listener.start(this::listen)).isFalse();
        listener.stop();
        assertThat(listening).isEmpty();

        // Shown again later, it listens again
        assertThat(listener.start(this::listen)).isTrue();
        assertThat(listening).hasSize(1);
        listener.stop();
        listener.stop();
        assertThat(listening).isEmpty();
    }

    private HandlerRegistration listen() {
        final String name = "listener " + listening.size();
        listening.add(name);
        return () -> listening.remove(name);
    }
}
