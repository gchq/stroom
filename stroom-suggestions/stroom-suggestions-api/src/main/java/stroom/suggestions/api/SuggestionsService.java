/*
 * Copyright 2020 Crown Copyright
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

public interface SuggestionsService {

    Suggestions fetch(final FetchSuggestionsRequest request);

    /// Fetches the most common values of a field from the handler for the request's data source type.
    ///
    /// @param request The data source, field, filter and time range.
    /// @return The most common values, or no values if the data source type has no handler.
    FieldValues fetchFieldValues(final FetchFieldValuesRequest request);
}
