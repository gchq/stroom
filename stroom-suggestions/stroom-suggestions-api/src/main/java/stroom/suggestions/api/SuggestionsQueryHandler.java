/*
 * Copyright 2022 Crown Copyright
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

package stroom.suggestions.api;

import stroom.query.shared.FetchFieldValuesRequest;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.FieldValues;
import stroom.query.shared.Suggestions;

public interface SuggestionsQueryHandler {

    Suggestions getSuggestions(final FetchSuggestionsRequest request);

    /// Fetches the most common values of a field, for the value picker of an expression term.
    ///
    /// @param request The data source, field, filter and time range.
    /// @return The most common values, or no values if the data source doesn't provide them.
    default FieldValues getFieldValues(final FetchFieldValuesRequest request) {
        return FieldValues.EMPTY;
    }
}
