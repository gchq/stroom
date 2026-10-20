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

class TestControlNames {

    @Test
    void testParse() {
        assertThat(ControlNames.parse("Amount, Unit")).containsExactly("Amount", "Unit");
        assertThat(ControlNames.parse("Hours,Minutes")).containsExactly("Hours", "Minutes");
        assertThat(ControlNames.parse(" One ")).containsExactly("One");
    }

    @Test
    void testParseNone() {
        assertThat(ControlNames.parse(null)).isEmpty();
        assertThat(ControlNames.parse("")).isEmpty();
        assertThat(ControlNames.parse("  ")).isEmpty();
    }

    @Test
    void testParseKeepsBlankNamesInPlace() {
        // A blank name keeps the names after it matched to their controls
        assertThat(ControlNames.parse(", Unit")).containsExactly("", "Unit");
    }
}
