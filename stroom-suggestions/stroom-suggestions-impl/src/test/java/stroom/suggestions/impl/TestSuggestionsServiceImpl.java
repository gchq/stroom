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

package stroom.suggestions.impl;

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.FetchFieldValuesRequest;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.FieldValueCount;
import stroom.query.shared.FieldValues;
import stroom.query.shared.Suggestions;
import stroom.security.mock.MockSecurityContext;
import stroom.suggestions.api.SuggestionsQueryHandler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSuggestionsServiceImpl {

    private static final FieldValues VALUES = new FieldValues(List.of(new FieldValueCount("alice", 3L)), 4L, true);
    private static final Suggestions SUGGESTIONS = new Suggestions(List.of("alice"));

    // Lists field values but doesn't suggest values as the user types
    private static final SuggestionsQueryHandler VALUES_HANDLER = new SuggestionsQueryHandler() {
        @Override
        public Suggestions getSuggestions(final FetchSuggestionsRequest request) {
            return Suggestions.EMPTY;
        }

        @Override
        public FieldValues getFieldValues(final FetchFieldValuesRequest request) {
            return VALUES;
        }
    };
    // Suggests values as the user types but doesn't list field values
    private static final SuggestionsQueryHandler SUGGESTIONS_HANDLER = new SuggestionsQueryHandler() {
        @Override
        public Suggestions getSuggestions(final FetchSuggestionsRequest request) {
            return SUGGESTIONS;
        }
    };

    private final SuggestionsServiceImpl service = new SuggestionsServiceImpl(
            Map.of("Values", () -> VALUES_HANDLER, "Suggestions", () -> SUGGESTIONS_HANDLER),
            new MockSecurityContext());

    @Test
    void testFetchFieldValues() {
        assertThat(service.fetchFieldValues(valuesRequest("Values")))
                .isEqualTo(VALUES);
    }

    @Test
    void testFetchFieldValues_handlerWithoutValues() {
        assertThat(service.fetchFieldValues(valuesRequest("Suggestions")))
                .isEqualTo(FieldValues.EMPTY);
    }

    @Test
    void testFetchFieldValues_unknownType() {
        assertThat(service.fetchFieldValues(valuesRequest("Unknown")))
                .isEqualTo(FieldValues.EMPTY);
    }

    @Test
    void testFetch() {
        assertThat(service.fetch(suggestionsRequest("Suggestions")))
                .isEqualTo(SUGGESTIONS);
        // A handler that only lists field values suggests nothing as the user types
        assertThat(service.fetch(suggestionsRequest("Values")))
                .isEqualTo(Suggestions.EMPTY);
        assertThat(service.fetch(suggestionsRequest("Unknown")))
                .isEqualTo(Suggestions.EMPTY);
    }

    private static FetchFieldValuesRequest valuesRequest(final String type) {
        return new FetchFieldValuesRequest(
                new DocRef(type, "uuid"),
                QueryField.createKeyword("user"),
                null,
                null,
                null,
                null);
    }

    private static FetchSuggestionsRequest suggestionsRequest(final String type) {
        return new FetchSuggestionsRequest(new DocRef(type, "uuid"), QueryField.createKeyword("user"), "al");
    }
}
