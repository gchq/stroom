/*
 * Copyright 2017 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle.MultiWordSuggestion;
import com.google.gwt.user.client.ui.SuggestOracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// Suggests a term's values as they are typed: True and False for a boolean field, else what its
/// data source suggests for the field, fetched (by whatever [#setFetcher] is given, e.g. a term
/// editor's controller) once typing pauses, and kept if the source says it may be.
public class AsyncSuggestOracle extends SuggestOracle {

    private static final int DEBOUNCE_PERIOD_MILLIS = 400;

    private static final Map<FetchSuggestionsRequest, List<String>> CACHE = new HashMap<>();

    private BiConsumer<FetchSuggestionsRequest, Consumer<Suggestions>> fetcher;
    private DocRef dataSource;
    private QueryField field;
    private Timer requestTimer;

    /// @param fetcher Fetches a request's suggestions and gives them to the consumer, or null to
    ///                suggest nothing but a boolean's values.
    public void setFetcher(final BiConsumer<FetchSuggestionsRequest, Consumer<Suggestions>> fetcher) {
        this.fetcher = fetcher;
    }

    /// @param dataSource The data source whose values are suggested.
    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    /// @param field The field whose values are suggested.
    public void setField(final QueryField field) {
        this.field = field;
    }

    @Override
    public void requestDefaultSuggestions(final Request request, final Callback callback) {
        requestSuggestions(request, callback, 0);
    }

    @Override
    public void requestSuggestions(final Request request, final Callback callback) {
        requestSuggestions(request, callback, DEBOUNCE_PERIOD_MILLIS);
    }

    private void requestSuggestions(final Request request,
                                    final Callback callback,
                                    final int debouncePeriod) {
        if (field != null && FieldType.BOOLEAN.equals(field.getFldType())) {
            final String query = request.getQuery() != null ? request.getQuery().trim().toLowerCase() : "";
            final List<String> list = new ArrayList<>();
            if ("true".startsWith(query)) {
                list.add("True");
            }
            if ("false".startsWith(query)) {
                list.add("False");
            }
            returnSuggestions(request, callback, list);
            return;
        }

        if (fetcher != null && dataSource != null) {
            // Debounce requests so we don't spam the backend
            if (requestTimer != null) {
                requestTimer.cancel();
            }

            requestTimer = new Timer() {
                @Override
                public void run() {
                    final FetchSuggestionsRequest fetchSuggestionsRequest =
                            new FetchSuggestionsRequest(dataSource, field, request.getQuery());
                    final List<String> cachedSuggestions = CACHE.get(fetchSuggestionsRequest);
                    if (cachedSuggestions != null) {
                        returnSuggestions(request, callback, cachedSuggestions);

                    } else {
                        fetcher.accept(fetchSuggestionsRequest, result -> {
                            if (result.isCacheable()) {
                                CACHE.put(fetchSuggestionsRequest, result.getList());
                            }

                            returnSuggestions(request, callback, result.getList());
                        });
                    }
                }
            };

            requestTimer.schedule(debouncePeriod);
        }
    }

    private void returnSuggestions(final Request request,
                                   final Callback callback,
                                   final List<String> list) {
        final List<Suggestion> suggestions = new ArrayList<>();
        for (final String string : list) {
            suggestions.add(new MultiWordSuggestion(string, string));
        }
        callback.onSuggestionsReady(request, new Response(suggestions));
    }
}
