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

package stroom.ai.impl.tools.query;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.query.api.Result;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// What a search's response says went wrong, as Stroom's query service reports it: errors and warnings in its
/// `errorMessages` (and its results'), each with a severity. Its `SearchResponseMapper` leaves the deprecated `errors`
/// list null, so a tool reading only that took a failed query for one that ran with no rows. The deprecated lists are
/// still read, as errors, for any response that fills them.
public final class SearchProblems {

    private SearchProblems() {
        // Static only.
    }

    /// @param response A search's response.
    /// @return Its errors, each once in the order given: messages of severity error or worse, and the deprecated
    /// errors lists; empty when the search hasn't failed.
    public static List<String> errors(final DashboardSearchResponse response) {
        final Set<String> errors = new LinkedHashSet<>(NullSafe.list(response.getErrors()));
        errors.addAll(messages(response, true));
        for (final Result result : NullSafe.list(response.getResults())) {
            errors.addAll(NullSafe.list(result.getErrors()));
        }
        return new ArrayList<>(errors);
    }

    /// @param response A search's response.
    /// @return Its warnings, each once in the order given: messages less severe than an error (e.g. a join's warning
    /// that one row matched very many), which don't stop the results being used.
    public static List<String> warnings(final DashboardSearchResponse response) {
        return new ArrayList<>(new LinkedHashSet<>(messages(response, false)));
    }

    // The messages of the response and its results that are errors (or not).
    private static List<String> messages(final DashboardSearchResponse response, final boolean errors) {
        final List<ErrorMessage> all = new ArrayList<>(NullSafe.list(response.getErrorMessages()));
        for (final Result result : NullSafe.list(response.getResults())) {
            all.addAll(NullSafe.list(result.getErrorMessages()));
        }
        return all.stream()
                .filter(message -> NullSafe.isNonBlankString(message.getMessage()))
                .filter(message -> message.getSeverity().greaterThanOrEqual(Severity.ERROR) == errors)
                .map(ErrorMessage::getMessage)
                .toList();
    }
}
