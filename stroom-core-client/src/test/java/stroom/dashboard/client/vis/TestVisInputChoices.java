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

package stroom.dashboard.client.vis;

import stroom.query.api.Format;
import stroom.visualisation.shared.VisInput;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisInputChoices {

    private static final List<VisFieldOption> COLUMNS = List.of(
            new VisFieldOption("Count", Format.Type.NUMBER),
            new VisFieldOption("Host", Format.Type.TEXT),
            new VisFieldOption("Notes", null),
            new VisFieldOption("Time", Format.Type.DATE_TIME),
            new VisFieldOption("Total", Format.Type.GENERAL));

    private static final VisInput Y_AXIS = new VisInput("y", "Y axis", "The height of each bar",
            List.of("NUMBER"), true, false);
    private static final VisInput X_AXIS = new VisInput("x", "X axis", null,
            List.of("NUMBER", "DATE_TIME"), false, false);

    @Test
    void testChoicesAreTheColumnsTheInputTakes() {
        // Columns of no particular type may hold numbers, so they are offered too
        assertThat(VisInputChoices.choices(Y_AXIS, COLUMNS, null))
                .containsExactly("", "Count", "Notes", "Total");
        assertThat(VisInputChoices.choices(X_AXIS, COLUMNS, ""))
                .containsExactly("", "Count", "Notes", "Time", "Total");
    }

    @Test
    void testChoicesKeepTheChosenColumn() {
        // Host isn't one the input takes, but it was chosen, so opening the settings keeps it
        assertThat(VisInputChoices.choices(Y_AXIS, COLUMNS, "Host"))
                .containsExactly("", "Count", "Host", "Notes", "Total");
    }

    @Test
    void testChoicesWithoutInputAreAllColumns() {
        assertThat(VisInputChoices.choices(null, COLUMNS, null))
                .containsExactly("", "Count", "Host", "Notes", "Time", "Total");
    }

    @Test
    void testProblem() {
        assertThat(VisInputChoices.problem(Y_AXIS, COLUMNS, "Host"))
                .isEqualTo("Host is text, but Y axis takes numbers");
        assertThat(VisInputChoices.problem(X_AXIS, COLUMNS, "Host"))
                .isEqualTo("Host is text, but X axis takes numbers or dates");
        assertThat(VisInputChoices.problem(Y_AXIS, COLUMNS, "Count")).isNull();
        assertThat(VisInputChoices.problem(Y_AXIS, COLUMNS, "Notes")).isNull();
        assertThat(VisInputChoices.problem(Y_AXIS, COLUMNS, "")).isNull();
        assertThat(VisInputChoices.problem(Y_AXIS, COLUMNS, null)).isNull();
        // A column the table no longer has is left to the visualisation
        assertThat(VisInputChoices.problem(Y_AXIS, COLUMNS, "Gone")).isNull();
        assertThat(VisInputChoices.problem(null, COLUMNS, "Host")).isNull();
    }

    @Test
    void testHelpText() {
        assertThat(VisInputChoices.helpText(Y_AXIS)).isEqualTo("Required. The height of each bar. Takes numbers.");
        assertThat(VisInputChoices.helpText(X_AXIS)).isEqualTo("Takes numbers or dates.");
        assertThat(VisInputChoices.helpText(new VisInput("s", null, "Splits the bars.", null, null, null)))
                .isEqualTo("Splits the bars.");
    }
}
