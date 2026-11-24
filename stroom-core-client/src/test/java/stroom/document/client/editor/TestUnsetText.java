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

import static org.assertj.core.api.Assertions.assertThat;

class TestUnsetText {

    /// Text that isn't set is written as it was read.
    @Test
    void unsetTextIsAsRead() {
        assertThat(UnsetText.asRead("", null)).isNull();
        assertThat(UnsetText.asRead(null, "")).isEmpty();
        assertThat(UnsetText.asRead("", " ")).isEqualTo(" ");
    }

    /// Text that was set or cleared is as shown.
    @Test
    void changedTextIsAsShown() {
        assertThat(UnsetText.asRead("a", null)).isEqualTo("a");
        assertThat(UnsetText.asRead("", "a")).isEmpty();
    }
}
