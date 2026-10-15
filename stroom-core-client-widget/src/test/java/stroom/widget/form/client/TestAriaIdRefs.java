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

package stroom.widget.form.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAriaIdRefs {

    @Test
    void testAdd() {
        assertThat(AriaIdRefs.add(null, "help", false)).isEqualTo("help");
        assertThat(AriaIdRefs.add("", "help", false)).isEqualTo("help");
        assertThat(AriaIdRefs.add("help", "error", true)).isEqualTo("error help");
        assertThat(AriaIdRefs.add("error", "help", false)).isEqualTo("error help");
        // Added once, moving to where asked
        assertThat(AriaIdRefs.add("help error", "error", true)).isEqualTo("error help");
        assertThat(AriaIdRefs.add("error help", "error", true)).isEqualTo("error help");
        // Extra spaces are tidied
        assertThat(AriaIdRefs.add("  a   b ", "c", false)).isEqualTo("a b c");
    }

    @Test
    void testAddNothing() {
        assertThat(AriaIdRefs.add("a", null, false)).isEqualTo("a");
        assertThat(AriaIdRefs.add("a", " ", true)).isEqualTo("a");
        assertThat(AriaIdRefs.add(null, null, false)).isNull();
    }

    @Test
    void testRemove() {
        assertThat(AriaIdRefs.remove("error help", "error")).isEqualTo("help");
        assertThat(AriaIdRefs.remove("help", "help")).isNull();
        assertThat(AriaIdRefs.remove("help", "other")).isEqualTo("help");
        assertThat(AriaIdRefs.remove(null, "help")).isNull();
        assertThat(AriaIdRefs.remove("a help b", "help")).isEqualTo("a b");
        // Only whole ids are removed
        assertThat(AriaIdRefs.remove("help-2", "help")).isEqualTo("help-2");
    }
}
