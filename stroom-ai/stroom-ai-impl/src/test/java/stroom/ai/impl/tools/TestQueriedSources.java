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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The join line the joins hint gives for two data sources a session queried apart: their names quoted as StroomQL
/// takes them, and a short alias made from the joined source's name.
class TestQueriedSources {

    @Test
    void theJoinedSourcesAliasIsMadeFromItsName() {
        assertThat(QueriedSources.joinLine("Staff activity", "HR"))
                .isEqualTo("from \"Staff activity\" join \"HR\" as hr on <key> = hr.<key>");
        // Several words: their initials.
        assertThat(QueriedSources.joinLine("HR", "Staff activity"))
                .isEqualTo("from \"HR\" join \"Staff activity\" as sa on <key> = sa.<key>");
        // One long word, cut; a name starting with a digit, or a keyword, made a safe identifier.
        assertThat(QueriedSources.joinLine("a", "Authentication")).endsWith("as authenti on <key> = authenti.<key>");
        assertThat(QueriedSources.joinLine("a", "1st-feed")).endsWith("as s1f on <key> = s1f.<key>");
        assertThat(QueriedSources.joinLine("a", "On")).endsWith("as on2 on <key> = on2.<key>");
        assertThat(QueriedSources.joinLine("a", "--")).endsWith("as j on <key> = j.<key>");
    }

    @Test
    void theHintsJoinLinesGiveBothOrdersAndWhatDecides() {
        assertThat(QueriedSources.joinLines("Staff activity", "HR"))
                .isEqualTo("from \"Staff activity\" join \"HR\" as hr on <key> = hr.<key>, or from \"HR\" join "
                           + "\"Staff activity\" as sa on <key> = sa.<key>. The joined source is read in full first, "
                           + "so join the smaller one.");
        // Only one name writable: no join lines at all.
        assertThat(QueriedSources.joinLines("Staff activity", "9889f8e1-72da-4b8c-addc-2368bfc625f1")).isNull();
    }

    @Test
    void noJoinLineIsGivenForAUuidOrANameStroomQlCantQuote() {
        // An index given by uuid (as esql_query often is) isn't a name to write in a query.
        assertThat(QueriedSources.joinLine("Staff activity", "9889f8e1-72da-4b8c-addc-2368bfc625f1")).isNull();
        assertThat(QueriedSources.joinLine("9889F8E1-72DA-4B8C-ADDC-2368BFC625F1", "HR")).isNull();
        assertThat(QueriedSources.joinLine("Say \"hi\"", "HR")).isNull();
    }
}
