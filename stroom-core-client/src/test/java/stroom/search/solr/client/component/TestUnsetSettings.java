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

package stroom.search.solr.client.component;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestUnsetSettings {

    /// However no lines were stored, showing none keeps them as they were.
    @Test
    void unsetLinesAreAsRead() {
        for (final List<String> read : Arrays.asList(null, new ArrayList<String>(), List.of(""), List.of(" ", ""))) {
            assertThat(UnsetSettings.linesAsRead(List.of(), read)).isSameAs(read);
        }
    }

    /// Lines set or cleared are as shown.
    @Test
    void changedLinesAreAsShown() {
        assertThat(UnsetSettings.linesAsRead(List.of("a"), null)).containsExactly("a");
        assertThat(UnsetSettings.linesAsRead(List.of("a"), List.of(""))).containsExactly("a");
        assertThat(UnsetSettings.linesAsRead(List.of(), List.of("a"))).isEmpty();
    }

    /// Blank text is as read, and text set or cleared as shown.
    @Test
    void text() {
        assertThat(UnsetSettings.textAsRead("", null)).isNull();
        assertThat(UnsetSettings.textAsRead("", " ")).isEqualTo(" ");
        assertThat(UnsetSettings.textAsRead("a", null)).isEqualTo("a");
        assertThat(UnsetSettings.textAsRead("", "a")).isEmpty();
    }

    /// An expression with no terms is none if there was none, and otherwise is as shown.
    @Test
    void expression() {
        final ExpressionOperator empty = ExpressionOperator.builder().build();
        final ExpressionOperator term = ExpressionOperator.builder()
                .addTerm("f", Condition.EQUALS, "v")
                .build();
        assertThat(UnsetSettings.expressionAsRead(empty, null)).isNull();
        assertThat(UnsetSettings.expressionAsRead(null, null)).isNull();
        assertThat(UnsetSettings.expressionAsRead(term, null)).isSameAs(term);
        assertThat(UnsetSettings.expressionAsRead(empty, term)).isSameAs(empty);
    }
}
