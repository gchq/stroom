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

package stroom.query.client;

import stroom.query.api.datasource.QueryField;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTermValueSuggestions {

    @Test
    void aBooleanSuggestsWhatStartsWithWhatWasTyped() {
        assertThat(TermValueSuggestions.booleanSuggestions("")).containsExactly("True", "False");
        assertThat(TermValueSuggestions.booleanSuggestions(null)).containsExactly("True", "False");
        assertThat(TermValueSuggestions.booleanSuggestions(" T")).containsExactly("True");
        assertThat(TermValueSuggestions.booleanSuggestions("fal")).containsExactly("False");
        assertThat(TermValueSuggestions.booleanSuggestions("x")).isEmpty();
    }

    @Test
    void aBooleanFieldAnswersAtOnceWithoutAFetcher() {
        final TermValueSuggestions suggestions = new TermValueSuggestions();
        suggestions.setField(QueryField.createBoolean("Enabled"));
        final List<String> found = new ArrayList<>();

        suggestions.fetch("tr", found::addAll);

        assertThat(found).containsExactly("True");
    }
}
