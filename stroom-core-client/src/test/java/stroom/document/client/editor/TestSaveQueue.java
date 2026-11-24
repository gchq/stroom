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

package stroom.document.client.editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSaveQueue {

    private final SaveQueue queue = new SaveQueue();
    private final List<String> run = new ArrayList<>();

    /// A save with none in progress starts.
    @Test
    void aSaveStarts() {
        assertThat(start("first")).isTrue();
        assertThat(run).isEmpty();
    }

    /// One asked for during another waits, and is tried again once that succeeds.
    @Test
    void aSecondSaveWaitsForTheFirst() {
        start("first");
        assertThat(start("second")).isFalse();
        assertThat(start("third")).isFalse();
        assertThat(run).isEmpty();

        queue.succeeded(() -> run.add("read"));

        assertThat(run).containsExactly("read", "second again", "third again");
    }

    /// If the save in progress fails those waiting are abandoned, and another can start.
    @Test
    void aSecondSaveIsAbandonedIfTheFirstFails() {
        start("first");
        start("second");

        queue.failed();

        assertThat(run).containsExactly("second abandoned");
        assertThat(start("third")).isTrue();
    }

    /// If what was saved can't be read those waiting are abandoned, and another can start.
    @Test
    void aFaultReadingDoesNotStopLaterSaves() {
        start("first");
        start("second");

        assertThatThrownBy(() -> queue.succeeded(() -> {
            throw new IllegalStateException("Can't be read");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(run).containsExactly("second abandoned");
        assertThat(start("third")).isTrue();
    }

    /// Part of a save that throws fails it, so another can start.
    @Test
    void aFaultDuringASaveDoesNotStopLaterSaves() {
        start("first");
        start("second");

        assertThatThrownBy(() -> queue.guard(() -> {
            throw new IllegalStateException("Can't be sent");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(run).containsExactly("second abandoned");
        assertThat(start("third")).isTrue();
    }

    /// Every save waiting is tried again, though one before it throws.
    @Test
    void aFaultTryingOneAgainDoesNotLoseTheRest() {
        start("first");
        queue.start(() -> {
            throw new IllegalStateException("Can't be written");
        }, () -> {
        });
        start("third");

        assertThatThrownBy(() -> queue.succeeded(() -> {
        })).hasMessage("Can't be written");

        assertThat(run).containsExactly("third again");
    }

    /// A save tried again waits in its turn for one that starts before it.
    @Test
    void oneTriedAgainMayStart() {
        start("first");
        queue.start(() -> run.add("second " + start("second")), () -> {
        });
        queue.start(() -> run.add("third " + start("third")), () -> {
        });

        queue.succeeded(() -> run.add("read"));

        assertThat(run).containsExactly("read", "second true", "third false");
        queue.succeeded(() -> run.add("read"));
        assertThat(run).containsExactly("read", "second true", "third false", "read", "third again");
    }

    private boolean start(final String name) {
        return queue.start(() -> run.add(name + " again"), () -> run.add(name + " abandoned"));
    }
}
