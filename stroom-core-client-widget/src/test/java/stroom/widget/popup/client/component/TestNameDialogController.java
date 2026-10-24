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

package stroom.widget.popup.client.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// The dialog itself (shown, OK with and without a name, Cancel) is checked by its workbench
// stories (Widgets/Dialogs/NameDialog); this is the check behind OK
class TestNameDialogController {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void noName(final String name) {
        // Regression test: OK with no name did nothing and said nothing (e.g. Save Tab Session)
        assertThat(NameDialogController.problemWith(name))
                .isEqualTo(NameDialogController.NO_NAME_MESSAGE);
    }

    @Test
    void name() {
        assertThat(NameDialogController.problemWith("My session"))
                .isNull();
    }

    @Test
    void nameWithSpacesAround() {
        // Accepted as typed: a name with spaces around it is still a name
        assertThat(NameDialogController.problemWith(" My session "))
                .isNull();
    }
}
