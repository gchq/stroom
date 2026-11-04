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

package stroom.widget.layout.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestScrollPaneTabStop {

    @Test
    void testContentThatFitsIsNotATabStop() {
        assertThat(ScrollPaneTabStop.isTabStop(null, false, false)).isFalse();
    }

    @Test
    void testOverflowingContentWithNothingFocusableIsATabStop() {
        assertThat(ScrollPaneTabStop.isTabStop(null, true, false)).isTrue();
    }

    @Test
    void testOverflowingContentWithSomethingFocusableIsNotATabStop() {
        // Tabbing to what is inside scrolls the pane
        assertThat(ScrollPaneTabStop.isTabStop(null, true, true)).isFalse();
    }

    @Test
    void testChoiceWins() {
        assertThat(ScrollPaneTabStop.isTabStop(true, false, true)).isTrue();
        assertThat(ScrollPaneTabStop.isTabStop(false, true, false)).isFalse();
    }
}
